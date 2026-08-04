package com.dingdangmaoup.resin.pura.resin.configuration

import com.dingdangmaoup.resin.pura.ResinModel
import com.dingdangmaoup.resin.pura.ResinModelBase
import com.dingdangmaoup.resin.pura.ResinModelDataBase
import com.dingdangmaoup.resin.pura.resin.ResinInstallation
import com.dingdangmaoup.resin.pura.resin.WebApp
import com.intellij.javaee.appServers.deployment.DeploymentModel
import com.intellij.javaee.appServers.deployment.DeploymentSource
import com.intellij.javaee.appServers.deployment.DeploymentStatus
import com.intellij.javaee.appServers.run.configuration.CommonModel
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.util.Pair as IdeaPair
import com.intellij.openapi.util.Ref
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.lang.reflect.Proxy
import javax.management.InstanceNotFoundException
import javax.management.MBeanServerConnection
import javax.management.ObjectName

class Resin3XConfigurationStrategyTest {
    @Test
    fun `deploy reports failure when remote cleanup fails`() {
        withFixture { strategy, resinModel, webApp ->
            assertFalse(strategy.deployWithJmx(resinModel, webApp))
            assertEquals(1, strategy.cleanupAttempts)
            assertEquals(0, strategy.undeployAttempts)
        }
    }

    @Test
    fun `undeploy reports failure when remote cleanup fails`() {
        withFixture { strategy, resinModel, webApp ->
            assertFalse(strategy.undeployWithJmx(resinModel, webApp))
            assertEquals(1, strategy.cleanupAttempts)
            assertEquals(1, strategy.undeployAttempts)
        }
    }

    @Test
    fun `web app object names remain exact for special archive names`() {
        val archiveKeys = listOf(
            "plain",
            "hello world",
            "a'b",
            "a,b",
            "a=b",
            "a:b",
            "a\"b",
            "a*b",
            "a?b",
            "a\\b",
        )

        for (archiveKey in archiveKeys) {
            val objectName = Resin3XConfigurationStrategy.createWebAppObjectName(archiveKey)
            val property = objectName.getKeyProperty("name")
            val decodedProperty = if (property.startsWith('"')) ObjectName.unquote(property) else property

            assertFalse("$archiveKey must not create a pattern", objectName.isPattern)
            assertEquals("/$archiveKey", decodedProperty)
            if (archiveKey.none { it in setOf(',', '=', ':', '"', '*', '?') }) {
                assertEquals("/$archiveKey", property)
            }
        }
    }

    @Test
    fun `root archive maps to the root web app mbean`() {
        val objectName = Resin3XConfigurationStrategy.createWebAppObjectName("ROOT")

        assertFalse(objectName.isPattern)
        assertEquals("/", objectName.getKeyProperty("name"))
        assertEquals("/", Resin3XConfigurationStrategy.createWebAppObjectName("root").getKeyProperty("name"))
        assertEquals("/", Resin3XConfigurationStrategy.createWebAppObjectName("Root").getKeyProperty("name"))
    }

    @Test
    fun `line breaks are rejected because Resin cannot register a matching object name`() {
        assertThrows(IllegalArgumentException::class.java) {
            Resin3XConfigurationStrategy.createWebAppObjectName("bad\nname")
        }
        assertThrows(IllegalArgumentException::class.java) {
            Resin3XConfigurationStrategy.createWebAppObjectName("bad\rname")
        }
    }

    @Test
    fun `archive start refreshes the exact deploy mbean before invoking the typed command`() {
        val (connection, calls) = recordingConnection { call ->
            when (call) {
                is MBeanCall.GetAttribute -> arrayOf("sample", "ROOT")
                is MBeanCall.Invoke -> null
            }
        }

        assertTrue(Resin3XConfigurationStrategy.invokeArchiveCommand(connection, "start", "sample"))
        assertEquals(
            listOf(
                MBeanCall.Invoke(
                    Resin3XConfigurationStrategy.MBEAN_WEB_APP_DEPLOY,
                    "update",
                    emptyList(),
                    emptyList(),
                ),
                MBeanCall.GetAttribute(Resin3XConfigurationStrategy.MBEAN_WEB_APP_DEPLOY, "Names"),
                MBeanCall.Invoke(
                    Resin3XConfigurationStrategy.MBEAN_WEB_APP_DEPLOY,
                    "start",
                    listOf("sample"),
                    listOf(String::class.java.name),
                ),
            ),
            calls,
        )
    }

