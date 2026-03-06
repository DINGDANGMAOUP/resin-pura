package com.dingdangmaoup.resin.pura.resin.common

import com.intellij.javaee.appServers.deployment.DeploymentManager
import com.intellij.javaee.appServers.deployment.DeploymentModel
import com.intellij.javaee.appServers.deployment.DeploymentProvider
import com.intellij.javaee.appServers.deployment.DeploymentStatus
import com.intellij.javaee.appServers.run.configuration.CommonModel
import com.intellij.javaee.appServers.serverInstances.J2EEServerInstance

abstract class DeploymentProviderEx : DeploymentProvider() {
    companion object {
        @JvmStatic
        protected fun setDeploymentStatus(instance: J2EEServerInstance, model: DeploymentModel, status: DeploymentStatus) {
            val configuration: CommonModel = instance.commonModel
            val project = configuration.project
            DeploymentManager.getInstance(project).setDeploymentStatus(model, status, configuration, instance)
        }
    }
}
