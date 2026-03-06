package com.dingdangmaoup.resin.pura.resin

import com.dingdangmaoup.resin.pura.ResinPersistentData
import com.dingdangmaoup.resin.pura.resin.configuration.JmxConfigurationStrategy
import com.dingdangmaoup.resin.pura.resin.configuration.ResinConfigurationStrategy
import com.intellij.execution.ExecutionException
import com.intellij.javaee.appServers.appServerIntegrations.ApplicationServer
import com.intellij.openapi.diagnostic.Logger

class ResinPersistentDataHelper(private val applicationServer: ApplicationServer?) {
    fun getPersistentData(): ResinPersistentData? {
        return applicationServer?.persistentData as? ResinPersistentData
    }

    fun getInstallation(): ResinInstallation? {
        return try {
            getInstallationOrError()
        } catch (e: ExecutionException) {
            LOG.debug(e)
            null
        }
    }

    @Throws(ExecutionException::class)
    fun getInstallationOrError(): ResinInstallation? {
        val persistentData = getPersistentData()
        return if (persistentData != null) {
            ResinInstallation.create(persistentData.RESIN_HOME)
        } else {
            null
        }
    }

    fun getJmxStrategy(): JmxConfigurationStrategy? {
        val strategy = getStrategy()
        return strategy as? JmxConfigurationStrategy
    }

    fun hasJmxStrategy(): Boolean {
        return getJmxStrategy() != null
    }

    fun getStrategy(): ResinConfigurationStrategy? {
        val installation = getInstallation()
        return if (installation == null) {
            null
        } else {
            ResinConfigurationStrategy.getForInstallation(installation)
        }
    }

    companion object {
        private val LOG = Logger.getInstance(ResinPersistentDataHelper::class.java)
    }
}
