package com.dingdangmaoup.resin.pura

import com.dingdangmaoup.resin.pura.ui.RemoteRunConfigurationEditor
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

class ResinRemoteModel : ResinModelBase<ResinRemoteModel.ResinRemoteModelData>() {
    override fun getEditor(): SettingsEditor<CommonModel> = RemoteRunConfigurationEditor(project)

    override fun getAddressesToCheck(): List<Pair<String, Int>> = Collections.emptyList()

    @Throws(RuntimeConfigurationException::class)
    override fun checkConfiguration() {
        if (!hasJmxStrategy() && getCommonModel().deploymentModels.isNotEmpty()) {
            throw RuntimeConfigurationError("Remote deployment is not supported for Resin 2.x")
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

    private fun getHost(): TransportHost? {
        return TransportManager.getInstance().findHost(getTransportHostId(), project)
    }

    private fun getTransportHostTarget(): TransportHostTarget? {
        val host = getHost()
        return if (host == null) null else host.findOrCreateHostTarget(getTransportTargetWebApps())
    }

    override fun transferFile(webAppFile: File): Boolean {
        val target = getTransportHostTarget()
        return target != null && target.transfer(
            project,
            Collections.singletonList(LocalFileSystem.getInstance().refreshAndFindFileByIoFile(webAppFile)),
        )
    }

    override fun deleteFile(webAppFile: File): Boolean {
        val target = getTransportHostTarget()
        val vFile: VirtualFile? = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(webAppFile)
        if (vFile == null) {
            return true
        }
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
