package com.dingdangmaoup.resin.pura.runConfiguration

import com.intellij.execution.Executor
import com.intellij.execution.configurations.*
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessHandlerFactory
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project


class PluginRunConfiguration(
    project: Project,
    factory: ConfigurationFactory,
    name: String
) : RunConfigurationBase<PluginRunConfigurationOptions>(project, factory, name) {

    override fun getOptions(): PluginRunConfigurationOptions {
        return super.getOptions() as PluginRunConfigurationOptions
    }

    fun getScriptName(): String? {
        return options.getScriptName()
    }

    fun setScriptName(scriptName: String) {
        options.setScriptName(scriptName)
    }

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> {
        return PluginSettingsEditor()
    }

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState {
        return object : CommandLineState(environment) {
            override fun startProcess(): ProcessHandler {
                val commandLine: GeneralCommandLine =
                    GeneralCommandLine(options.getScriptName())
                val processHandler = ProcessHandlerFactory.getInstance()
                    .createColoredProcessHandler(commandLine)
                ProcessTerminatedListener.attach(processHandler)
                return processHandler
            }
        }
    }


}