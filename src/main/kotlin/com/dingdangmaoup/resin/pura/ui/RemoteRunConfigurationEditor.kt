package com.dingdangmaoup.resin.pura.ui

import com.dingdangmaoup.resin.pura.ResinBundle
import com.dingdangmaoup.resin.pura.ResinRemoteModel
import com.dingdangmaoup.resin.pura.resin.jmx.ConnectorPingCommand
import com.dingdangmaoup.resin.pura.resin.jmx.JmxCredentialEdit
import com.dingdangmaoup.resin.pura.resin.jmx.JmxCredentialSource
import com.dingdangmaoup.resin.pura.resin.jmx.JmxCredentials
import com.dingdangmaoup.resin.pura.resin.jmx.JmxEndpoint
import com.intellij.javaee.appServers.run.configuration.CommonModel
import com.intellij.javaee.transport.TransportManager
import com.intellij.javaee.transport.TransportManagerConfigurable
import com.intellij.javaee.transport.TransportManagerConfigurableListener
import com.intellij.javaee.transport.TransportTarget
import com.intellij.javaee.transport.TransportTargetConfigurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.UIUtil
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPasswordField
import javax.swing.JTextField

class RemoteRunConfigurationEditor(private val myProject: Project) : ResinRunConfigurationEditorBase() {
    private val myJmxPortField = JTextField(10)
    private val myJmxUsernameField = JTextField(16)
    private val myJmxPasswordField = JPasswordField(16)
    private val myClearJmxCredentials = JCheckBox(ResinBundle.message("run.config.dlg.jmx.credentials.clear"))
    private val myPingButton = JButton(ResinBundle.message("Form.RemoteRunConfigurationEditor.ping"))
    private val myCharsetField = JTextField(16)
    private val myJmxPortLabel = JLabel(ResinBundle.message("run.config.dlg.jmx.port"))
    private val myJmxUsernameLabel = JLabel(ResinBundle.message("run.config.dlg.jmx.username"))
    private val myJmxPasswordLabel = JLabel(ResinBundle.message("run.config.dlg.jmx.password"))
    private val myJmxCredentialsHint = JLabel(ResinBundle.message("run.config.dlg.jmx.credentials.hint"))
    private val myCharsetLabel = JLabel(ResinBundle.message("run.config.dlg.charset"))
    private val myTransportTargetLabel = JLabel(ResinBundle.message("Form.RemoteRunConfigurationEditor.webapps.target"))
    private val myTransportHintLabel = JLabel(ResinBundle.message("Form.RemoteRunConfigurationEditor.select.host.hint"))
    private val myTransportManagerConfigurable = TransportManagerConfigurable()
    private val myTransportTargetConfigurable = TransportTargetConfigurable()
    private val myRootPanel = panel {
        row {
            cell(myJmxPortLabel)
            cell(myJmxPortField).align(AlignX.FILL)
            cell(myPingButton)
        }
        row {
            cell(myJmxUsernameLabel)
            cell(myJmxUsernameField).align(AlignX.FILL)
        }
        row {
            cell(myJmxPasswordLabel)
            cell(myJmxPasswordField).align(AlignX.FILL)
        }
        row {
            cell(myJmxCredentialsHint).align(AlignX.FILL)
            cell(myClearJmxCredentials)
        }
        row {
            cell(myCharsetLabel)
            cell(myCharsetField).align(AlignX.FILL)
        }
        group(ResinBundle.message("Form.RemoteRunConfigurationEditor.remote.staging")) {
            row {
                cell(myTransportManagerConfigurable.mainPanel).align(AlignX.FILL)
            }
            row {
                cell(myTransportTargetLabel)
                cell(myTransportTargetConfigurable.mainPanel).align(AlignX.FILL)
            }
            row {
                cell(myTransportHintLabel).align(AlignX.FILL)
            }
        }
    }

    private lateinit var myServerModel: ResinRemoteModel
    private var myDeploymentTransportTarget: TransportTarget? = null
    private var myOriginalJmxEndpoint: JmxEndpoint? = null

    init {
        myTransportTargetConfigurable.setParentConfigurable(myTransportManagerConfigurable)
        myTransportManagerConfigurable.addListener(object : TransportManagerConfigurableListener {
            override fun hostSelectionChanged() {
                updateTransportUiState()
            }
        })
        myPingButton.addActionListener { onPingButton() }
        updateTransportUiState()
    }

    private fun getProject(): Project = myProject

