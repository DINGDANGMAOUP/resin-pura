package com.dingdangmaoup.resin.pura.resin.version

import com.dingdangmaoup.resin.pura.ResinBundle
import com.intellij.javaee.oss.util.Version
import com.intellij.openapi.util.io.FileUtil
import java.io.File

abstract class ResinVersion(private val name: String, private val versionNumber: String) {
    override fun toString(): String = name

    fun getVersionNumber(): String = versionNumber

    fun getParsed(): Version = Version(versionNumber)

    override fun equals(other: Any?): Boolean {
        return other is ResinVersion && this.toString() == other.toString()
    }

    override fun hashCode(): Int = toString().hashCode()

    abstract fun isOfVersion(resinHome: File): Boolean

    abstract fun getStartupClass(): String?

    abstract fun allowXdebug(): Boolean

    abstract fun allowJmx(): Boolean

    companion object {
        @JvmField
        val VERSION_2_X: ResinVersion = object : ResinVersion(
            ResinBundle.message("resin.version.fallback.v2"),
            "2.x",
        ) {
            override fun isOfVersion(resinHome: File): Boolean {
                return File(resinHome, FileUtil.toSystemDependentName("lib/jsdk23.jar")).exists()
            }

            override fun getStartupClass(): String = "com.caucho.server.http.HttpServer"

            override fun allowXdebug(): Boolean = false

            override fun allowJmx(): Boolean = false
        }

        @JvmField
        val VERSION_3_X: ResinVersion = object : ResinVersion(
            ResinBundle.message("resin.version.fallback.v3"),
            "3.x",
        ) {
            override fun isOfVersion(resinHome: File): Boolean {
                return File(resinHome, FileUtil.toSystemDependentName("lib/jsdk-24.jar")).exists()
            }

            override fun getStartupClass(): String = "com.caucho.server.resin.Resin"

            override fun allowXdebug(): Boolean = true

            override fun allowJmx(): Boolean = true
        }

        @JvmField
        val UNKNOWN_VERSION: ResinVersion = object : ResinVersion(
            ResinBundle.message("resin.version.fallback.vUnknown"),
            "unknown",
        ) {
            override fun isOfVersion(resinHome: File): Boolean {
                return !VERSION_2_X.isOfVersion(resinHome) && !VERSION_3_X.isOfVersion(resinHome)
            }

            override fun getStartupClass(): String? = null

            override fun allowXdebug(): Boolean = false

            override fun allowJmx(): Boolean = false
        }
    }
}
