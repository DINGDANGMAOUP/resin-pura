package com.dingdangmaoup.resin.pura

import com.dingdangmaoup.resin.pura.resin.ResinConfiguration
import com.dingdangmaoup.resin.pura.resin.WebApp
import com.dingdangmaoup.resin.pura.resin.common.DeploymentProviderEx
import com.dingdangmaoup.resin.pura.resin.configuration.JmxConfigurationStrategy
import com.intellij.execution.ExecutionException
import com.intellij.javaee.appServers.deployment.DeploymentMethod
import com.intellij.javaee.appServers.deployment.DeploymentModel
import com.intellij.javaee.appServers.deployment.DeploymentSource
import com.intellij.javaee.appServers.deployment.DeploymentStatus
import com.intellij.javaee.appServers.run.configuration.CommonModel
import com.intellij.javaee.appServers.serverInstances.J2EEServerInstance
import com.intellij.javaee.util.DeployStateChecker
import com.intellij.javaee.web.artifact.WebArtifactUtil
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.packaging.artifacts.ArtifactType

class ResinDeploymentProvider : DeploymentProviderEx() {
    override fun doDeploy(project: Project, instance: J2EEServerInstance, deploymentModel: DeploymentModel) {
        getDeploymentMethod(deploymentModel).doDeploy(project, instance, deploymentModel)
    }

    override fun createNewDeploymentModel(commonModel: CommonModel, source: DeploymentSource): DeploymentModel {
        return ResinModuleDeploymentModel(commonModel, source)
    }

    override fun createAdditionalDeploymentSettingsEditor(
        commonModel: CommonModel,
        source: DeploymentSource,
    ): SettingsEditor<DeploymentModel>? {
        val resinModel = commonModel.serverModel as ResinModelBase<*>
        return resinModel.createAdditionalDeploymentSettingsEditor(commonModel, source)
    }

    override fun getSupportedArtifactTypes(): Collection<ArtifactType> {
        return listOf(
            WebArtifactUtil.getInstance().explodedWarArtifactType,
            WebArtifactUtil.getInstance().warArtifactType,
        )
    }

    override fun startUndeploy(instance: J2EEServerInstance, deploymentModel: DeploymentModel) {
        getDeploymentMethod(deploymentModel).startUndeploy(instance, deploymentModel)
    }

    override fun updateDeploymentStatus(j2EEServerInstance: J2EEServerInstance, deploymentModel: DeploymentModel) {
    }

    override fun getAvailableMethods(): Array<DeploymentMethod> = DEPLOYMENT_METHODS

    private fun getDeploymentMethod(deploymentModel: DeploymentModel): ResinDeploymentMethod {
        val method = deploymentModel.deploymentMethod
        return method as? ResinDeploymentMethod ?: DEFAULT_DEPLOYMENT_METHOD
    }

    private abstract class ResinDeploymentMethod(name: String, local: Boolean, remote: Boolean) :
        DeploymentMethod(name, local, remote) {
        abstract fun doDeploy(project: Project, instance: J2EEServerInstance, deploymentModel: DeploymentModel)
        abstract fun startUndeploy(instance: J2EEServerInstance, deploymentModel: DeploymentModel)
    }

