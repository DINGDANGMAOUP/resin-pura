package com.dingdangmaoup.resin.pura

import com.intellij.javaee.directoryManager.JavaeeSystemBaseDirectoryManagerProvider
import com.intellij.javaee.directoryManager.SystemBaseDirectoryManager

object ResinSystemBaseDirectoryManager {
    @JvmStatic
    fun getInstance(): SystemBaseDirectoryManager {
        return JavaeeSystemBaseDirectoryManagerProvider.getManagerInstance("resin")
    }
}
