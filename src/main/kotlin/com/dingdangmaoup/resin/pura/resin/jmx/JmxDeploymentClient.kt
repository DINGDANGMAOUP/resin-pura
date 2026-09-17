package com.dingdangmaoup.resin.pura.resin.jmx

import com.dingdangmaoup.resin.pura.ResinModelBase
import com.dingdangmaoup.resin.pura.ResinModel
import com.dingdangmaoup.resin.pura.resin.LocalArtifactTransport
import com.dingdangmaoup.resin.pura.resin.ResinInstallation
import com.dingdangmaoup.resin.pura.resin.WebApp
import com.dingdangmaoup.resin.pura.resin.DeploymentTarget
import com.dingdangmaoup.resin.pura.resin.DeploymentObservation
import com.dingdangmaoup.resin.pura.resin.common.MBeanUtil
import com.dingdangmaoup.resin.pura.resin.configuration.JmxConfigurationStrategy
import com.intellij.javaee.appServers.deployment.DeploymentStatus
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import java.io.File
import java.io.IOException
import javax.management.InstanceNotFoundException
import javax.management.JMException
import javax.management.MBeanServerConnection
import javax.management.ObjectName

/** JMX commands and archive deployment lifecycle, independent of XML editing. */
open class JmxDeploymentClient(private val myResinInstallation: ResinInstallation) : JmxConfigurationStrategy {
    override fun observeConfiguration(
        resinModel: ResinModelBase<*>, webApp: WebApp, removing: Boolean,
    ): DeploymentObservation {
        val command = object : ConnectorCommandBase<WebAppStateResult>(resinModel) {
            override fun doExecute(connection: MBeanServerConnection): WebAppStateResult =
                readWebAppState(connection, createApplicationObjectName(webApp.target()))
        }
        if (!command.safeExecute()) return DeploymentObservation(DeploymentStatus.UNKNOWN, false)
        return observeState(command.getResult(), removing)
    }

    override fun deployWithJmx(resinModel: ResinModelBase<*>, webApp: WebApp): Boolean {
        val location = webApp.getLocation() ?: return false
        val webAppFile = File(FileUtil.toSystemDependentName(location))
        if (archiveTargetError(webApp, getArchiveKey(webAppFile)) != null) return false
        if (!resinModel.validateTransferSource(webAppFile)) return false
        if (!webAppFile.exists()) {
            LOG.error("Can't find web app")
            return false
        }

        if (getDeployStateWithJmx(resinModel, webApp, Ref.create(false)) != DeploymentStatus.UNKNOWN &&
            !executeUndeployCommand(resinModel, webAppFile)
        ) {
            return false
        }

        if (!cleanUpWebApp(resinModel, webAppFile)) {
            return false
        }
        if (!resinModel.transferFile(webAppFile)) {
            return false
        }
        val deployCommand = DeployCommand(resinModel, "start", getArchiveKey(webAppFile))
        if (!deployCommand.safeExecute() || deployCommand.getResult() != true) {
            return false
        }
        return true
    }

    override fun getDeployStateWithJmx(
        resinModel: ResinModelBase<*>,
        webApp: WebApp,
        isFinal: Ref<Boolean>,
    ): DeploymentStatus {
        val observation = observeArchive(resinModel, webApp, false)
        isFinal.set(observation.terminal)
        return observation.status
    }

    override fun observeArchive(resinModel: ResinModelBase<*>, webApp: WebApp, removing: Boolean): DeploymentObservation {
        val location = webApp.getLocation() ?: return DeploymentObservation(DeploymentStatus.FAILED, true)
        val command = GetStateCommand(resinModel, File(FileUtil.toSystemDependentName(location)))
        return observeState(if (command.safeExecute()) command.getResult() else null, removing)
    }

    override fun undeployWithJmx(resinModel: ResinModelBase<*>, webApp: WebApp): Boolean {
        val location = webApp.getLocation() ?: return false
        val webAppFile = File(FileUtil.toSystemDependentName(location))
        if (archiveTargetError(webApp, getArchiveKey(webAppFile)) != null) return false
        if (!resinModel.validateTransferSource(webAppFile)) return false
        if (!executeUndeployCommand(resinModel, webAppFile)) {
            return false
        }
        if (!cleanUpWebApp(resinModel, webAppFile)) {
            return false
        }

        val getStateCommand = GetStateCommand(resinModel, webAppFile)
        if (!getStateCommand.safeExecute()) {
            return false
        }
        return getStateCommand.getResult() == WebAppStateResult.Missing
    }

