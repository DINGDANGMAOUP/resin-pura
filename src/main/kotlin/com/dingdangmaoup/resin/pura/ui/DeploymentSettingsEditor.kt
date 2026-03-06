package com.dingdangmaoup.resin.pura.ui

import com.dingdangmaoup.resin.pura.ResinModuleDeploymentModel
import com.intellij.javaee.appServers.deployment.DeploymentModel
import com.intellij.javaee.appServers.deployment.DeploymentSource
import com.intellij.javaee.appServers.run.configuration.CommonModel
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.SettingsEditor
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField

class DeploymentSettingsEditor(
    commonModel: CommonModel,
    deploymentSource: DeploymentSource,
) : SettingsEditor<DeploymentModel>({ ResinModuleDeploymentModel(commonModel, deploymentSource) }) {
    private val myMainPanel = JPanel(GridBagLayout())
    private val myHostField = JTextField(28)
    private val myApplicationContextField = JTextField(28)
    private val myDefaultContextCheckBox = JCheckBox("Use default context path")

    init {
        val c = GridBagConstraints().apply {
            insets = Insets(4, 6, 4, 6)
            fill = GridBagConstraints.HORIZONTAL
            weightx = 1.0
            gridx = 0
            gridy = 0
            gridwidth = 2
        }
        myMainPanel.add(myDefaultContextCheckBox, c)

        c.gridy = 1
        c.gridwidth = 1
        c.weightx = 0.0
        myMainPanel.add(JLabel("Context Path:"), c)
        c.gridx = 1
        c.weightx = 1.0
        myMainPanel.add(myApplicationContextField, c)

        c.gridx = 0
        c.gridy = 2
        c.weightx = 0.0
        myMainPanel.add(JLabel("Host:"), c)
        c.gridx = 1
        c.weightx = 1.0
        myMainPanel.add(myHostField, c)

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
