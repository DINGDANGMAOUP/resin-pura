package com.dingdangmaoup.resin.pura.runConfiguration

import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import javax.swing.JComponent
import javax.swing.JPanel


class PluginSettingsEditor : SettingsEditor<PluginRunConfiguration>() {
    private val content: JPanel = JPanel()
    private val scriptPathField: TextFieldWithBrowseButton = TextFieldWithBrowseButton()
    override fun resetEditorFrom(configuration: PluginRunConfiguration) {
    }

    override fun applyEditorTo(configuration: PluginRunConfiguration) {
    }

    override fun createEditor(): JComponent {
        return content
    }

}