    internal open fun executeUndeployCommand(resinModel: ResinModelBase<*>, webAppFile: File): Boolean {
        // ArchiveDeployMXBean#undeploy(String) is shared by Resin 3 and 4. Calling
        // WebApp#destroy directly is not available on Resin 3 and reports false positives
        // when AbstractConnectorCommand converts a JMX exception into a null result.
        val command = DeployCommand(resinModel, "undeploy", getArchiveKey(webAppFile))
        return command.safeExecute() && command.getResult() == true
    }

    internal open fun cleanUpWebApp(resinModel: ResinModelBase<*>, webAppFile: File): Boolean {
        if (!webAppFile.isDirectory && webAppFile.name.endsWith(".war", ignoreCase = true) &&
            !resinModel.deleteFile(File(webAppFile.parent, FileUtilRt.getNameWithoutExtension(webAppFile.name)))
        ) {
            return false
        }
        return resinModel.deleteFile(webAppFile)
    }

    internal fun getArchiveKey(webAppFile: File): String {
        val fileName = webAppFile.name
        val exploded = webAppFile.isDirectory || (!webAppFile.exists() && !fileName.endsWith(".war", ignoreCase = true))
        val trimExtension = !(exploded && myResinInstallation.getVersion().getParsed().compare(4, 0, 10) > 0)
        return if (trimExtension) FileUtilRt.getNameWithoutExtension(fileName) else fileName
    }

    private class DeployCommand(
        resinModel: ResinModelBase<*>,
        private val myCommand: String,
        private val myArg: String,
    ) : ConnectorCommandBase<Boolean>(resinModel) {
        @Throws(JMException::class, IOException::class)
        override fun doExecute(connection: MBeanServerConnection): Boolean {
            return invokeArchiveCommand(connection, myCommand, myArg)
        }
    }

    private abstract inner class WebAppCommandBase<T : Any>(resinModel: ResinModelBase<*>, webAppFile: File) :
        ConnectorCommandBase<T>(resinModel) {
        private val myObjectName: ObjectName = createWebAppObjectName(getArchiveKey(webAppFile))

        @Throws(JMException::class, IOException::class)
        override fun doExecute(connection: MBeanServerConnection): T? = doExecute(connection, myObjectName)

        @Throws(JMException::class, IOException::class)
        protected abstract fun doExecute(connection: MBeanServerConnection, objectName: ObjectName): T?
    }

    private inner class GetStateCommand(resinModel: ResinModelBase<*>, webAppFile: File) :
        WebAppCommandBase<WebAppStateResult>(resinModel, webAppFile) {
        @Throws(JMException::class, IOException::class)
        override fun doExecute(connection: MBeanServerConnection, objectName: ObjectName): WebAppStateResult {
            return readWebAppState(connection, objectName)
        }
    }

    internal sealed interface WebAppStateResult {
        data class Found(val state: String) : WebAppStateResult

        data object Missing : WebAppStateResult
    }

