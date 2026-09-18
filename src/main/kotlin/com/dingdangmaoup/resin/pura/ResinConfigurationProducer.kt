package com.dingdangmaoup.resin.pura

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.LazyRunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.javaee.appServers.run.configuration.CommonModel
import com.intellij.javaee.appServers.run.configuration.J2EEConfigurationFactory
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import org.jdom.Element

class ResinConfigurationProducer : LazyRunConfigurationProducer<RunConfiguration>() {
    override fun getConfigurationFactory(): ConfigurationFactory = ResinConfigurationType.getInstance().localFactory

    override fun setupConfigurationFromContext(
        configuration: RunConfiguration,
        context: ConfigurationContext,
        sourceElement: Ref<PsiElement>,
    ): Boolean {
        val file = context.psiLocation?.containingFile ?: return false
        // Keep the platform's file eligibility, server selection and artifact/build-task setup.
        val generated = J2EEConfigurationFactory.getInstance()
            .createSettingsByFile(file, ResinConfigurationType.getInstance()) ?: return false
        copyGeneratedConfiguration(generated.configuration, configuration)
        sourceElement.set(file)
        return true
    }

    override fun isConfigurationFromContext(configuration: RunConfiguration, context: ConfigurationContext): Boolean {
        val model = configuration as? CommonModel ?: return false
        if (!model.isLocal || model.serverModel !is ResinModel) return false
        val file = context.psiLocation?.containingFile ?: return false
        val generated = J2EEConfigurationFactory.getInstance()
            .createSettingsByFile(file, ResinConfigurationType.getInstance())?.configuration as? CommonModel ?: return false
        return matchesGeneratedConfiguration(model, generated)
    }

    companion object {
        internal fun copyGeneratedConfiguration(source: RunConfiguration, target: RunConfiguration) {
            val state = Element("configuration")
            source.writeExternal(state)
            target.readExternal(state)
            target.name = source.name
            // Before-run tasks are held separately from the configuration's XML state.
            target.beforeRunTasks = source.beforeRunTasks.map { it.clone() }
            target.isAllowRunningInParallel = source.isAllowRunningInParallel
        }

        internal fun matchesGeneratedConfiguration(existing: CommonModel, generated: CommonModel): Boolean =
            existing.isLocal && existing.serverModel is ResinModel &&
                existing.applicationServer != null && existing.applicationServer == generated.applicationServer &&
                !existing.urlToOpenInBrowser.isNullOrBlank() &&
                existing.urlToOpenInBrowser == generated.urlToOpenInBrowser &&
                existing.deployedArtifacts.toSet() == generated.deployedArtifacts.toSet()
    }
}
