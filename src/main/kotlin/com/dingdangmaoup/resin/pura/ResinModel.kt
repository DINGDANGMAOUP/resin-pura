package com.dingdangmaoup.resin.pura

import com.dingdangmaoup.resin.pura.resin.ResinConfiguration
import com.dingdangmaoup.resin.pura.resin.ResinInstallation
import com.dingdangmaoup.resin.pura.ui.DeploymentSettingsEditor
import com.dingdangmaoup.resin.pura.ui.RunConfigurationEditor
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.RuntimeConfigurationException
import com.intellij.javaee.appServers.deployment.DeploymentModel
import com.intellij.javaee.appServers.deployment.DeploymentSource
import com.intellij.javaee.appServers.run.configuration.CommonModel
import com.intellij.javaee.directoryManager.SystemBaseDirectoryManager
import com.intellij.javaee.jmxremote.JmxRemoteAware
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import java.io.File
import java.io.IOException
import java.util.Collections

class ResinModel : ResinModelBase<ResinModel.ResinLocalModelData>(), JmxRemoteAware {
    private var myConfiguration: ResinConfiguration? = null
    private var myJmxUsername: String? = null
    private var myJmxPassword: String? = null
    private var myAccessFile: File? = null
    private var myPasswordFile: File? = null

    override fun getEditor(): SettingsEditor<CommonModel> = RunConfigurationEditor()

    override fun getAddressesToCheck(): List<Pair<String, Int>> {
        return Collections.singletonList(Pair.create(getCommonModel().host, localPort))
    }

    @Throws(ExecutionException::class)
    fun getOrCreateResinConfiguration(forceCreation: Boolean): ResinConfiguration {
        if (myConfiguration == null || forceCreation) {
            myConfiguration = ResinConfiguration(this)
        }
        return myConfiguration!!
    }

    @Throws(RuntimeConfigurationException::class)
    override fun checkConfiguration() {
        findConfFile()
        super.checkConfiguration()
    }

    @Throws(RuntimeConfigurationException::class)
    fun findConfFile(): File {
        var sourceConfig: File? = null
        val persistentData = helper.getPersistentData()
        if (persistentData != null && !StringUtil.isEmpty(persistentData.RESIN_CONF)) {
            sourceConfig = File(persistentData.RESIN_CONF)
        }

        val sourceConfigPath = getResinConf()
        if (!StringUtil.isEmpty(sourceConfigPath)) {
            sourceConfig = File(FileUtil.toSystemDependentName(sourceConfigPath))
        }

        if (sourceConfig == null) {
            throw RuntimeConfigurationException(ResinBundle.message("message.error.resin.conf.doesnt.chosen"))
        }
        if (!sourceConfig.exists()) {
            throw RuntimeConfigurationException(
                ResinBundle.message("message.error.resin.conf.doesnt.exist", sourceConfig.absolutePath)
            )
        }
        if (sourceConfig.isDirectory) {
            throw RuntimeConfigurationException(
                ResinBundle.message("message.error.resin.conf.directory", sourceConfig.absolutePath)
            )
        }
        return sourceConfig
    }

    fun isDebugConfiguration(): Boolean = data.isDebugConfiguration()

    fun setDebugConfiguration(debugConfiguration: Boolean) {
        data.setDebugConfiguration(debugConfiguration)
    }

    fun isAutoBuildClassPath(): Boolean = data.isAutoBuildClassPath()

    fun setAutoBuildClassPath(autoBuildClassPath: Boolean) {
        data.setAutoBuildClassPath(autoBuildClassPath)
    }

    fun getResinConf(): String = data.getResinConf()

    fun setResinConf(resinConf: String?) {
        data.setResinConf(resinConf)
    }

    fun isReadOnlyConfiguration(): Boolean = data.isReadOnlyConfiguration()

    fun setReadOnlyConfiguration(readOnlyConfiguration: Boolean) {
        data.setReadOnlyConfiguration(readOnlyConfiguration)
    }

    fun getAdditionalParameters(): String {
        val additionalParameters = data.getAdditionalParameters()
        return additionalParameters?.trim() ?: ""
    }

    fun setAdditionalParameters(additionalParameters: String?) {
        data.setAdditionalParameters(additionalParameters)
    }

    fun getDeployMode(): String {
        val deployMode = data.getDeployMode()
        return deployMode ?: DEPLOY_MODE_MANUAL
    }

    fun setDeployMode(deployMode: String?) {
        data.setDeployMode(deployMode)
    }

