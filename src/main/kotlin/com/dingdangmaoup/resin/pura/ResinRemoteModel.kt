package com.dingdangmaoup.resin.pura

import com.dingdangmaoup.resin.pura.ui.RemoteRunConfigurationEditor
import com.dingdangmaoup.resin.pura.resin.jmx.JmxCredentialStore
import com.dingdangmaoup.resin.pura.resin.jmx.JmxCredentialEdit
import com.dingdangmaoup.resin.pura.resin.jmx.JmxCredentials
import com.dingdangmaoup.resin.pura.resin.jmx.JmxEndpoint
import com.dingdangmaoup.resin.pura.resin.jmx.PasswordSafeJmxCredentialStore
import com.dingdangmaoup.resin.pura.resin.jmx.applyCredentialEdit
import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.execution.configurations.RuntimeConfigurationException
import com.intellij.javaee.appServers.deployment.DeploymentModel
import com.intellij.javaee.appServers.deployment.DeploymentSource
import com.intellij.javaee.appServers.run.configuration.CommonModel
import com.intellij.javaee.transport.TransportHost
import com.intellij.javaee.transport.TransportHostTarget
import com.intellij.javaee.transport.TransportManager
import com.intellij.javaee.transport.TransportTarget
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.util.Pair
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.xmlb.annotations.Tag
import java.io.File
import java.util.Collections

class ResinRemoteModel() : ResinModelBase<ResinRemoteModel.ResinRemoteModelData>() {
    private var myJmxCredentialStore: JmxCredentialStore? = null

    internal constructor(jmxCredentialStore: JmxCredentialStore) : this() {
        myJmxCredentialStore = jmxCredentialStore
    }

    override fun getEditor(): SettingsEditor<CommonModel> = RemoteRunConfigurationEditor(project)

    override fun getAddressesToCheck(): List<Pair<String, Int>> = Collections.emptyList()

    @Throws(RuntimeConfigurationException::class)
    override fun checkConfiguration() {
        val hasDeployments = getCommonModel().deploymentModels.isNotEmpty()
        if (!hasJmxStrategy() && hasDeployments) {
            throw RuntimeConfigurationError(ResinBundle.message("remote.config.error.resin2x.no.deployment"))
        }
        if (hasDeployments && getTransportHostId().isNullOrBlank()) {
            throw RuntimeConfigurationError(ResinBundle.message("remote.config.error.transport.host.required"))
        }
        if (hasDeployments && getHost() == null) {
            throw RuntimeConfigurationError(ResinBundle.message("remote.config.error.transport.host.not.found"))
        }
        val target = getTransportTargetWebApps()
        if (hasDeployments && (target == null || target.id == null)) {
            throw RuntimeConfigurationError(ResinBundle.message("remote.config.error.transport.target.required"))
        }
        super.checkConfiguration()
    }

    fun getTransportHostId(): String? = data.getTransportHostId()

    fun setTransportHostId(transportHostId: String?) {
        data.setTransportHostId(transportHostId)
    }

    fun getTransportTargetWebApps(): TransportTarget? = data.getTransportTargetWebApps()

    fun setTransportTargetWebApps(transportTargetWebApps: TransportTarget?) {
        data.setTransportTargetWebApps(transportTargetWebApps)
    }

    override fun createResinModelData(): ResinRemoteModelData = ResinRemoteModelData()

    override fun getJmxCredentials(): JmxCredentials? =
        loadJmxCredentials(JmxEndpoint.of(getCommonModel().host, jmxPort))

    internal fun loadJmxCredentials(endpoint: JmxEndpoint): JmxCredentials? = getJmxCredentialStore().load(endpoint)

    internal fun applyJmxCredentialEdit(
        originalEndpoint: JmxEndpoint,
        targetEndpoint: JmxEndpoint,
        edit: JmxCredentialEdit,
    ) = applyCredentialEdit(getJmxCredentialStore(), originalEndpoint, targetEndpoint, edit)

    private fun getJmxCredentialStore(): JmxCredentialStore {
        val existing = myJmxCredentialStore
        if (existing != null) return existing
        return PasswordSafeJmxCredentialStore().also { myJmxCredentialStore = it }
    }

    private fun getHost(): TransportHost? {
        return TransportManager.getInstance().findHost(getTransportHostId(), project)
    }

    private fun getTransportHostTarget(): TransportHostTarget? {
        val host = getHost()
        return host?.findOrCreateHostTarget(getTransportTargetWebApps())
    }

    override fun transferFile(webAppFile: File): Boolean {
        val target = getTransportHostTarget() ?: return false
        val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(webAppFile) ?: return false
        return target.transfer(
            project,
            Collections.singletonList(virtualFile),
        )
    }

    override fun deleteFile(webAppFile: File): Boolean {
        val target = getTransportHostTarget()
        val vFile: VirtualFile =
            LocalFileSystem.getInstance().refreshAndFindFileByIoFile(webAppFile) ?: return true
        return target != null && target.delete(project, Collections.singletonList(vFile))
    }

    override fun createAdditionalDeploymentSettingsEditor(
        commonModel: CommonModel,
        source: DeploymentSource,
    ): SettingsEditor<DeploymentModel>? {
        return null
    }

    class ResinRemoteModelData : ResinModelDataBase() {
        @field:Tag("host-id")
        private var myTransportHostId: String? = null

        @field:Tag("transport-target-webapps")
        private var myTransportTargetWebApps: TransportTarget? = null

        fun getTransportHostId(): String? = myTransportHostId

        fun setTransportHostId(transportHostId: String?) {
            myTransportHostId = transportHostId
        }

        fun getTransportTargetWebApps(): TransportTarget? = myTransportTargetWebApps

        fun setTransportTargetWebApps(transportTargetWebApps: TransportTarget?) {
            myTransportTargetWebApps = transportTargetWebApps
        }
    }
}