    companion object {
        private val LOG = Logger.getInstance(ResinDeploymentProvider::class.java)

        @JvmField
        val JMX_DEPLOYMENT_METHOD: DeploymentMethod =
            object : ResinDeploymentMethod(ResinBundle.message("ResinDeploymentProvider.deploy.method.jmx.name"), true, true) {
                override fun isApplicable(commonModel: CommonModel): Boolean {
                    return super.isApplicable(commonModel) && (commonModel.serverModel as ResinModelBase<*>).hasJmxStrategy()
                }

                override fun doDeploy(project: Project, instance: J2EEServerInstance, deploymentModel: DeploymentModel) {
                    val strategy = getJmxStrategy(deploymentModel)
                    val serverModel = deploymentModel.serverModel as ResinModelBase<*>
                    val webApp = getWebApp(deploymentModel)
                    val success = strategy != null && webApp != null && strategy.deployWithJmx(serverModel, webApp)
                    if (success) {
                        setDeploymentStatus(instance, deploymentModel, DeploymentStatus.UNKNOWN)
                        (instance as ResinServerInstance).getPoller().putDeployStateChecker(object : DeployStateChecker {
                            override fun getDeploymentModel(): DeploymentModel = deploymentModel

                            override fun check(): Boolean {
                                val isFinal = Ref.create(false)
                                setDeploymentStatus(
                                    instance,
                                    deploymentModel,
                                    strategy.getDeployStateWithJmx(serverModel, webApp, isFinal),
                                )
                                return isFinal.get()
                            }
                        })
                    } else {
                        setDeploymentStatus(instance, deploymentModel, DeploymentStatus.FAILED)
                    }
                }

                override fun startUndeploy(instance: J2EEServerInstance, deploymentModel: DeploymentModel) {
                    (instance as ResinServerInstance).getPoller().removeDeployStateChecker(deploymentModel)
                    val strategy = getJmxStrategy(deploymentModel)
                    val webApp = getWebApp(deploymentModel)
                    val success = strategy != null && webApp != null &&
                        strategy.undeployWithJmx(deploymentModel.serverModel as ResinModelBase<*>, webApp)
                    setDeploymentStatus(
                        instance,
                        deploymentModel,
                        if (success) DeploymentStatus.NOT_DEPLOYED else DeploymentStatus.UNKNOWN,
                    )
                }

                private fun getJmxStrategy(deploymentModel: DeploymentModel): JmxConfigurationStrategy? {
                    return (deploymentModel.serverModel as ResinModelBase<*>).jmxStrategy
                }
            }

        @JvmField
        val CONF_DEPLOYMENT_METHOD: DeploymentMethod =
            object : ResinDeploymentMethod(ResinBundle.message("ResinDeploymentProvider.deploy.method.conf.name"), true, false) {
                override fun doDeploy(project: Project, instance: J2EEServerInstance, deploymentModel: DeploymentModel) {
                    setDeploymentStatus(instance, deploymentModel, DeploymentStatus.DEPLOYED)
                }

                override fun startUndeploy(instance: J2EEServerInstance, deploymentModel: DeploymentModel) {
                    var success = false
                    try {
                        val resinModel = deploymentModel.serverModel as ResinModel
                        val resinConfiguration: ResinConfiguration = resinModel.getOrCreateResinConfiguration(false)
                        val webApp = getWebApp(deploymentModel)
                        success = webApp != null && resinConfiguration.undeploy(webApp)
                    } catch (e: ExecutionException) {
                        LOG.error(e)
                    }
                    setDeploymentStatus(
                        instance,
                        deploymentModel,
                        if (success) DeploymentStatus.NOT_DEPLOYED else DeploymentStatus.UNKNOWN,
                    )
                }
            }

        private val DEFAULT_DEPLOYMENT_METHOD: ResinDeploymentMethod
            get() = JMX_DEPLOYMENT_METHOD as ResinDeploymentMethod

        private val DEPLOYMENT_METHODS: Array<DeploymentMethod> = arrayOf(JMX_DEPLOYMENT_METHOD, CONF_DEPLOYMENT_METHOD)

        @JvmStatic
        fun getWebApp(deploymentModel: DeploymentModel): WebApp? {
            val resinModel = deploymentModel as ResinModuleDeploymentModel
            val filePath = deploymentModel.deploymentSource.filePath
            return if (filePath == null) {
                null
            } else {
                WebApp(
                    resinModel.isDefaultContextPath,
                    resinModel.contextPath,
                    resinModel.host,
                    filePath,
                    (deploymentModel.serverModel as ResinModelBase<*>).charset,
                )
            }
        }
    }
}
