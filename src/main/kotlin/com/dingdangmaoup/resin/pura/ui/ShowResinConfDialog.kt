package com.dingdangmaoup.resin.pura.ui

import com.dingdangmaoup.resin.pura.ResinBundle
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JButton
import javax.swing.JDialog
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea

class ShowResinConfDialog(text: String) : JDialog() {
    private val contentPane = JPanel(BorderLayout(8, 8))
    private val buttonOK = JButton("OK")
    private val textArea = JTextArea()

    init {
        setContentPane(contentPane)
        isModal = true
        rootPane.defaultButton = buttonOK

        buttonOK.addActionListener { onOK() }

        title = ResinBundle.message("message.text.resin.conf.altered")
        textArea.text = text
        textArea.isEditable = false
        contentPane.add(JScrollPane(textArea), BorderLayout.CENTER)
        val buttonPanel = JPanel(BorderLayout())
        buttonPanel.add(buttonOK, BorderLayout.EAST)
        contentPane.add(buttonPanel, BorderLayout.SOUTH)
        contentPane.preferredSize = Dimension(640, 480)
    }

    private fun onOK() {
        dispose()
    }
}