    @Test
    fun `missing archives reject start but make undeploy idempotent without invoking either command`() {
        val (startConnection, startCalls) = recordingConnection { call ->
            when (call) {
                is MBeanCall.GetAttribute -> emptyList<String>()
                is MBeanCall.Invoke -> null
            }
        }
        val (undeployConnection, undeployCalls) = recordingConnection { call ->
            when (call) {
                is MBeanCall.GetAttribute -> emptyArray<String>()
                is MBeanCall.Invoke -> null
            }
        }

        assertFalse(Resin3XConfigurationStrategy.invokeArchiveCommand(startConnection, "start", "missing"))
        assertTrue(Resin3XConfigurationStrategy.invokeArchiveCommand(undeployConnection, "undeploy", "missing"))

        val expectedLookup = listOf(
            MBeanCall.Invoke(
                Resin3XConfigurationStrategy.MBEAN_WEB_APP_DEPLOY,
                "update",
                emptyList(),
                emptyList(),
            ),
            MBeanCall.GetAttribute(Resin3XConfigurationStrategy.MBEAN_WEB_APP_DEPLOY, "Names"),
        )
        assertEquals(expectedLookup, startCalls)
        assertEquals(expectedLookup, undeployCalls)
    }

    @Test
    fun `archive lookup transport failure is not mistaken for a missing archive`() {
        val failure = IOException("connection lost")
        val (connection, calls) = recordingConnection { call ->
            when (call) {
                is MBeanCall.GetAttribute -> throw failure
                is MBeanCall.Invoke -> null
            }
        }

        val thrown = assertThrows(IOException::class.java) {
            Resin3XConfigurationStrategy.invokeArchiveCommand(connection, "undeploy", "sample")
        }

        assertSame(failure, thrown)
        assertEquals(
            listOf(
                MBeanCall.Invoke(
                    Resin3XConfigurationStrategy.MBEAN_WEB_APP_DEPLOY,
                    "update",
                    emptyList(),
                    emptyList(),
                ),
                MBeanCall.GetAttribute(Resin3XConfigurationStrategy.MBEAN_WEB_APP_DEPLOY, "Names"),
            ),
            calls,
        )
    }

    @Test
    fun `archive command transport failure propagates after the successful refresh`() {
        val failure = IOException("connection lost during start")
        val (connection, calls) = recordingConnection { call ->
            when (call) {
                is MBeanCall.GetAttribute -> listOf("sample")
                is MBeanCall.Invoke -> {
                    if (call.operation == "start") throw failure
                    null
                }
            }
        }

        val thrown = assertThrows(IOException::class.java) {
            Resin3XConfigurationStrategy.invokeArchiveCommand(connection, "start", "sample")
        }

        assertSame(failure, thrown)
        assertEquals(
            listOf(
                MBeanCall.Invoke(
                    Resin3XConfigurationStrategy.MBEAN_WEB_APP_DEPLOY,
                    "update",
                    emptyList(),
                    emptyList(),
                ),
                MBeanCall.GetAttribute(Resin3XConfigurationStrategy.MBEAN_WEB_APP_DEPLOY, "Names"),
                MBeanCall.Invoke(
                    Resin3XConfigurationStrategy.MBEAN_WEB_APP_DEPLOY,
                    "start",
                    listOf("sample"),
                    listOf(String::class.java.name),
                ),
            ),
            calls,
        )
    }

