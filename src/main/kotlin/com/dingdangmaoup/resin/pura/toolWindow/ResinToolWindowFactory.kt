package com.github.dingdangmaoup.resin.pura.toolWindow

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBPanel

class ResinToolWindowFactory:ToolWindowFactory{
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
      thisLogger().warn("project: $project, toolWindow: $toolWindow")
    }

    override fun shouldBeAvailable(project: Project)=true

    class ResinToolWindow(toolWindow: ToolWindow){
        fun getContent()=JBPanel<JBPanel<*>>().apply{
            
        }
    }
}