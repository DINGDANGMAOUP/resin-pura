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
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JTextField
import org.jetbrains.annotations.PropertyKey

class RunConfigurationEditor : ResinRunConfigurationEditorBase(), PanelWithAnchor {
    private val myHttpPortTextField = JTextField(10)
    private val debugConfiguration = JCheckBox(ResinBundle.message("run.config.dlg.debug.configuration"))
    private val resinConfSelector = TextFieldWithBrowseButton()
    private val charset = JTextField(16)
    private val readOnlyConfiguration = JCheckBox(ResinBundle.message("run.config.dlg.read.only.configuration"))
    private val additionalParameters = RawCommandLineEditor()
    private val autoBuildClasspath = JCheckBox(ResinBundle.message("run.config.dlg.auto.build.classpath"))
    private val myDeployModeComboBox = JComboBox<String>()
    private val myJmxPortTextField = JTextField(10)
    private val httpPortLabel = JLabel(ResinBundle.message("run.config.dlg.http.port"))
    private val resinConfLabel = JLabel(ResinBundle.message("run.config.dlg.resin.conf"))
    private val deployModeLabel = JLabel(ResinBundle.message("run.config.dlg.deploy.mode"))
    private val charsetLabel = JLabel(ResinBundle.message("run.config.dlg.charset"))
    private val myAdditionalResinCommandLineLabel = JBLabel(ResinBundle.message("run.config.dlg.additional.params"))
    private val myJmxPortLabel = JLabel(ResinBundle.message("run.config.dlg.jmx.port"))
    private var anchor: JComponent? = null
    private val mainPanel = panel {
        row {
            cell(readOnlyConfiguration)
        }
        row {
            cell(autoBuildClasspath)
        }
        row {
            cell(debugConfiguration)
        }
        row {
            cell(httpPortLabel)
            cell(myHttpPortTextField).align(AlignX.FILL)
        }
        row {
            cell(resinConfLabel)
            cell(resinConfSelector).align(AlignX.FILL)
        }
        row {
            cell(deployModeLabel)
            cell(myDeployModeComboBox).align(AlignX.FILL)
        }
        row {
            cell(myJmxPortLabel)
            cell(myJmxPortTextField).align(AlignX.FILL)
        }
        row {
            cell(charsetLabel)
            cell(charset).align(AlignX.FILL)
        }
        row {
            cell(myAdditionalResinCommandLineLabel)
            cell(additionalParameters).align(AlignX.FILL)
        }
    }

    init {
        initChooser(
            resinConfSelector,
            ResinBundle.message("message.text.settings.resin.conf.file.title"),
            ResinBundle.message("message.text.settings.resin.conf.file.select"),
        )
        myDeployModeComboBox.addItem(ResinModel.DEPLOY_MODE_AUTO)
        myDeployModeComboBox.addItem(ResinModel.DEPLOY_MODE_LAZY)
        myDeployModeComboBox.addItem(ResinModel.DEPLOY_MODE_MANUAL)
        setAnchor(myAdditionalResinCommandLineLabel)
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
