package com.dingdangmaoup.resin.pura.ui

import com.dingdangmaoup.resin.pura.ResinModelBase
import com.dingdangmaoup.resin.pura.resin.ResinPersistentDataHelper
import com.intellij.javaee.appServers.appServerIntegrations.ApplicationServer
import com.intellij.javaee.appServers.run.configuration.ApplicationServerSelectionListener
import com.intellij.javaee.appServers.run.configuration.CommonModel
import com.intellij.openapi.options.SettingsEditor

abstract class ResinRunConfigurationEditorBase : SettingsEditor<CommonModel>(), ApplicationServerSelectionListener {
    override fun serverSelected(server: ApplicationServer?) {
        serverChanged(server)
    }

    override fun serverProbablyEdited(server: ApplicationServer?) {
        serverChanged(server)
    }

    private fun serverChanged(server: ApplicationServer?) {
        setJmxPortVisible(ResinPersistentDataHelper(server).hasJmxStrategy())
    }

    protected fun updateJmxPortVisible(model: ResinModelBase<*>) {
        setJmxPortVisible(model.hasJmxStrategy())
    }

    protected abstract fun setJmxPortVisible(visible: Boolean)
}
