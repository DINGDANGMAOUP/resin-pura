package com.dingdangmaoup.resin.pura.resin.version

import java.io.File

object FallbackDetector {
    @JvmStatic
    fun getResinVersion(resinHome: File): ResinVersion {
        if (ResinVersion.VERSION_3_X.isOfVersion(resinHome)) {
            return ResinVersion.VERSION_3_X
        }
        if (ResinVersion.VERSION_2_X.isOfVersion(resinHome)) {
            return ResinVersion.VERSION_2_X
        }
        return ResinVersion.UNKNOWN_VERSION
    }
}