    private fun onPingButton() {
        val endpoint: JmxEndpoint
        val credentialSource: JmxCredentialSource
        try {
            endpoint = currentEndpoint(myServerModel.getCommonModel().host)
            credentialSource = when (val edit = readCredentialEdit()) {
                JmxCredentialEdit.Keep -> JmxCredentialSource.Stored
                JmxCredentialEdit.Clear -> JmxCredentialSource.Anonymous
                is JmxCredentialEdit.Replace -> JmxCredentialSource.Draft(edit.credentials)
            }
        } catch (e: ConfigurationException) {
            Messages.showErrorDialog(e.localizedMessage, ResinBundle.message("RemoteRunConfigurationEditor.message.ping"))
            return
        }

        var success: Boolean? = null
        val completed = ProgressManager.getInstance().runProcessWithProgressSynchronously(
            {
                try {
                    val pingResult = ConnectorPingCommand(myServerModel, endpoint, credentialSource).execute()
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
        clearCredentialInputs()
        myOriginalJmxEndpoint = runCatching {
            JmxEndpoint.of(s.host, myServerModel.jmxPort)
        }.getOrNull()
        myTransportManagerConfigurable.setHostId(myServerModel.getTransportHostId(), getProject())
        myDeploymentTransportTarget = getOrCreateTransportTarget(myServerModel.getTransportTargetWebApps())
        myTransportTargetConfigurable.setTarget(myDeploymentTransportTarget)
        updateTransportUiState()
        updateJmxPortVisible(myServerModel)
    }

    @Throws(ConfigurationException::class)
    override fun applyEditorTo(s: CommonModel) {
        val serverModel = s.serverModel as ResinRemoteModel
        val targetEndpoint = currentEndpoint(s.host)
        val credentialEdit = readCredentialEdit()
        val targetCharset = RunConfigurationEditor.parseCharset(myCharsetField.text)

        runPersistentSideEffect(myServerModel, serverModel) {
            if (credentialEdit != JmxCredentialEdit.Keep) {
                applyCredentialEdit(
                    serverModel,
                    myOriginalJmxEndpoint ?: targetEndpoint,
                    targetEndpoint,
                    credentialEdit,
                )
                clearCredentialInputs()
            }
            myOriginalJmxEndpoint = targetEndpoint
        }
        serverModel.jmxPort = targetEndpoint.port
        serverModel.charset = targetCharset
        serverModel.setTransportHostId(myTransportManagerConfigurable.hostId)
        serverModel.setTransportTargetWebApps(myDeploymentTransportTarget)
        runPersistentSideEffect(myServerModel, serverModel) {
            myTransportTargetConfigurable.saveState()
        }
    }

    override fun createEditor(): JComponent = myRootPanel

    override fun disposeEditor() {
        clearCredentialInputs()
        super.disposeEditor()
    }

    override fun setJmxPortVisible(visible: Boolean) {
        myJmxPortLabel.isVisible = visible
        myJmxPortField.isVisible = visible
        myPingButton.isVisible = visible
        myJmxUsernameLabel.isVisible = visible
        myJmxUsernameField.isVisible = visible
        myJmxPasswordLabel.isVisible = visible
        myJmxPasswordField.isVisible = visible
        myJmxCredentialsHint.isVisible = visible
        myClearJmxCredentials.isVisible = visible
    }

    @Throws(ConfigurationException::class)
    private fun readCredentialEdit(): JmxCredentialEdit {
        val username = myJmxUsernameField.text.trim()
        val passwordChars = myJmxPasswordField.password
        return try {
            parseCredentialEdit(username, String(passwordChars), myClearJmxCredentials.isSelected)
        } finally {
            passwordChars.fill('\u0000')
        }
    }

    @Throws(ConfigurationException::class)
    private fun applyCredentialEdit(
        serverModel: ResinRemoteModel,
        originalEndpoint: JmxEndpoint,
        targetEndpoint: JmxEndpoint,
        edit: JmxCredentialEdit,
    ) {
        var failure: Exception? = null
        val completed = ProgressManager.getInstance().runProcessWithProgressSynchronously(
            {
                try {
                    serverModel.applyJmxCredentialEdit(originalEndpoint, targetEndpoint, edit)
                } catch (e: Exception) {
                    failure = e
                }
            },
            ResinBundle.message("run.config.dlg.jmx.credentials.operation"),
            false,
            getProject(),
        )
        if (!completed || failure != null) {
            throw ConfigurationException(ResinBundle.message("run.config.dlg.jmx.credentials.save.error"))
        }
    }

    private fun updateTransportUiState() {
        val hasHost = myTransportManagerConfigurable.host != null
        myTransportTargetLabel.isEnabled = hasHost
        UIUtil.setEnabled(myTransportTargetConfigurable.mainPanel, hasHost, true)
        myTransportHintLabel.isVisible = !hasHost
    }

    companion object {
        internal fun isPersistentApply(editorModel: ResinRemoteModel, targetModel: ResinRemoteModel): Boolean =
            editorModel === targetModel

        internal fun runPersistentSideEffect(
            editorModel: ResinRemoteModel,
            targetModel: ResinRemoteModel,
            sideEffect: () -> Unit,
        ) {
            if (isPersistentApply(editorModel, targetModel)) {
                sideEffect()
            }
        }

        @Throws(ConfigurationException::class)
        internal fun parseCredentialEdit(
            username: String,
            password: String,
            clearSelected: Boolean,
        ): JmxCredentialEdit {
            val normalizedUsername = username.trim()
            val credentials = when {
                normalizedUsername.isEmpty() && password.isEmpty() -> null
                normalizedUsername.isEmpty() || password.isEmpty() -> {
                    throw ConfigurationException(ResinBundle.message("run.config.dlg.jmx.credentials.incomplete"))
                }

                else -> JmxCredentials(normalizedUsername, password)
            }
            if (clearSelected && credentials != null) {
                throw ConfigurationException(ResinBundle.message("run.config.dlg.jmx.credentials.clear.conflict"))
            }
            return when {
                clearSelected -> JmxCredentialEdit.Clear
                credentials != null -> JmxCredentialEdit.Replace(credentials)
                else -> JmxCredentialEdit.Keep
            }
        }

        private fun getOrCreateTransportTarget(target: TransportTarget?): TransportTarget {
            return if (target == null || target.id == null) TransportManager.createTarget() else target
        }
    }

    @Throws(ConfigurationException::class)
    private fun currentEndpoint(host: String): JmxEndpoint {
        return try {
            JmxEndpoint.of(host, parseJmxPort())
        } catch (_: IllegalArgumentException) {
            throw ConfigurationException(ResinBundle.message("run.config.dlg.jmx.credentials.endpoint.invalid"))
        }
    }

    private fun clearCredentialInputs() {
        myJmxUsernameField.text = ""
        myJmxPasswordField.text = ""
        myClearJmxCredentials.isSelected = false
    }
}
