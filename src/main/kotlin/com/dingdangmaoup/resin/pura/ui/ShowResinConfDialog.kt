package com.dingdangmaoup.resin.pura.ui

import com.dingdangmaoup.resin.pura.ResinBundle
import java.awt.Dimension
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JTextArea

class ShowResinConfDialog(text: String) : DialogWrapper(true) {
    private val textArea = JTextArea().apply {
        this.text = text
        isEditable = false
    }
    private val contentPanel = panel {
        row {
            cell(JBScrollPane(textArea)).align(AlignX.FILL)
        }.resizableRow()
    }

    init {
        title = ResinBundle.message("message.text.resin.conf.altered")
        init()
        contentPanel.preferredSize = Dimension(640, 480)
    }

    override fun createCenterPanel(): JComponent = contentPanel

    override fun createActions(): Array<Action> = arrayOf(okAction)
}
