package com.dingdangmaoup.resin.pura.resin.version

import com.dingdangmaoup.resin.pura.ResinBundle
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.io.FileUtil
import java.io.File
import java.io.IOException
import java.util.jar.JarFile

/**
 * Detects Resin capabilities from JAR metadata and bytecode structure without loading Resin classes into the IDE process.
 *
 * The historical name is retained because this object is used as the detector entry point, but no
 * class loading or reflective calls are performed here.
 */
object ClassCallDetector {
    private val LOG = Logger.getInstance(ClassCallDetector::class.java)
    private const val RESIN_VERSION_CLASS_NAME = "com/caucho/Version"
    private const val RESIN_VERSION_CLASS = "$RESIN_VERSION_CLASS_NAME.class"
    private const val RESIN_XDEBUG_CLASS = "com/caucho/log/LogManagerImpl.class"
    private const val RESIN_JMX_CLASS = "com/caucho/jmx/MBeanServerBuilderImpl.class"
    private const val RESIN_VERSION_FIELD = "VERSION"
    private const val IMPLEMENTATION_VERSION = "Implementation-Version"
    private const val MAX_VERSION_CLASS_BYTES = 1024 * 1024

    @JvmStatic
    fun getResinVersion(resinHome: File): ResinVersion? {
        val resinJar = File(resinHome, FileUtil.toSystemDependentName("lib/resin.jar"))
        if (!resinJar.isFile) {
            return null
        }

        return try {
            JarFile(resinJar).use { jar ->
                val version = getResinVersionFromClassFile(jar)
                    ?: getResinVersionFromManifest(jar)
                    ?: return null
                val startupClass = StartupClassFinder.getStartupClassForVersion(version) ?: return null

                GenericResinVersion(
                    ResinBundle.message("resin.version.prefix", version),
                    version,
                    startupClass,
                    hasClassEntry(jar, RESIN_XDEBUG_CLASS),
                    hasClassEntry(jar, RESIN_JMX_CLASS),
                )
            }
        } catch (e: IOException) {
            LOG.warn("Unable to inspect Resin JAR at ${resinJar.absolutePath}", e)
            null
        } catch (e: SecurityException) {
            LOG.warn("Unable to verify Resin JAR at ${resinJar.absolutePath}", e)
            null
        }
    }

    private fun getResinVersionFromManifest(jar: JarFile): String? {
        return jar.manifest
            ?.mainAttributes
            ?.getValue(IMPLEMENTATION_VERSION)
            ?.trim()
            ?.takeIf(String::isNotEmpty)
    }

    private fun getResinVersionFromClassFile(jar: JarFile): String? {
        val entry = jar.getJarEntry(RESIN_VERSION_CLASS) ?: return null
        if (entry.isDirectory || entry.size > MAX_VERSION_CLASS_BYTES) {
            return null
        }

        val classBytes = jar.getInputStream(entry).use { input ->
            input.readNBytes(MAX_VERSION_CLASS_BYTES + 1)
        }
        if (classBytes.size > MAX_VERSION_CLASS_BYTES) {
            return null
        }

        return ClassFileStringConstantReader.readStaticString(
            classBytes,
            RESIN_VERSION_CLASS_NAME,
            RESIN_VERSION_FIELD,
        )?.trim()?.takeIf(String::isNotEmpty)
    }

    private fun hasClassEntry(jar: JarFile, classEntry: String): Boolean = jar.getJarEntry(classEntry)?.isDirectory == false
}
