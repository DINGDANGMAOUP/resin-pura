package com.dingdangmaoup.resin.pura.ui

import com.dingdangmaoup.resin.pura.ResinBundle
import com.dingdangmaoup.resin.pura.ResinModuleDeploymentModel
import com.intellij.javaee.appServers.deployment.DeploymentModel
import com.intellij.javaee.appServers.deployment.DeploymentSource
import com.intellij.javaee.appServers.run.configuration.CommonModel
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.SettingsEditor
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JTextField

class DeploymentSettingsEditor(
    commonModel: CommonModel,
    deploymentSource: DeploymentSource,
) : SettingsEditor<DeploymentModel>({ ResinModuleDeploymentModel(commonModel, deploymentSource) }) {
    private val myHostField = JTextField(28)
    private val myApplicationContextField = JTextField(28)
    private val myDefaultContextCheckBox = JCheckBox(ResinBundle.message("deployment.dlg.use.default.context.path"))
    private val myContextPathLabel = JLabel(ResinBundle.message("deployment.dlg.context.path"))
    private val myHostLabel = JLabel(ResinBundle.message("deployment.dlg.host"))
    private val myMainPanel = panel {
        row {
            cell(myDefaultContextCheckBox)
        }
        row {
            cell(myContextPathLabel)
            cell(myApplicationContextField).align(AlignX.FILL)
        }
        row {
            cell(myHostLabel)
            cell(myHostField).align(AlignX.FILL)
        }
    }

    init {
        myDefaultContextCheckBox.addActionListener { updateContextEnabled() }
        myDefaultContextCheckBox.isSelected = true
        updateContextEnabled()
    }

    private fun updateContextEnabled() {
        myApplicationContextField.isEnabled = !myDefaultContextCheckBox.isSelected
    }

    override fun resetEditorFrom(settings: DeploymentModel) {
        val resinDeploymentModel = settings as ResinModuleDeploymentModel
        myDefaultContextCheckBox.isSelected = resinDeploymentModel.isDefaultContextPath
        updateContextEnabled()
        myApplicationContextField.text = resinDeploymentModel.contextPath
        myHostField.text = resinDeploymentModel.host
    }

    @Throws(ConfigurationException::class)
    override fun applyEditorTo(settings: DeploymentModel) {
        val resinDeploymentModel = settings as ResinModuleDeploymentModel
        resinDeploymentModel.isDefaultContextPath = myDefaultContextCheckBox.isSelected
        resinDeploymentModel.contextPath = myApplicationContextField.text
        resinDeploymentModel.host = myHostField.text
    }

    override fun createEditor(): JComponent = myMainPanel
}
