package com.dingdangmaoup.resin.pura.ui

import com.dingdangmaoup.resin.pura.ResinBundle
import com.dingdangmaoup.resin.pura.ResinPersistentData
import com.dingdangmaoup.resin.pura.resin.ResinInstallation
import com.intellij.execution.ExecutionException
import com.intellij.icons.AllIcons
import com.intellij.javaee.appServers.appServerIntegrations.ApplicationServerPersistentDataEditor
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.ui.TextBrowseFolderListener
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import java.io.File
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.event.DocumentEvent

class SelectResinLocationEditor : ApplicationServerPersistentDataEditor<ResinPersistentData>() {
    private val resinHomeSelector = TextFieldWithBrowseButton()
    private val resinVersionLabel = JLabel()
    private val includeAllResinjarsCheckbox = JCheckBox(ResinBundle.message("location.dlg.resin.include.jars"))
    private val defaultResinConf = TextFieldWithBrowseButton()
    private val myErrorLabel = JLabel()
    private val resinHomeLabel = JLabel(ResinBundle.message("location.dlg.resin.home.text"))
    private val resinVersionTitleLabel = JLabel(ResinBundle.message("location.dlg.detected.version"))
    private val defaultResinConfLabel = JLabel(ResinBundle.message("location.dlg.default.resin.conf"))
    private val mainPanel = panel {
        row {
            cell(resinHomeLabel)
            cell(resinHomeSelector).align(AlignX.FILL)
        }
        row {
            cell(resinVersionTitleLabel)
            cell(resinVersionLabel).align(AlignX.FILL)
        }
        row {
            cell(includeAllResinjarsCheckbox)
        }
        row {
            cell(defaultResinConfLabel)
            cell(defaultResinConf).align(AlignX.FILL)
        }
        row {
            cell(myErrorLabel).align(AlignX.FILL)
        }
    }

    private var suggestConfPath = false
    private var myHasHomeError = false

    init {
        initChooser(
            resinHomeSelector,
            ResinBundle.message("message.text.locator.resin.home.title"),
            ResinBundle.message("message.text.locator.resin.home.summary"),
            chooseFiles = false,
            chooseDirs = true,
        )
        initChooser(
            defaultResinConf,
            ResinBundle.message("message.text.locator.resin.conf.title"),
            ResinBundle.message("message.text.locator.resin.conf.summary"),
            chooseFiles = true,
            chooseDirs = false,
        )

        resinHomeSelector.textField.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(event: DocumentEvent) {
                suggestConfPath = true
                update()
            }
        })

        defaultResinConf.textField.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(event: DocumentEvent) {
                suggestConfPath = false
                updateConfPath()
            }
        })

        includeAllResinjarsCheckbox.addChangeListener { update() }
        myErrorLabel.icon = AllIcons.General.BalloonError
        update()
    }

    private fun update() {
        val homePath = resinHomeSelector.text
        myErrorLabel.text = ""

        val installation = try {
            ResinInstallation.create(homePath)
        } catch (e: ExecutionException) {
            myErrorLabel.text = e.message
            myErrorLabel.isVisible = true
            myHasHomeError = true
            return
        }

        hideError()
        myHasHomeError = false

        if (!installation.isVersionDetected()) {
            resinVersionLabel.text = ResinBundle.message("location.dlg.detected.version.unknown")
        } else {
            resinVersionLabel.text = installation.getVersion().toString()
            if (suggestConfPath) {
                var resinConfDef = File(homePath, FileUtil.toSystemDependentName(RESIN_CONF_FILE))
                if (resinConfDef.exists()) {
                    defaultResinConf.text = resinConfDef.absoluteFile.absolutePath
                } else {
                    resinConfDef = File(homePath, FileUtil.toSystemDependentName(OLD_RESIN_CONF_FILE))
                    if (resinConfDef.exists()) {
                        defaultResinConf.text = resinConfDef.absoluteFile.absolutePath
                    }
                }
            }
        }
        updateConfPath()
    }

    private fun updateConfPath() {
        if (myHasHomeError) return
        val confFilePath = defaultResinConf.text
        val confFile = if (StringUtil.isEmpty(confFilePath)) null else File(confFilePath)
        if (confFile == null) {
            showError(ResinBundle.message("message.error.resin.conf.doesnt.chosen"))
            return
        }
        if (!confFile.exists()) {
            showError(ResinBundle.message("message.error.resin.conf.doesnt.exist", ""))
            return
        }
        if (confFile.isDirectory) {
            showError(ResinBundle.message("message.error.resin.conf.directory", confFile.absolutePath))
            return
        }
        hideError()
    }

    private fun showError(errorMsg: String) {
        myErrorLabel.text = errorMsg
        myErrorLabel.isVisible = true
    }

    private fun hideError() {
        myErrorLabel.isVisible = false
    }

    override fun resetEditorFrom(resinPersistentData: ResinPersistentData) {
        suggestConfPath = resinPersistentData.RESIN_HOME.isEmpty()
        resinHomeSelector.text = resinPersistentData.RESIN_HOME
        includeAllResinjarsCheckbox.isSelected = resinPersistentData.INCLUDE_ALL_JARS
        defaultResinConf.text = resinPersistentData.RESIN_CONF
        update()
    }

    override fun applyEditorTo(data: ResinPersistentData) {
        data.RESIN_HOME = resinHomeSelector.text
        data.RESIN_CONF = defaultResinConf.text
        data.INCLUDE_ALL_JARS = includeAllResinjarsCheckbox.isSelected
    }

    override fun createEditor(): JComponent = mainPanel

    companion object {
        private const val RESIN_CONF_FILE = "conf/resin.xml"
        private const val OLD_RESIN_CONF_FILE = "conf/resin.conf"

        private fun initChooser(
            field: TextFieldWithBrowseButton,
            title: String,
            description: String,
            chooseFiles: Boolean,
            chooseDirs: Boolean,
        ) {
            field.text = ""
            field.textField.isEditable = true
            val descriptor = FileChooserDescriptor(chooseFiles, chooseDirs, false, false, false, false)
                .withTitle(title)
                .withDescription(description)
            field.addBrowseFolderListener(TextBrowseFolderListener(descriptor, null))
        }
    }
}
