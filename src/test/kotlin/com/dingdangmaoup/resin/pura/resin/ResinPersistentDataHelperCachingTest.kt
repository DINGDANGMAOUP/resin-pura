package com.dingdangmaoup.resin.pura.resin

import com.dingdangmaoup.resin.pura.ResinModelBase
import com.dingdangmaoup.resin.pura.ResinModelDataBase
import com.dingdangmaoup.resin.pura.ResinPersistentData
import com.intellij.execution.ExecutionException
import com.intellij.javaee.appServers.appServerIntegrations.ApplicationServer
import com.intellij.javaee.appServers.deployment.DeploymentModel
import com.intellij.javaee.appServers.deployment.DeploymentSource
import com.intellij.javaee.appServers.run.configuration.CommonModel
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.util.Pair as IdeaPair
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.lang.reflect.Proxy

class ResinPersistentDataHelperCachingTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `stable resin home reuses the installation and changing home invalidates it`() {
        val firstHome = createResinHome("first-home")
        val secondHome = createResinHome("second-home")
        val persistentData = ResinPersistentData().apply { RESIN_HOME = firstHome.absolutePath }
        val helper = ResinPersistentDataHelper(applicationServer { persistentData })

        val firstInstallation = requireNotNull(helper.getInstallationOrError())
        val repeatedInstallation = requireNotNull(helper.getInstallationOrError())

        assertSame(firstInstallation, repeatedInstallation)
        assertSame(firstInstallation.getVersion(), repeatedInstallation.getVersion())

        persistentData.RESIN_HOME = secondHome.absolutePath
        val changedInstallation = requireNotNull(helper.getInstallationOrError())

        assertNotSame(firstInstallation, changedInstallation)
        assertEquals(secondHome.canonicalFile, changedInstallation.getResinHome().canonicalFile)
        assertSame(changedInstallation, helper.getInstallationOrError())
    }

    @Test
    fun `invalid home is retried instead of poisoning the cache`() {
        val lateHome = File(temporaryFolder.root, "late-home")
        val persistentData = ResinPersistentData().apply { RESIN_HOME = lateHome.absolutePath }
        val helper = ResinPersistentDataHelper(applicationServer { persistentData })

        assertThrows(ExecutionException::class.java) {
            helper.getInstallationOrError()
        }

        check(lateHome.mkdir())
        check(File(lateHome, "bin").mkdir())
        check(File(lateHome, "lib").mkdir())

        val installation = requireNotNull(helper.getInstallationOrError())
        assertEquals(lateHome.canonicalFile, installation.getResinHome().canonicalFile)

        val libDirectory = File(lateHome, "lib")
        check(libDirectory.delete())
        assertThrows(ExecutionException::class.java) {
            helper.getInstallationOrError()
        }
        check(libDirectory.mkdir())

        assertNotSame(installation, helper.getInstallationOrError())
    }

    @Test
    fun `model reuses helper for one application server and replaces it when selection changes`() {
        val firstHome = createResinHome("model-first-home")
        val secondHome = createResinHome("model-second-home")
        val firstData = ResinPersistentData().apply { RESIN_HOME = firstHome.absolutePath }
        val secondData = ResinPersistentData().apply { RESIN_HOME = secondHome.absolutePath }
        val firstServer = applicationServer { firstData }
        val secondServer = applicationServer { secondData }
        var selectedServer: ApplicationServer? = firstServer
        val model = HelperCachingModel()
        model.setCommonModel(commonModel { selectedServer })

        val firstHelper = model.helper
        assertSame(firstHelper, model.helper)
        assertSame(firstHelper.getInstallationOrError(), model.installation)

        selectedServer = secondServer
        val secondHelper = model.helper

        assertNotSame(firstHelper, secondHelper)
        assertSame(secondHelper, model.helper)
        assertEquals(secondHome.canonicalFile, model.installation?.getResinHome()?.canonicalFile)
    }

    private fun createResinHome(name: String): File {
        val home = temporaryFolder.newFolder(name)
        check(File(home, "bin").mkdir())
        check(File(home, "lib").mkdir())
        return home
    }

    private fun applicationServer(
        persistentData: () -> ResinPersistentData,
    ): ApplicationServer {
        return Proxy.newProxyInstance(
            javaClass.classLoader,
            arrayOf(ApplicationServer::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "getPersistentData" -> persistentData()
                else -> throw AssertionError("Unexpected ApplicationServer call: ${method.name}")
            }
        } as ApplicationServer
    }

    private fun commonModel(applicationServer: () -> ApplicationServer?): CommonModel {
        return Proxy.newProxyInstance(
            javaClass.classLoader,
            arrayOf(CommonModel::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "getApplicationServer" -> applicationServer()
                else -> throw AssertionError("Unexpected CommonModel call: ${method.name}")
            }
        } as CommonModel
    }

    private class HelperCachingModel : ResinModelBase<ResinModelDataBase>() {
        override fun createResinModelData(): ResinModelDataBase = ResinModelDataBase()

        override fun transferFile(webAppFile: File): Boolean =
            throw AssertionError("File transfer is not needed by this fixture")

        override fun deleteFile(webAppFile: File): Boolean =
            throw AssertionError("File deletion is not needed by this fixture")

        override fun createAdditionalDeploymentSettingsEditor(
            commonModel: CommonModel,
            source: DeploymentSource,
        ): SettingsEditor<DeploymentModel>? = null

        override fun getEditor(): SettingsEditor<CommonModel> =
            throw AssertionError("Editor is not needed by this fixture")

        override fun getAddressesToCheck(): List<IdeaPair<String, Int>> = emptyList()
    }
}