    companion object {
        private val LOG = Logger.getInstance(JmxDeploymentClient::class.java)

        internal fun archiveTargetError(webApp: WebApp, archiveKey: String): String? {
            if (webApp.getHost().isNotEmpty() && webApp.getHost() != "default") return "deployment.jmx.default.host"
            val expectedContext = if (archiveKey.equals("ROOT", true)) "/" else "/$archiveKey"
            if (!webApp.usesDefaultContext() && webApp.getContextPath() != expectedContext) return "deployment.jmx.archive.context"
            return null
        }

        internal fun localTransport(model: ResinModel): LocalArtifactTransport? {
            val command = object : ConnectorCommandBase<LocalArtifactTransport>(model) {
                override fun doExecute(connection: MBeanServerConnection): LocalArtifactTransport {
                    return readLocalTransport(connection)
                }
            }
            return if (command.safeExecute()) command.getResult() else null
        }
        internal fun readLocalTransport(connection: MBeanServerConnection): LocalArtifactTransport {
            fun attribute(name: String) = connection.getAttribute(MBEAN_WEB_APP_DEPLOY, name) as? String
            return LocalArtifactTransport(
                File(requireNotNull(attribute("ArchiveDirectory"))), File(requireNotNull(attribute("ExpandDirectory"))),
                attribute("ExpandPrefix").orEmpty(), attribute("ExpandSuffix").orEmpty(),
            )
        }

        @JvmField
        val MBEAN_WEB_APP_DEPLOY: ObjectName = MBeanUtil.newObjectName("resin:type=WebAppDeploy,Host=default,name=webapps")

        private const val ROOT_ARCHIVE_KEY = "ROOT"
        private val OBJECT_NAME_QUOTE_CHARS = setOf(',', '=', ':', '"', '*', '?')

        internal fun createWebAppObjectName(archiveKey: String): ObjectName {
            require('\n' !in archiveKey && '\r' !in archiveKey) { "WebApp archive names must not contain line breaks" }
            val webAppName = if (archiveKey.equals(ROOT_ARCHIVE_KEY, ignoreCase = true)) "/" else "/$archiveKey"
            return createApplicationObjectName(DeploymentTarget("", webAppName))
        }

        internal fun createApplicationObjectName(target: DeploymentTarget): ObjectName {
            fun quote(value: String): String {
                require('\n' !in value && '\r' !in value) { "Deployment names must not contain line breaks" }
                return if (value.any(OBJECT_NAME_QUOTE_CHARS::contains)) ObjectName.quote(value) else value
            }
            return MBeanUtil.newObjectName("resin:type=WebApp,Host=${quote(target.jmxHost)},name=${quote(target.contextPath)}").also { objectName ->
                check(!objectName.isPattern) { "WebApp ObjectName must be exact" }
            }
        }

        internal fun observeState(state: WebAppStateResult?, removing: Boolean): DeploymentObservation = when (state) {
            WebAppStateResult.Missing -> DeploymentObservation(
                if (removing) DeploymentStatus.NOT_DEPLOYED else DeploymentStatus.UNKNOWN, removing,
            )
            is WebAppStateResult.Found -> when {
                removing -> DeploymentObservation(DeploymentStatus.UNKNOWN, false)
                state.state.equals(STATE_JMX_ATTRIBUTE_ACTIVE, true) -> DeploymentObservation(DeploymentStatus.DEPLOYED, true)
                state.state.equals(STATE_JMX_ATTRIBUTE_ERROR, true) || state.state.equals(STATE_JMX_ATTRIBUTE_FAILED, true) ->
                    DeploymentObservation(DeploymentStatus.FAILED, true)
                else -> DeploymentObservation(DeploymentStatus.UNKNOWN, false)
            }
            null -> DeploymentObservation(DeploymentStatus.UNKNOWN, false)
        }

        @Throws(JMException::class, IOException::class)
        internal fun readWebAppState(
            connection: MBeanServerConnection,
            objectName: ObjectName,
        ): WebAppStateResult {
            return try {
                WebAppStateResult.Found(connection.getAttribute(objectName, STATE_JMX_ATTRIBUTE) as String)
            } catch (_: InstanceNotFoundException) {
                WebAppStateResult.Missing
            }
        }

        @Throws(JMException::class, IOException::class)
        internal fun refreshArchiveIndex(connection: MBeanServerConnection, archiveKey: String): Boolean {
            connection.invoke(MBEAN_WEB_APP_DEPLOY, UPDATE_OPERATION, emptyArray(), emptyArray())
            val names = connection.getAttribute(MBEAN_WEB_APP_DEPLOY, NAMES_JMX_ATTRIBUTE)
            return when (names) {
                is Array<*> -> names.any { archiveNameMatches(it, archiveKey) }
                is Collection<*> -> names.any { archiveNameMatches(it, archiveKey) }
                else -> false
            }
        }

        private fun archiveNameMatches(name: Any?, archiveKey: String): Boolean {
            return if (archiveKey.equals(ROOT_ARCHIVE_KEY, ignoreCase = true)) {
                name is String && name.equals(ROOT_ARCHIVE_KEY, ignoreCase = true)
            } else {
                name == archiveKey
            }
        }

        @Throws(JMException::class, IOException::class)
        internal fun invokeArchiveCommand(
            connection: MBeanServerConnection,
            command: String,
            archiveKey: String,
        ): Boolean {
            if (!refreshArchiveIndex(connection, archiveKey)) {
                return command == UNDEPLOY_OPERATION
            }
            connection.invoke(
                MBEAN_WEB_APP_DEPLOY,
                command,
                arrayOf(archiveKey),
                arrayOf(String::class.java.name),
            )
            return true
        }

        @JvmField
        val STATE_JMX_ATTRIBUTE: String = "State"

        private const val NAMES_JMX_ATTRIBUTE = "Names"
        private const val UPDATE_OPERATION = "update"
        private const val UNDEPLOY_OPERATION = "undeploy"

        @JvmField
        val STATE_JMX_ATTRIBUTE_ACTIVE: String = "active"

        @JvmField
        val STATE_JMX_ATTRIBUTE_ERROR: String = "error"

        @JvmField
        val STATE_JMX_ATTRIBUTE_FAILED: String = "failed"

    }
}
