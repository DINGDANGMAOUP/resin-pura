package com.dingdangmaoup.resin.pura

import com.dingdangmaoup.resin.pura.resin.WebApp
import com.dingdangmaoup.resin.pura.resin.DeploymentObservation
import com.dingdangmaoup.resin.pura.resin.DeploymentWait
import com.dingdangmaoup.resin.pura.resin.DeploymentOperation
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
        val operation = beginOperation(instance, deploymentModel, false)
        getDeploymentMethod(deploymentModel).doDeploy(project, instance, deploymentModel, operation)
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
        val operation = beginOperation(instance, deploymentModel, true)
        getDeploymentMethod(deploymentModel).startUndeploy(instance, deploymentModel, operation)
    }

    override fun updateDeploymentStatus(j2EEServerInstance: J2EEServerInstance, deploymentModel: DeploymentModel) {
        if (j2EEServerInstance.isStopped) return
        val model = deploymentModel.serverModel as ResinModelBase<*>
        val strategy = model.jmxStrategy ?: return
        val webApp = getWebApp(deploymentModel) ?: return
        val operation = (j2EEServerInstance as ResinServerInstance).deploymentOperations[deploymentModel] ?: return
        val observation = if (deploymentModel.deploymentMethod == CONF_DEPLOYMENT_METHOD) {
            strategy.observeConfiguration(model, webApp, operation.removing)
        } else {
            strategy.observeArchive(model, webApp, operation.removing)
        }
        setCurrentStatus(j2EEServerInstance, deploymentModel, operation, observation.status)
    }

    override fun getAvailableMethods(): Array<DeploymentMethod> = DEPLOYMENT_METHODS

    private fun getDeploymentMethod(deploymentModel: DeploymentModel): ResinDeploymentMethod {
        val method = deploymentModel.deploymentMethod
        return method as? ResinDeploymentMethod ?: DEFAULT_DEPLOYMENT_METHOD
    }

    private abstract class ResinDeploymentMethod(name: String, local: Boolean, remote: Boolean) :
        DeploymentMethod(name, local, remote) {
        abstract fun doDeploy(project: Project, instance: J2EEServerInstance, deploymentModel: DeploymentModel, operation: DeploymentOperation)
        abstract fun startUndeploy(instance: J2EEServerInstance, deploymentModel: DeploymentModel, operation: DeploymentOperation)
    }

    companion object {
        private val LOG = Logger.getInstance(ResinDeploymentProvider::class.java)

        @JvmField
        val JMX_DEPLOYMENT_METHOD: DeploymentMethod =
            object : ResinDeploymentMethod(ResinBundle.message("ResinDeploymentProvider.deploy.method.jmx.name"), true, true) {
                override fun isApplicable(commonModel: CommonModel): Boolean {
                    return super.isApplicable(commonModel) && (commonModel.serverModel as ResinModelBase<*>).hasJmxStrategy()
                }

                override fun doDeploy(project: Project, instance: J2EEServerInstance, deploymentModel: DeploymentModel, operation: DeploymentOperation) {
                    val strategy = getJmxStrategy(deploymentModel)
                    val serverModel = deploymentModel.serverModel as ResinModelBase<*>
                    val webApp = getWebApp(deploymentModel)
                    val success = strategy != null && webApp != null && strategy.deployWithJmx(serverModel, webApp)
                    if (success) {
                        setCurrentStatus(instance, deploymentModel, operation, DeploymentStatus.UNKNOWN)
                        observeDeployment(instance, deploymentModel, operation) {
                            val terminal = Ref.create(false)
                            DeploymentObservation(strategy.getDeployStateWithJmx(serverModel, webApp, terminal), terminal.get())
                        }
                    } else {
                        setCurrentStatus(instance, deploymentModel, operation, DeploymentStatus.FAILED)
                    }
                }

                override fun startUndeploy(instance: J2EEServerInstance, deploymentModel: DeploymentModel, operation: DeploymentOperation) {
                    (instance as ResinServerInstance).getPoller().removeDeployStateChecker(deploymentModel)
                    val strategy = getJmxStrategy(deploymentModel)
                    val webApp = getWebApp(deploymentModel)
                    val success = strategy != null && webApp != null &&
                        strategy.undeployWithJmx(deploymentModel.serverModel as ResinModelBase<*>, webApp)
                    setCurrentStatus(
                        instance,
                        deploymentModel,
                        operation,
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
                override fun isApplicable(commonModel: CommonModel): Boolean =
                    super.isApplicable(commonModel) && (commonModel.serverModel as? ResinModel)?.isReadOnlyConfiguration() == false

                override fun doDeploy(project: Project, instance: J2EEServerInstance, deploymentModel: DeploymentModel, operation: DeploymentOperation) {
                    changeConfiguration(instance, deploymentModel, operation)
                }

                override fun startUndeploy(instance: J2EEServerInstance, deploymentModel: DeploymentModel, operation: DeploymentOperation) {
                    changeConfiguration(instance, deploymentModel, operation)
                }
            }

        private fun changeConfiguration(instance: J2EEServerInstance, deploymentModel: DeploymentModel, operation: DeploymentOperation) {
            val removing = operation.removing
            (instance as ResinServerInstance).getPoller().removeDeployStateChecker(deploymentModel)
            try {
                val model = deploymentModel.serverModel as ResinModel
                val webApp = getWebApp(deploymentModel)
                    ?: throw ExecutionException(ResinBundle.message("deployment.source.missing"))
                val configuration = model.runSession.configuration
                    ?: throw ExecutionException(ResinBundle.message("deployment.session.missing"))
                if (removing) configuration.undeploy(webApp) else configuration.deploy(webApp)
                // A successful XML write is not evidence that Resin has reloaded the application.
                setCurrentStatus(instance, deploymentModel, operation, DeploymentStatus.UNKNOWN)
                model.jmxStrategy?.let { strategy ->
                    observeDeployment(instance, deploymentModel, operation) {
                        strategy.observeConfiguration(model, webApp, removing)
                    }
                }
            } catch (e: ExecutionException) {
                LOG.warn("Resin configuration deployment failed", e)
                setCurrentStatus(instance, deploymentModel, operation, if (removing) DeploymentStatus.UNKNOWN else DeploymentStatus.FAILED)
            }
        }

        private fun observeDeployment(
            instance: J2EEServerInstance, deploymentModel: DeploymentModel, operation: DeploymentOperation,
            observe: () -> DeploymentObservation,
        ) {
            val wait = DeploymentWait()
            (instance as ResinServerInstance).getPoller().putDeployStateChecker(object : DeployStateChecker {
                override fun getDeploymentModel(): DeploymentModel = deploymentModel
                override fun check(): Boolean {
                    if (instance.isStopped || instance.deploymentOperations[deploymentModel] !== operation) return true
                    val observation = observe()
                    setCurrentStatus(instance, deploymentModel, operation, observation.status)
                    return wait.finish(observation)
                }
            })
        }

        private fun beginOperation(instance: J2EEServerInstance, model: DeploymentModel, removing: Boolean): DeploymentOperation {
            val server = instance as ResinServerInstance
            server.getPoller().removeDeployStateChecker(model)
            return synchronized(server.deploymentOperations) {
                DeploymentOperation(removing).also { server.deploymentOperations[model] = it }
            }
        }

        private fun setCurrentStatus(instance: J2EEServerInstance, model: DeploymentModel, operation: DeploymentOperation, status: DeploymentStatus) {
            val operations = (instance as ResinServerInstance).deploymentOperations
            synchronized(operations) {
                if (operations[model] === operation && !instance.isStopped) {
                    setDeploymentStatus(instance, model, status)
                }
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
