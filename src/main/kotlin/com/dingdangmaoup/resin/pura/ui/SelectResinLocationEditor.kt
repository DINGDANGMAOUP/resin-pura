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
import com.intellij.util.concurrency.AppExecutorUtil
import javax.swing.SwingUtilities
import javax.swing.Timer
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
    private var resetting = false
    private val detector = LatestRequestRunner<LocationRequest, LocationResult>(
        AppExecutorUtil.getAppExecutorService(),
        { action -> SwingUtilities.invokeLater(action) },
        ::inspectLocation,
        ::showResult,
    )
    private val detectionTimer = Timer(250) {
        detector.submit(LocationRequest(resinHomeSelector.text, defaultResinConf.text, suggestConfPath))
    }.apply { isRepeats = false }

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
                if (!resetting) {
                    suggestConfPath = true
                    scheduleDetection()
                }
            }
        })

        defaultResinConf.textField.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(event: DocumentEvent) {
                if (!resetting) {
                    suggestConfPath = false
                    scheduleDetection()
                }
            }
        })

        myErrorLabel.icon = AllIcons.General.BalloonError
        scheduleDetection()
    }

    private fun scheduleDetection() {
        detector.invalidate()
        resinVersionLabel.text = ResinBundle.message("location.dlg.detecting")
        myErrorLabel.isVisible = false
        detectionTimer.restart()
    }

    private fun showResult(result: LocationResult) {
        resinVersionLabel.text = result.version
        resetting = true
        try {
            defaultResinConf.text = result.configurationPath
        } finally {
            resetting = false
        }
        myErrorLabel.text = result.error ?: ""
        myErrorLabel.isVisible = result.error != null
    }

    override fun resetEditorFrom(resinPersistentData: ResinPersistentData) {
        resetting = true
        try {
            suggestConfPath = resinPersistentData.RESIN_HOME.isEmpty()
            resinHomeSelector.text = resinPersistentData.RESIN_HOME
            includeAllResinjarsCheckbox.isSelected = resinPersistentData.INCLUDE_ALL_JARS
            defaultResinConf.text = resinPersistentData.RESIN_CONF
        } finally {
            resetting = false
        }
        scheduleDetection()
    }

    override fun disposeEditor() {
        detectionTimer.stop()
        detector.close()
        super.disposeEditor()
    }

    override fun applyEditorTo(data: ResinPersistentData) {
        data.RESIN_HOME = resinHomeSelector.text
        data.RESIN_CONF = defaultResinConf.text
        data.INCLUDE_ALL_JARS = includeAllResinjarsCheckbox.isSelected
    }

    override fun createEditor(): JComponent = mainPanel

    private data class LocationRequest(val home: String, val configuration: String, val suggest: Boolean)
    private data class LocationResult(val version: String, val configurationPath: String, val error: String?)

    companion object {
        private fun inspectLocation(request: LocationRequest): LocationResult {
            val installation = try {
                ResinInstallation.create(request.home)
            } catch (error: ExecutionException) {
                return LocationResult("", request.configuration, error.message)
            }
            val detected = installation.isVersionDetected()
            val version = if (detected) installation.getVersion().toString()
                else ResinBundle.message("location.dlg.detected.version.unknown")
            val suggestion = if (request.suggest && detected) {
                listOf(RESIN_CONF_FILE, OLD_RESIN_CONF_FILE).map { File(request.home, it) }.firstOrNull { it.isFile }?.absolutePath
            } else null
            val configuration = suggestion ?: request.configuration
            val file = File(configuration)
            val error = when {
                configuration.isEmpty() -> ResinBundle.message("message.error.resin.conf.doesnt.chosen")
                !file.exists() -> ResinBundle.message("message.error.resin.conf.doesnt.exist", configuration)
                file.isDirectory -> ResinBundle.message("message.error.resin.conf.directory", configuration)
                else -> null
            }
            return LocationResult(version, configuration, error)
        }

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
