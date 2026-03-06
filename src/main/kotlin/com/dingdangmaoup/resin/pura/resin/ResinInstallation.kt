package com.dingdangmaoup.resin.pura.resin

import com.dingdangmaoup.resin.pura.ResinBundle
import com.dingdangmaoup.resin.pura.resin.version.ResinLibCollector
import com.dingdangmaoup.resin.pura.resin.version.ResinVersion
import com.dingdangmaoup.resin.pura.resin.version.ResinVersionDetector
import com.intellij.execution.ExecutionException
import com.intellij.openapi.util.io.FileUtil
import java.io.File

class ResinInstallation private constructor(private val myHome: File, private val myLib: File) {
    fun getVersion(): ResinVersion = ResinVersionDetector.getResinVersion(myHome) ?: ResinVersion.UNKNOWN_VERSION

    fun isVersionDetected(): Boolean {
        val ver = getVersion()
        return ver != ResinVersion.UNKNOWN_VERSION
    }

    fun getResinHome(): File = myHome

    fun getDisplayName(): String = getVersion().toString()

    fun getLibFiles(all: Boolean): Array<File> = ResinLibCollector.getLibFiles(myLib, all)

    companion object {
        @JvmStatic
        @Throws(ExecutionException::class)
        fun create(homePath: String): ResinInstallation {
            val home = File(FileUtil.toSystemDependentName(homePath))
            if (!isExistingDir(home)) {
                throw ExecutionException(ResinBundle.message("message.error.resin.home.doesnt.exist"))
            }
            val bin = File(home, "bin")
            if (!isExistingDir(bin)) {
                throw ExecutionException(ResinBundle.message("message.error.resin.bin.doesnt.exist"))
            }
            val lib = File(home, "lib")
            if (!isExistingDir(lib)) {
                throw ExecutionException(ResinBundle.message("message.error.resin.lib.doesnt.exist"))
            }
            return ResinInstallation(home, lib)
        }

        private fun isExistingDir(dirCandidate: File): Boolean = dirCandidate.exists() && dirCandidate.isDirectory
    }
}
