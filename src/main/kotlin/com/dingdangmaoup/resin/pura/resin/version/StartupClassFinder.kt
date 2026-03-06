package com.dingdangmaoup.resin.pura.resin.version

import java.io.IOException
import java.io.InputStream
import java.util.Properties

object StartupClassFinder {
    private const val MAPPING_PROP_FILE = "com/dingdangmaoup/resin/pura/resin/version/versionmapping.properties"
    private var versionMapping: Properties? = null

    @JvmStatic
    fun getStartupClassForVersion(version: String): String? {
        if (versionMapping == null) {
            loadVersionMapping()
        }

        var current: String? = version
        while (current != null) {
            val startup = versionMapping!!.getProperty(current)
            if (startup != null) {
                return startup
            }
            val posDot = current.lastIndexOf('.')
            current = if (posDot == -1) null else current.substring(0, posDot)
        }
        return null
    }

    private fun loadVersionMapping() {
        versionMapping = Properties()
        try {
            val input: InputStream? = javaClass.classLoader.getResourceAsStream(MAPPING_PROP_FILE)
            if (input != null) {
                input.use { versionMapping!!.load(it) }
            }
        } catch (ioe: IOException) {
            ioe.printStackTrace()
        }
    }
}
