package com.dingdangmaoup.resin.pura.resin.version

import com.dingdangmaoup.resin.pura.ResinBundle
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.io.FileUtil
import java.io.File
import java.io.IOException
import java.lang.reflect.Field
import java.util.jar.Attributes
import java.util.jar.JarFile

object ClassCallDetector {
    private val LOG = Logger.getInstance(ClassCallDetector::class.java)
    private const val RESIN_VERSION_CLASS = "com.caucho.Version"
    private const val RESIN_XDEBUG_CLASS = "com.caucho.log.LogManagerImpl"
    private const val RESIN_JMX_CLASS = "com.caucho.jmx.MBeanServerBuilderImpl"
    private const val RESIN_VERSION_CLASS_ATT_NAME = "VERSION"

    @JvmStatic
    fun getResinVersion(resinHome: File): ResinVersion? {
        return try {
            val resinJar = File(resinHome, FileUtil.toSystemDependentName("lib/resin.jar"))
            if (!resinJar.exists()) {
                return null
            }

            val loader = JarClassLoader(resinJar.absolutePath)
            var version = getResinVersionFromClass(loader)
            if (version == null) {
                version = getResinVersionFromManifest(resinJar)
            }
            if (version == null) {
                return null
            }
            val startupClass = StartupClassFinder.getStartupClassForVersion(version)
            val allowDebug = hasClass(loader, RESIN_XDEBUG_CLASS)
            val allowJmx = hasClass(loader, RESIN_JMX_CLASS)

            if (startupClass == null) {
                null
            } else {
                GenericResinVersion(
                    ResinBundle.message("resin.version.prefix", version),
                    version,
                    startupClass,
                    allowDebug,
                    allowJmx,
                )
            }
        } catch (e: IOException) {
            LOG.error(e)
            null
        }
    }

    private fun getResinVersionFromClass(loader: JarClassLoader): String? {
        return try {
            val versionClass = loader.loadClass(RESIN_VERSION_CLASS)
            val field: Field = versionClass.getDeclaredField(RESIN_VERSION_CLASS_ATT_NAME)
            field[null].toString()
        } catch (_: ClassNotFoundException) {
            null
        } catch (e: IllegalAccessException) {
            LOG.error(e)
            null
        } catch (e: NoSuchFieldException) {
            LOG.error(e)
            null
        }
    }

    @Throws(IOException::class)
    private fun getResinVersionFromManifest(jarFile: File): String? {
        JarFile(jarFile).use { jar ->
            val attributes: Attributes? = jar.manifest.mainAttributes
            return attributes?.getValue("Implementation-Version")
        }
    }

    private fun hasClass(loader: JarClassLoader, className: String): Boolean {
        return try {
            loader.loadClass(className)
            true
        } catch (_: ClassNotFoundException) {
            false
        }
    }
}
