package com.dingdangmaoup.resin.pura

import com.dingdangmaoup.resin.pura.resin.ResinInstallation
import com.dingdangmaoup.resin.pura.ui.SelectResinLocationEditor
import com.intellij.execution.ExecutionException
import com.intellij.javaee.appServers.appServerIntegrations.ApplicationServerHelper
import com.intellij.javaee.appServers.appServerIntegrations.ApplicationServerInfo
import com.intellij.javaee.appServers.appServerIntegrations.ApplicationServerPersistentData
import com.intellij.javaee.appServers.appServerIntegrations.ApplicationServerPersistentDataEditor
import com.intellij.javaee.appServers.appServerIntegrations.CantFindApplicationServerJarsException

class ResinApplicationServerHelper : ApplicationServerHelper {
    @Throws(CantFindApplicationServerJarsException::class)
    override fun getApplicationServerInfo(persistentData: ApplicationServerPersistentData): ApplicationServerInfo {
        try {
            val resinPersistentData = persistentData as ResinPersistentData
            val resinInstallation = ResinInstallation.create(resinPersistentData.RESIN_HOME)
            val resinLib = resinInstallation.getLibFiles(resinPersistentData.INCLUDE_ALL_JARS)
            val version = resinInstallation.getDisplayName()
            return ApplicationServerInfo(resinLib, version)
        } catch (e: ExecutionException) {
            throw CantFindApplicationServerJarsException(e.message)
        }
    }

    override fun createPersistentDataEmptyInstance(): ApplicationServerPersistentData {
        return ResinPersistentData()
    }

    override fun createConfigurable(): ApplicationServerPersistentDataEditor<ResinPersistentData> {
        return SelectResinLocationEditor()
    }
}
