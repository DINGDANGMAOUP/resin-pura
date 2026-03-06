package com.dingdangmaoup.resin.pura.resin.configuration

import com.dingdangmaoup.resin.pura.ResinModelBase
import com.dingdangmaoup.resin.pura.resin.WebApp
import com.intellij.javaee.appServers.deployment.DeploymentStatus
import com.intellij.openapi.util.Ref

interface JmxConfigurationStrategy {
    fun deployWithJmx(resinModel: ResinModelBase<*>, webApp: WebApp): Boolean

    fun undeployWithJmx(resinModel: ResinModelBase<*>, webApp: WebApp): Boolean

    fun getDeployStateWithJmx(resinModel: ResinModelBase<*>, webApp: WebApp, isFinal: Ref<Boolean>): DeploymentStatus
}
