package com.dingdangmaoup.resin.pura

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.javaee.appServers.appServerIntegrations.AppServerIntegration
import com.intellij.javaee.appServers.run.configuration.J2EEConfigurationFactory
import com.intellij.javaee.appServers.run.configuration.JavaeeAppServerConfigurationType
import com.intellij.openapi.project.Project
import javax.swing.Icon

class ResinConfigurationType : JavaeeAppServerConfigurationType("ResinConfigurationType") {
    override fun getDisplayName(): String = ResinBundle.message("run.config.tab.title.resin")

    override fun getConfigurationTypeDescription(): String = ResinBundle.message("run.config.tab.description.resin")

    override fun getIcon(): Icon = ResinManager.ICON_RESIN

    override fun getHelpTopic(): String = "reference.dialogs.rundebug.ResinConfigurationType"

    override fun createJ2EEConfigurationTemplate(
        factory: ConfigurationFactory,
        project: Project,
        isLocal: Boolean,
    ): RunConfiguration {
        return J2EEConfigurationFactory.getInstance().createJ2EERunConfiguration(
            factory,
            project,
            createServerModel(isLocal),
            ResinManager.getInstance(),
            isLocal,
            if (isLocal) ResinStartupPolicy() else null,
        )
    }

    override fun getIntegration(): AppServerIntegration = ResinManager.getInstance()

    companion object {
        internal fun createServerModel(isLocal: Boolean): ResinModelBase<*> =
            if (isLocal) ResinModel().apply { port = ResinUtil.DEFAULT_PORT } else ResinRemoteModel()

        @JvmStatic
        fun getInstance(): ResinConfigurationType =
            ConfigurationTypeUtil.findConfigurationType(ResinConfigurationType::class.java)
    }
}
