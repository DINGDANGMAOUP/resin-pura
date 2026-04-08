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
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JTextField

class RemoteRunConfigurationEditor(private val myProject: Project) : ResinRunConfigurationEditorBase() {
    private val myJmxPortField = JTextField(10)
    private val myPingButton = JButton(ResinBundle.message("Form.RemoteRunConfigurationEditor.ping"))
    private val myCharsetField = JTextField(16)
    private val myJmxPortLabel = JLabel(ResinBundle.message("run.config.dlg.jmx.port"))
    private val myCharsetLabel = JLabel(ResinBundle.message("run.config.dlg.charset"))
    private val myRootPanel = panel {
        row {
            cell(myJmxPortLabel)
            cell(myJmxPortField).align(AlignX.FILL)
            cell(myPingButton)
        }
        row {
            cell(myCharsetLabel)
            cell(myCharsetField).align(AlignX.FILL)
        }
    }

    private lateinit var myServerModel: ResinRemoteModel
    private var myDeploymentTransportTarget: TransportTarget? = null

    init {
        myPingButton.addActionListener { onPingButton() }
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
