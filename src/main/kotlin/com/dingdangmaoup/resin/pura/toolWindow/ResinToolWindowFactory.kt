package com.dingdangmaoup.resin.pura.toolWindow

import com.dingdangmaoup.resin.pura.IconBundle
import com.dingdangmaoup.resin.pura.ui.Login
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import javax.swing.Icon

class ResinToolWindowFactory : ToolWindowFactory {
    private var login = Login()
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        thisLogger().warn("project: $project, toolWindow: $toolWindow")
        val content = ContentFactory.getInstance().createContent(login.loginContent, null, false)
        toolWindow.contentManager.addContent(content)
    }

    override val icon: Icon
        get() = IconBundle.ResinIcon

    override fun shouldBeAvailable(project: Project) = true

}