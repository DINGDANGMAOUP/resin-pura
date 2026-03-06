package com.dingdangmaoup.resin.pura.resin.version

import java.io.File

object ResinVersionDetector {
    @JvmStatic
    fun getResinVersion(resinHome: File): ResinVersion? {
        var version = ClassCallDetector.getResinVersion(resinHome)
        if (version == null) {
            version = FallbackDetector.getResinVersion(resinHome)
        }
        return version
    }
}
