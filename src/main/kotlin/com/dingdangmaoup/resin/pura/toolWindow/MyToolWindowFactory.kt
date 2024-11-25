package com.github.dingdangmaoup.resin.pura.toolWindow

import com.github.dingdangmaoup.resin.pura.MyBundle
import com.github.dingdangmaoup.resin.pura.PluginBundle
import com.github.dingdangmaoup.resin.pura.services.MyProjectService
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.content.ContentFactory
import javax.swing.JButton


class MyToolWindowFactory : ToolWindowFactory {

    init {
        thisLogger().warn("Don't forget to remove all non-needed sample code files with their corresponding registration entries in `plugin.xml`.")
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val myToolWindow = MyToolWindow(toolWindow)
        val content = ContentFactory.getInstance().createContent(myToolWindow.getContent(), null, false)
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project) = true

    class MyToolWindow(toolWindow: ToolWindow) {

        private val service = toolWindow.project.service<MyProjectService>()

        fun getContent() = JBPanel<JBPanel<*>>().apply {
            val label = JBLabel(MyBundle.message("randomLabel", "?"))

            add(label)
            add(JButton(MyBundle.message("shuffle")).apply {
                addActionListener {
                    Notifications.Bus.notify(
                        Notification(
                            "rp_msg",
                            PluginBundle.message("resin.application.server.name"),
                            NotificationType.INFORMATION
                        )
                    )
                    label.text = MyBundle.message("randomLabel", service.getRandomNumber())
                }
            })
        }
    }
}
