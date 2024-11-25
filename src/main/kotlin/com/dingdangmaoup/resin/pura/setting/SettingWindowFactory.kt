package com.dingdangmaoup.resin.pura.setting

import com.dingdangmaoup.resin.pura.PluginBundle
import com.dingdangmaoup.resin.pura.ui.Settings
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.ui.components.JBPanel
import java.awt.BorderLayout
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JTextField

class SettingWindowFactory : SearchableConfigurable {

    private var settings = Settings()
    override fun createComponent(): JComponent {
        val settingWindow = SettingWindow()
        return settingWindow.getContent()
    }

    override fun isModified(): Boolean {
        return true
    }

    override fun apply() {
        println("apply")
    }

    override fun getDisplayName(): String {
        return PluginBundle.message("setting.display.name")
    }

    override fun getId(): String {
        return "ResinSettingWindow"
    }

    class SettingWindow {
        private val content = JBPanel<JBPanel<*>>()

        init {
            content.layout=BorderLayout(0,20)
            content.border=BorderFactory.createEmptyBorder(20,20,20,20)
            content.add(JLabel(PluginBundle.message("login.username.label")),BorderLayout.PAGE_START)
            content.add(JTextField().apply {
                text = PluginBundle.message("login.username.input", "123")
            },BorderLayout.LINE_END)
        }

        fun getContent() = content
    }
}