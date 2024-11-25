package com.dingdangmaoup.resin.pura.listeners

import com.intellij.openapi.application.ApplicationActivationListener
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.wm.IdeFrame

class PluginApplicationActivationListener : ApplicationActivationListener {
    override fun applicationActivated(ideFrame: IdeFrame) {
        super.applicationActivated(ideFrame)
        thisLogger().warn("ideFrame.project?.let { \" Current project: ${ideFrame.project}\" } ?: \"\"")
    }
}