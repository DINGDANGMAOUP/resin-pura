package com.dingdangmaoup.resin.pura.resin

import com.dingdangmaoup.resin.pura.ResinPersistentData
import com.dingdangmaoup.resin.pura.resin.configuration.JmxConfigurationStrategy
import com.dingdangmaoup.resin.pura.resin.configuration.ResinConfigurationStrategy
import com.intellij.execution.ExecutionException
import com.intellij.javaee.appServers.appServerIntegrations.ApplicationServer
import com.intellij.openapi.diagnostic.Logger
import java.io.File

class ResinPersistentDataHelper(private val applicationServer: ApplicationServer?) {
    private var myInstallationCache: InstallationCache? = null

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
    @Synchronized
    fun getInstallationOrError(): ResinInstallation? {
        val persistentData = getPersistentData()
        if (persistentData == null) {
            myInstallationCache = null
            return null
        }

        val homePath = persistentData.RESIN_HOME
        val cached = myInstallationCache
        if (cached != null && cached.homePath == homePath && cached.hasRequiredDirectories()) {
            return cached.installation
        }

        // Cache only a validated installation. An invalid path may become valid later
        // without changing the persisted value, so failed creations must remain retryable.
        myInstallationCache = null
        val installation = ResinInstallation.create(homePath)
        myInstallationCache = InstallationCache(homePath, installation)
        return installation
    }

    fun getJmxStrategy(): JmxConfigurationStrategy? {
        val installation = getInstallation() ?: return null
        if (!installation.getVersion().allowJmx()) return null
        return ResinConfigurationStrategy.getForInstallation(installation) as? JmxConfigurationStrategy
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

    private data class InstallationCache(
        val homePath: String,
        val installation: ResinInstallation,
    ) {
        fun hasRequiredDirectories(): Boolean {
            val home = installation.getResinHome()
            return home.isDirectory && File(home, "bin").isDirectory && File(home, "lib").isDirectory
        }
    }
}
