package com.dingdangmaoup.resin.pura.resin.version

import com.intellij.openapi.diagnostic.Logger
import java.io.IOException
import java.io.InputStream
import java.util.Properties

object StartupClassFinder {
    private const val MAPPING_PROP_FILE = "com/dingdangmaoup/resin/pura/resin/version/versionmapping.properties"
    private val versionMapping: Properties by lazy(LazyThreadSafetyMode.SYNCHRONIZED, ::loadVersionMapping)

    @JvmStatic
    fun getStartupClassForVersion(version: String): String? {
        var current: String? = version
        while (current != null) {
            val startup = versionMapping.getProperty(current)
            if (startup != null) {
                return startup
            }
            val posDot = current.lastIndexOf('.')
            current = if (posDot == -1) null else current.substring(0, posDot)
        }
        return null
    }

    private fun loadVersionMapping(): Properties {
        val mapping = Properties()
        try {
            val input: InputStream? = javaClass.classLoader.getResourceAsStream(MAPPING_PROP_FILE)
            if (input != null) {
                input.use(mapping::load)
            }
        } catch (e: IOException) {
            LOG.warn("Unable to load Resin startup-class mappings", e)
        }
        return mapping
    }

    private val LOG = Logger.getInstance(StartupClassFinder::class.java)
}
