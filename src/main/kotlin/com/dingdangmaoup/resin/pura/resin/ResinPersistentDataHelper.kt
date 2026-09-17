package com.dingdangmaoup.resin.pura.resin

import com.dingdangmaoup.resin.pura.ResinPersistentData
import com.dingdangmaoup.resin.pura.resin.configuration.JmxConfigurationStrategy
import com.dingdangmaoup.resin.pura.resin.jmx.JmxDeploymentClient
import com.intellij.execution.ExecutionException
import com.intellij.javaee.appServers.appServerIntegrations.ApplicationServer
import com.intellij.openapi.diagnostic.Logger
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import java.io.IOException

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
        if (cached != null && cached.homePath == homePath && cached.hasRequiredDirectories() &&
            cached.fingerprint == installationFingerprint(File(homePath))) {
            return cached.installation
        }

        // Cache only a validated installation. An invalid path may become valid later
        // without changing the persisted value, so failed creations must remain retryable.
        myInstallationCache = null
        val installation = ResinInstallation.create(homePath)
        myInstallationCache = InstallationCache(homePath, installation, installationFingerprint(File(homePath)))
        return installation
    }

    fun getJmxStrategy(): JmxConfigurationStrategy? {
        val installation = getInstallation() ?: return null
        if (!installation.getVersion().allowJmx()) return null
        return JmxDeploymentClient(installation)
    }

    fun hasJmxStrategy(): Boolean {
        return getJmxStrategy() != null
    }

    companion object {
        private val LOG = Logger.getInstance(ResinPersistentDataHelper::class.java)

        private fun installationFingerprint(home: File): List<String?> =
            listOf("resin.jar", "jsdk23.jar", "jsdk-24.jar").map { name ->
                try {
                    val attributes = Files.readAttributes(File(home, "lib/$name").toPath(), BasicFileAttributes::class.java)
                    "${attributes.fileKey()}:${attributes.size()}:${attributes.lastModifiedTime()}"
                } catch (_: IOException) {
                    null
                }
            }
    }

    private data class InstallationCache(
        val homePath: String,
        val installation: ResinInstallation,
        val fingerprint: List<String?>,
    ) {
        fun hasRequiredDirectories(): Boolean {
            val home = installation.getResinHome()
            return home.isDirectory && File(home, "bin").isDirectory && File(home, "lib").isDirectory
        }
    }
}