    override fun createResinModelData(): ResinLocalModelData = ResinLocalModelData()

    private fun getWebAppFileDestination(webAppFile: File): File? {
        val installation: ResinInstallation? = installation
        LOG.assertTrue(installation != null)
        if (installation == null) {
            return null
        }

        val webAppsDir = File(installation.getResinHome(), "webapps") // TODO: take from config
        if (!webAppsDir.exists()) {
            LOG.error("Can't find webapps folder")
            return null
        }

        return File(webAppsDir, webAppFile.name)
    }

    override fun transferFile(webAppFile: File): Boolean {
        val webAppFileDestination = getWebAppFileDestination(webAppFile) ?: return false
        return try {
            FileUtil.copyFileOrDir(webAppFile, webAppFileDestination)
            true
        } catch (e: IOException) {
            LOG.error(e)
            false
        }
    }

    override fun deleteFile(webAppFile: File): Boolean {
        val webAppFileDestination = getWebAppFileDestination(webAppFile) ?: return false
        return FileUtil.delete(webAppFileDestination)
    }

    override fun createAdditionalDeploymentSettingsEditor(
        commonModel: CommonModel,
        source: DeploymentSource,
    ): SettingsEditor<DeploymentModel> {
        return DeploymentSettingsEditor(commonModel, source)
    }

    override fun getJmxUsername(): String? = myJmxUsername

    override fun setJmxUsername(jmxUsername: String?) {
        myJmxUsername = jmxUsername
    }

    override fun getJmxPassword(): String? = myJmxPassword

    override fun setJmxPassword(jmxPassword: String?) {
        myJmxPassword = jmxPassword
    }

    override fun getSystemBaseDirectoryManager(): SystemBaseDirectoryManager {
        return ResinSystemBaseDirectoryManager.getInstance()
    }

    override fun getBaseDirectoryName(): String? = data.getBaseDirectoryName()

    override fun setBaseDirectoryName(baseDirectoryName: String?) {
        data.setBaseDirectoryName(baseDirectoryName)
    }

    fun getAccessFile(): File? = myAccessFile

    fun setAccessFile(accessFile: File?) {
        myAccessFile = accessFile
    }

    fun getPasswordFile(): File? = myPasswordFile

    fun setPasswordFile(passwordFile: File?) {
        myPasswordFile = passwordFile
    }

    class ResinLocalModelData : ResinModelDataBase() {
        private var myResinConf: String? = ""
        private var myDebugConfiguration = false
        private var myAutoBuildClassPath = false
        private var myReadOnlyConfiguration = false
        private var myAdditionalParameters: String? = ""
        private var myDeployMode: String? = DEPLOY_MODE_AUTO
        private var myBaseDirectoryName: String? = null

        fun getResinConf(): String = myResinConf ?: ""

        fun setResinConf(resinConf: String?) {
            myResinConf = resinConf
        }

        fun isDebugConfiguration(): Boolean = myDebugConfiguration

        fun setDebugConfiguration(debugConfiguration: Boolean) {
            myDebugConfiguration = debugConfiguration
        }

        fun isAutoBuildClassPath(): Boolean = myAutoBuildClassPath

        fun setAutoBuildClassPath(autoBuildClassPath: Boolean) {
            myAutoBuildClassPath = autoBuildClassPath
        }

        fun isReadOnlyConfiguration(): Boolean = myReadOnlyConfiguration

        fun setReadOnlyConfiguration(readOnlyConfiguration: Boolean) {
            myReadOnlyConfiguration = readOnlyConfiguration
        }

        fun getAdditionalParameters(): String? = myAdditionalParameters

        fun setAdditionalParameters(additionalParameters: String?) {
            myAdditionalParameters = additionalParameters
        }

        fun getDeployMode(): String? = myDeployMode

        fun setDeployMode(deployMode: String?) {
            myDeployMode = deployMode
        }

        fun getBaseDirectoryName(): String? = myBaseDirectoryName

        fun setBaseDirectoryName(baseDirectoryName: String?) {
            myBaseDirectoryName = baseDirectoryName
        }
    }

    companion object {
        private val LOG = Logger.getInstance(ResinModel::class.java)

        @JvmField
        val DEPLOY_MODE_AUTO: String = "automatic"

        @JvmField
        val DEPLOY_MODE_LAZY: String = "lazy"

        @JvmField
        val DEPLOY_MODE_MANUAL: String = "manual"
    }
}
