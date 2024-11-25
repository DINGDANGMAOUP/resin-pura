package com.dingdangmaoup.resin.pura.runConfiguration

import com.dingdangmaoup.resin.pura.IconBundle
import com.intellij.execution.configurations.ConfigurationTypeBase
import com.intellij.openapi.util.NotNullLazyValue

class PluginRunConfigurationType :
    ConfigurationTypeBase(
        ID, "Resin Pura", "Resin Pura run configuration type",
        NotNullLazyValue.createValue { IconBundle.ResinIcon }
    ) {
    companion object {
         const val ID = "PluginRunConfigurationType"
    }

    init {
        addFactory(PluginRunConfigurationFactory(this))
    }

}