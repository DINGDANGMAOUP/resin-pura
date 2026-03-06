package com.dingdangmaoup.resin.pura.ui

import com.dingdangmaoup.resin.pura.ResinBundle
import com.dingdangmaoup.resin.pura.ResinRemoteModel
import com.dingdangmaoup.resin.pura.resin.jmx.ConnectorPingCommand
import com.intellij.javaee.appServers.run.configuration.CommonModel
import com.intellij.javaee.transport.TransportManager
import com.intellij.javaee.transport.TransportTarget
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField

class RemoteRunConfigurationEditor(private val myProject: Project) : ResinRunConfigurationEditorBase() {
    private val myRootPanel = JPanel(GridBagLayout())
    private val myJmxPortField = JTextField(10)
    private val myPingButton = JButton("Ping")
    private val myCharsetField = JTextField(16)
    private val myJmxPortLabel = JLabel("JMX Port:")

    private lateinit var myServerModel: ResinRemoteModel
    private var myDeploymentTransportTarget: TransportTarget? = null

    init {
        buildUi()
        myPingButton.addActionListener { onPingButton() }
    }

    private fun buildUi() {
        val c = GridBagConstraints().apply {
            insets = Insets(4, 6, 4, 6)
            fill = GridBagConstraints.HORIZONTAL
            anchor = GridBagConstraints.WEST
            weightx = 1.0
            gridx = 0
            gridy = 0
        }

        myRootPanel.add(myJmxPortLabel, c)
        c.gridx = 1
        myRootPanel.add(myJmxPortField, c)
        c.gridx = 2
        c.weightx = 0.0
        myRootPanel.add(myPingButton, c)

        c.gridx = 0
        c.gridy = 1
        c.weightx = 0.0
        myRootPanel.add(JLabel("Charset:"), c)
        c.gridx = 1
        c.gridwidth = 2
        c.weightx = 1.0
        myRootPanel.add(myCharsetField, c)
    }

    private fun getProject(): Project = myProject

    private fun onPingButton() {
        val jmxPort = try {
            parseJmxPort()
        } catch (_: ConfigurationException) {
            return
        }

        var success: Boolean? = null
        val completed = ProgressManager.getInstance().runProcessWithProgressSynchronously(
            {
                try {
                    val pingResult = ConnectorPingCommand(myServerModel, jmxPort).execute()
                    success = pingResult != null && pingResult
                } catch (_: Exception) {
                    success = false
                }
            },
            ResinBundle.message("RemoteRunConfigurationEditor.message.ping.operation-name"),
            true,
            getProject(),
        )
        if (!completed || success == null) return

        val ping = ResinBundle.message("RemoteRunConfigurationEditor.message.ping")
        if (success == true) {
            Messages.showInfoMessage(ResinBundle.message("RemoteRunConfigurationEditor.message.ping.ok"), ping)
        } else {
            Messages.showErrorDialog(ResinBundle.message("RemoteRunConfigurationEditor.message.ping.failed"), ping)
        }
    }

    @Throws(ConfigurationException::class)
    private fun parseJmxPort(): Int = RunConfigurationEditor.parseInt(myJmxPortField, "run.config.dlg.jmx.port.error")

    override fun resetEditorFrom(s: CommonModel) {
        myServerModel = s.serverModel as ResinRemoteModel
        myCharsetField.text = myServerModel.charset
        myJmxPortField.text = myServerModel.jmxPort.toString()
        myDeploymentTransportTarget = getOrCreateTransportTarget(myServerModel.getTransportTargetWebApps())
        updateJmxPortVisible(myServerModel)
    }

    @Throws(ConfigurationException::class)
    override fun applyEditorTo(s: CommonModel) {
        val serverModel = s.serverModel as ResinRemoteModel
        serverModel.jmxPort = parseJmxPort()
        serverModel.charset = myCharsetField.text
        serverModel.setTransportTargetWebApps(myDeploymentTransportTarget)
    }

    override fun createEditor(): JComponent = myRootPanel

    override fun setJmxPortVisible(visible: Boolean) {
        myJmxPortLabel.isVisible = visible
        myJmxPortField.isVisible = visible
        myPingButton.isVisible = visible
    }

    companion object {
        private fun getOrCreateTransportTarget(target: TransportTarget?): TransportTarget {
            return if (target == null || target.id == null) TransportManager.createTarget() else target
        }
    }
}
