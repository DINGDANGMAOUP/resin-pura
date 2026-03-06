package com.dingdangmaoup.resin.pura.ui

import com.dingdangmaoup.resin.pura.ResinBundle
import com.dingdangmaoup.resin.pura.ResinModel
import com.dingdangmaoup.resin.pura.resin.common.ParseUtil
import com.intellij.javaee.appServers.run.configuration.CommonModel
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.ui.TextComponentAccessor
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.PanelWithAnchor
import com.intellij.ui.RawCommandLineEditor
import com.intellij.ui.components.JBLabel
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField
import org.jetbrains.annotations.PropertyKey

class RunConfigurationEditor : ResinRunConfigurationEditorBase(), PanelWithAnchor {
    private val mainPanel = JPanel(GridBagLayout())
    private val myHttpPortTextField = JTextField(10)
    private val debugConfiguration = JCheckBox("Debug Configuration")
    private val resinConfSelector = TextFieldWithBrowseButton()
    private val charset = JTextField(16)
    private val readOnlyConfiguration = JCheckBox("Read-only Configuration")
    private val additionalParameters = RawCommandLineEditor()
    private val autoBuildClasspath = JCheckBox("Auto Build Classpath")
    private val myDeployModeComboBox = JComboBox<String>()
    private val myJmxPortTextField = JTextField(10)
    private val myAdditionalResinCommandLineLabel = JBLabel("Additional Resin Command Line:")
    private val myJmxPortLabel = JLabel("JMX Port:")
    private var anchor: JComponent? = null

    init {
        initChooser(
            resinConfSelector,
            ResinBundle.message("message.text.settings.resin.conf.file.title"),
            ResinBundle.message("message.text.settings.resin.conf.file.select"),
        )
        myDeployModeComboBox.addItem(ResinModel.DEPLOY_MODE_AUTO)
        myDeployModeComboBox.addItem(ResinModel.DEPLOY_MODE_LAZY)
        myDeployModeComboBox.addItem(ResinModel.DEPLOY_MODE_MANUAL)
        buildUi()
        setAnchor(myAdditionalResinCommandLineLabel)
    }

    private fun buildUi() {
        val c = GridBagConstraints().apply {
            insets = Insets(4, 6, 4, 6)
            fill = GridBagConstraints.HORIZONTAL
            anchor = GridBagConstraints.WEST
            weightx = 1.0
            gridx = 0
            gridy = 0
            gridwidth = 2
        }

        mainPanel.add(readOnlyConfiguration, c)
        c.gridy++
        mainPanel.add(autoBuildClasspath, c)
        c.gridy++
        mainPanel.add(debugConfiguration, c)

        c.gridy++
        c.gridwidth = 1
        c.weightx = 0.0
        mainPanel.add(JLabel("HTTP Port:"), c)
        c.gridx = 1
        c.weightx = 1.0
        mainPanel.add(myHttpPortTextField, c)

        c.gridx = 0
        c.gridy++
        c.weightx = 0.0
        mainPanel.add(JLabel("Resin Conf:"), c)
        c.gridx = 1
        c.weightx = 1.0
        mainPanel.add(resinConfSelector, c)

        c.gridx = 0
        c.gridy++
        c.weightx = 0.0
        mainPanel.add(JLabel("Deploy Mode:"), c)
        c.gridx = 1
        c.weightx = 1.0
        mainPanel.add(myDeployModeComboBox, c)

        c.gridx = 0
        c.gridy++
        c.weightx = 0.0
        mainPanel.add(myJmxPortLabel, c)
        c.gridx = 1
        c.weightx = 1.0
        mainPanel.add(myJmxPortTextField, c)

        c.gridx = 0
        c.gridy++
        c.weightx = 0.0
        mainPanel.add(JLabel("Charset:"), c)
        c.gridx = 1
        c.weightx = 1.0
        mainPanel.add(charset, c)

        c.gridx = 0
        c.gridy++
        c.weightx = 0.0
        mainPanel.add(myAdditionalResinCommandLineLabel, c)
        c.gridx = 1
        c.weightx = 1.0
        mainPanel.add(additionalParameters, c)
    }

    override fun resetEditorFrom(commonModel: CommonModel) {
        val resinModel = commonModel.serverModel as ResinModel
        myHttpPortTextField.text = resinModel.localPort.toString()
        resinConfSelector.text = resinModel.getResinConf()
        debugConfiguration.isSelected = resinModel.isDebugConfiguration()
        readOnlyConfiguration.isSelected = resinModel.isReadOnlyConfiguration()
        autoBuildClasspath.isSelected = resinModel.isAutoBuildClassPath()
        charset.text = resinModel.charset
        additionalParameters.text = resinModel.getAdditionalParameters()
        myJmxPortTextField.text = resinModel.jmxPort.toString()
        myDeployModeComboBox.selectedItem = resinModel.getDeployMode()
        updateJmxPortVisible(resinModel)
    }

    @Throws(ConfigurationException::class)
    override fun applyEditorTo(commonModel: CommonModel) {
        val resinModel = commonModel.serverModel as ResinModel
        resinModel.port = parseInt(myHttpPortTextField, "run.config.dlg.http.port.error")
        resinModel.setResinConf(resinConfSelector.text)
        resinModel.setDebugConfiguration(debugConfiguration.isSelected)
        resinModel.setReadOnlyConfiguration(readOnlyConfiguration.isSelected)
        resinModel.setAutoBuildClassPath(autoBuildClasspath.isSelected)
        resinModel.charset = charset.text
        resinModel.setAdditionalParameters(additionalParameters.text)
        resinModel.jmxPort = parseInt(myJmxPortTextField, "run.config.dlg.jmx.port.error")
        resinModel.setDeployMode(myDeployModeComboBox.selectedItem as String?)
    }

    override fun setJmxPortVisible(visible: Boolean) {
        myJmxPortLabel.isVisible = visible
        myJmxPortTextField.isVisible = visible
    }

    override fun createEditor(): JComponent = mainPanel

    override fun getAnchor(): JComponent? = anchor

    override fun setAnchor(anchor: JComponent?) {
        this.anchor = anchor
        myAdditionalResinCommandLineLabel.anchor = anchor
    }

    companion object {
        @Suppress("DEPRECATION")
        private fun initChooser(field: TextFieldWithBrowseButton, title: String, description: String) {
            field.text = ""
            field.textField.isEditable = true
            field.addBrowseFolderListener(
                title,
                description,
                null,
                FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor(),
                TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT,
            )
        }

        @JvmStatic
        @Throws(ConfigurationException::class)
        fun parseInt(
            text: JTextField,
            @PropertyKey(resourceBundle = ResinBundle.BUNDLE) errorKey: String,
        ): Int {
            return object : ParseUtil() {
                override fun getErrorMessage(unparsableValue: String): String {
                    return ResinBundle.message(errorKey, unparsableValue)
                }
            }.parseInt(text)
        }
    }
}
