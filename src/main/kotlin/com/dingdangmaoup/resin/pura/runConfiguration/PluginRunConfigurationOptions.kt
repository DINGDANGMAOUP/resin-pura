package com.dingdangmaoup.resin.pura.runConfiguration

import com.intellij.execution.configurations.RunConfigurationOptions
import com.intellij.openapi.components.StoredProperty



class PluginRunConfigurationOptions: RunConfigurationOptions() {
    private val pluginScriptName: StoredProperty<String?> = string("").provideDelegate(
        this, "scriptName"
    )

    fun getScriptName(): String? {
        return pluginScriptName.getValue(this)
    }

    fun setScriptName(scriptName: String) {
        pluginScriptName.setValue(this, scriptName)
    }
}