    @Test
    fun `root archive matching is case insensitive for collection based name results`() {
        val (connection, calls) = recordingConnection { call ->
            when (call) {
                is MBeanCall.GetAttribute -> listOf("ROOT")
                is MBeanCall.Invoke -> null
            }
        }

        assertTrue(Resin3XConfigurationStrategy.invokeArchiveCommand(connection, "start", "root"))
        assertEquals(
            MBeanCall.Invoke(
                Resin3XConfigurationStrategy.MBEAN_WEB_APP_DEPLOY,
                "start",
                listOf("root"),
                listOf(String::class.java.name),
            ),
            calls.last(),
        )
    }

    @Test
    fun `state lookup distinguishes a missing web app from a transport failure`() {
        val objectName = Resin3XConfigurationStrategy.createWebAppObjectName("sample")
        val (foundConnection, foundCalls) = recordingConnection { call ->
            when (call) {
                is MBeanCall.GetAttribute -> "active"
                is MBeanCall.Invoke -> throw AssertionError("State lookup must not invoke an operation")
            }
        }
        val (missingConnection, missingCalls) = recordingConnection { call ->
            when (call) {
                is MBeanCall.GetAttribute -> throw InstanceNotFoundException(call.objectName.toString())
                is MBeanCall.Invoke -> throw AssertionError("State lookup must not invoke an operation")
            }
        }
        val transportFailure = IOException("connection lost")
        val (failedConnection, failedCalls) = recordingConnection { call ->
            when (call) {
                is MBeanCall.GetAttribute -> throw transportFailure
                is MBeanCall.Invoke -> throw AssertionError("State lookup must not invoke an operation")
            }
        }

        assertEquals(
            Resin3XConfigurationStrategy.WebAppStateResult.Found("active"),
            Resin3XConfigurationStrategy.readWebAppState(foundConnection, objectName),
        )
        assertSame(
            Resin3XConfigurationStrategy.WebAppStateResult.Missing,
            Resin3XConfigurationStrategy.readWebAppState(missingConnection, objectName),
        )
        val thrown = assertThrows(IOException::class.java) {
            Resin3XConfigurationStrategy.readWebAppState(failedConnection, objectName)
        }
        assertSame(transportFailure, thrown)

        val expectedCall = listOf(MBeanCall.GetAttribute(objectName, Resin3XConfigurationStrategy.STATE_JMX_ATTRIBUTE))
        assertEquals(expectedCall, foundCalls)
        assertEquals(expectedCall, missingCalls)
        assertEquals(expectedCall, failedCalls)
    }

    @Test
    fun `deploy stops after a failed file transfer and never reaches a jmx start`() {
        val webAppFile = Files.createTempFile("resin-webapp", ".war").toFile()
        try {
            withInstallation { installation ->
                val events = mutableListOf<String>()
                val strategy = TransferFailingStrategy(installation, events)
                val resinModel = TransferFailingModel(events)
                val webApp = WebApp(false, "/test", "default", webAppFile.absolutePath, null)

                assertFalse(strategy.deployWithJmx(resinModel, webApp))
                assertEquals(listOf("state", "cleanup", "transfer"), events)
            }
        } finally {
            webAppFile.delete()
        }
    }

    @Test
    fun `archive key removes only the final war extension`() {
        withStrategy { strategy ->
            assertEquals("sample.application", strategy.getArchiveKey(File("sample.application.war")))
            assertEquals("ROOT", strategy.getArchiveKey(File("ROOT.war")))
            assertTrue(Resin3XConfigurationStrategy.createWebAppObjectName("sample.application").isPropertyValuePattern.not())
        }
    }

    private fun withFixture(test: (CleanupFailingStrategy, ResinModel, WebApp) -> Unit) {
        val resinHome = Files.createTempDirectory("resin-home").toFile()
        val webAppFile = Files.createTempFile("resin-webapp", ".war").toFile()
        try {
            File(resinHome, "bin").mkdir()
            File(resinHome, "lib").mkdir()
            val installation = ResinInstallation.create(resinHome.absolutePath)
            val strategy = CleanupFailingStrategy(installation)
            val webApp = WebApp(false, "/test", "default", webAppFile.absolutePath, null)

            test(strategy, ResinModel(), webApp)
        } finally {
            webAppFile.delete()
            resinHome.deleteRecursively()
        }
    }

    private fun withStrategy(test: (Resin3XConfigurationStrategy) -> Unit) {
        withInstallation { installation ->
            test(Resin3XConfigurationStrategy(installation))
        }
    }

    private fun withInstallation(test: (ResinInstallation) -> Unit) {
        val resinHome = Files.createTempDirectory("resin-home").toFile()
        try {
            File(resinHome, "bin").mkdir()
            File(resinHome, "lib").mkdir()
            test(ResinInstallation.create(resinHome.absolutePath))
        } finally {
            resinHome.deleteRecursively()
        }
    }

    private class CleanupFailingStrategy(installation: ResinInstallation) :
        Resin3XConfigurationStrategy(installation) {
        var cleanupAttempts = 0
            private set
        var undeployAttempts = 0
            private set

        override fun getDeployStateWithJmx(
            resinModel: ResinModelBase<*>,
            webApp: WebApp,
            isFinal: Ref<Boolean>,
        ): DeploymentStatus = DeploymentStatus.UNKNOWN

        override fun executeUndeployCommand(resinModel: ResinModelBase<*>, webAppFile: File): Boolean {
            undeployAttempts++
            return true
        }

        override fun cleanUpWebApp(resinModel: ResinModelBase<*>, webAppFile: File): Boolean {
            cleanupAttempts++
            return false
        }
    }

    private class TransferFailingStrategy(
        installation: ResinInstallation,
        private val events: MutableList<String>,
    ) : Resin3XConfigurationStrategy(installation) {
        override fun getDeployStateWithJmx(
            resinModel: ResinModelBase<*>,
            webApp: WebApp,
            isFinal: Ref<Boolean>,
        ): DeploymentStatus {
            events += "state"
            return DeploymentStatus.UNKNOWN
        }

        override fun cleanUpWebApp(resinModel: ResinModelBase<*>, webAppFile: File): Boolean {
            events += "cleanup"
            return true
        }
    }

    private class TransferFailingModel(private val events: MutableList<String>) :
        ResinModelBase<ResinModelDataBase>() {
        override fun createResinModelData(): ResinModelDataBase = ResinModelDataBase()

        override fun transferFile(webAppFile: File): Boolean {
            events += "transfer"
            return false
        }

        override fun deleteFile(webAppFile: File): Boolean =
            throw AssertionError("Cleanup is controlled by the strategy fixture")

        override fun createAdditionalDeploymentSettingsEditor(
            commonModel: CommonModel,
            source: DeploymentSource,
        ): SettingsEditor<DeploymentModel>? = null

        override fun getEditor(): SettingsEditor<CommonModel> =
            throw AssertionError("Editor is not needed by this fixture")

        override fun getAddressesToCheck(): List<IdeaPair<String, Int>> = emptyList()
    }

    private sealed interface MBeanCall {
        data class Invoke(
            val objectName: ObjectName,
            val operation: String,
            val parameters: List<Any?>,
            val signature: List<String>,
        ) : MBeanCall

        data class GetAttribute(val objectName: ObjectName, val attribute: String) : MBeanCall
    }

    private fun recordingConnection(
        response: (MBeanCall) -> Any?,
    ): Pair<MBeanServerConnection, MutableList<MBeanCall>> {
        val calls = mutableListOf<MBeanCall>()
        val connection = Proxy.newProxyInstance(
            javaClass.classLoader,
            arrayOf(MBeanServerConnection::class.java),
        ) { _, method, arguments ->
            val args = requireNotNull(arguments)
            val call = when (method.name) {
                "invoke" -> MBeanCall.Invoke(
                    args[0] as ObjectName,
                    args[1] as String,
                    (args[2] as Array<*>).toList(),
                    (args[3] as Array<*>).map { it as String },
                )

                "getAttribute" -> MBeanCall.GetAttribute(args[0] as ObjectName, args[1] as String)
                else -> throw AssertionError("Unexpected MBean call: ${method.name}")
            }
            calls += call
            response(call)
        } as MBeanServerConnection
        return connection to calls
    }
}
