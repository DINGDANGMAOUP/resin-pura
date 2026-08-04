package com.dingdangmaoup.resin.pura

import com.dingdangmaoup.resin.pura.resin.ResinInstallation
import com.dingdangmaoup.resin.pura.resin.ResinPersistentDataHelper
import com.dingdangmaoup.resin.pura.resin.configuration.JmxConfigurationStrategy
import com.dingdangmaoup.resin.pura.resin.jmx.JmxCredentials
import com.intellij.configurationStore.serializeObjectInto
import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.execution.configurations.RuntimeConfigurationException
import com.intellij.execution.process.ProcessHandler
import com.intellij.javaee.appServers.appServerIntegrations.ApplicationServer
import com.intellij.javaee.appServers.appServerIntegrations.ApplicationServerUrlMapping
import com.intellij.javaee.appServers.deployment.DeploymentModel
import com.intellij.javaee.appServers.deployment.DeploymentProvider
import com.intellij.javaee.appServers.deployment.DeploymentSource
import com.intellij.javaee.appServers.run.configuration.CommonModel
import com.intellij.javaee.appServers.run.configuration.ServerModel
import com.intellij.javaee.appServers.run.execution.DefaultOutputProcessor
import com.intellij.javaee.appServers.run.execution.OutputProcessor
import com.intellij.javaee.appServers.serverInstances.J2EEServerInstance
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.InvalidDataException
import com.intellij.openapi.util.WriteExternalException
import com.intellij.util.xmlb.XmlSerializer
import org.jdom.Element
import java.io.File
import java.lang.reflect.Modifier
import java.util.HashSet

abstract class ResinModelBase<D : ResinModelDataBase> : ServerModel, Cloneable {
    private var myData: D = createResinModelData()
    private lateinit var myCommonModel: CommonModel
    @Volatile
    private var myHelperCache: HelperCache? = null

    protected val data: D
        get() = myData

    val project: Project
        get() = getCommonModel().project

    var port: Int
        get() = data.port
        set(value) {
            data.port = value
        }

    var jmxPort: Int
        get() = data.jmxPort
        set(value) {
            data.jmxPort = value
        }

    var charset: String
        get() = data.charset.trim()
        set(value) {
            data.charset = value
        }

    val helper: ResinPersistentDataHelper
        get() {
            val applicationServer = getCommonModel().applicationServer
            val cached = myHelperCache
            if (cached != null && cached.applicationServer === applicationServer) {
                return cached.helper
            }

            return synchronized(this) {
                val currentApplicationServer = getCommonModel().applicationServer
                val current = myHelperCache
                if (current != null && current.applicationServer === currentApplicationServer) {
                    current.helper
                } else {
                    ResinPersistentDataHelper(currentApplicationServer).also { newHelper ->
                        myHelperCache = HelperCache(currentApplicationServer, newHelper)
                    }
                }
            }
        }

    val installation: ResinInstallation?
        get() = helper.getInstallation()

    val jmxStrategy: JmxConfigurationStrategy?
        get() = helper.getJmxStrategy()

    final override fun getDefaultPort(): Int = ResinUtil.DEFAULT_PORT

    final override fun setCommonModel(commonModel: CommonModel) {
        myCommonModel = commonModel
    }

    fun getCommonModel(): CommonModel = myCommonModel

    final override fun createServerInstance(): J2EEServerInstance = ResinServerInstance(getCommonModel())

    // ABI bridge: both the 242 compatibility floor and 262 still declare this for-removal method as abstract.
    // ResinManager owns the provider through AppServerIntegration; keep the required nullable bridge only,
    // matching the platform's own JavaeeServerModel implementation until ServerModel removes the method.
    @Suppress("OVERRIDE_DEPRECATION")
    final override fun getDeploymentProvider(): DeploymentProvider? = null

    final override fun getDefaultUrlForBrowser(): String {
        val commonModel = getCommonModel()
        val urlMapping = commonModel.integration.deployedFileUrlProvider as ApplicationServerUrlMapping
        return urlMapping.getDefaultUrlForServerConfig(commonModel)
    }

    final override fun createOutputProcessor(
        processHandler: ProcessHandler,
        j2EEServerInstance: J2EEServerInstance,
    ): OutputProcessor = DefaultOutputProcessor(processHandler)

    public override fun clone(): Any {
        try {
            val copy = javaClass.getDeclaredConstructor().newInstance()
            var cls: Class<*>? = javaClass
            while (cls != null && cls != Any::class.java) {
                for (field in cls.declaredFields) {
                    if (Modifier.isStatic(field.modifiers)) {
                        continue
                    }
                    field.isAccessible = true
                    field.set(copy, field.get(this))
                }
                cls = cls.superclass
            }

            // IntelliJ clones server models while building editable configuration snapshots.
            // Keep platform/runtime collaborators shallow, but give persisted model data its
            // own copy so a draft cannot mutate the live configuration before it is accepted.
            val serializedData = Element("state")
            writeExternal(serializedData)
            copy.readExternal(serializedData)
            return copy
        } catch (e: Exception) {
            val cloneException = CloneNotSupportedException(e.message)
            cloneException.initCause(e)
            throw cloneException
        }
    }

    override fun getLocalPort(): Int = port

    @Throws(RuntimeConfigurationException::class)
    override fun checkConfiguration() {
        if (!ResinUtil.isValidPort(port)) {
            throw RuntimeConfigurationError(ResinBundle.message("run.config.dlg.http.port.error", port))
        }
        if (!ResinUtil.isValidPort(jmxPort)) {
            throw RuntimeConfigurationError(ResinBundle.message("run.config.dlg.jmx.port.error", jmxPort))
        }
        if (!ResinUtil.isValidCharset(charset)) {
            throw RuntimeConfigurationError(ResinBundle.message("run.config.dlg.charset.error", charset))
        }

        val contexts = HashSet<String>()
        for (deploymentModel in getCommonModel().deploymentModels) {
            val model = deploymentModel as ResinModuleDeploymentModel
            val contextPath = model.contextPath
            if (!model.isDefaultContextPath && !contexts.add(contextPath)) {
                throw RuntimeConfigurationError(ResinBundle.message("error.duplicate.context.path.text", contextPath))
            }
        }
    }

    @Throws(InvalidDataException::class)
    final override fun readExternal(element: Element) {
        val newData = createResinModelData()
        XmlSerializer.deserializeInto(newData, element)
        myData = newData
    }

    @Throws(WriteExternalException::class)
    final override fun writeExternal(element: Element) {
        serializeObjectInto(myData, element)
    }

    fun hasJmxStrategy(): Boolean = helper.hasJmxStrategy()

    protected abstract fun createResinModelData(): D

    abstract fun transferFile(webAppFile: File): Boolean

    abstract fun deleteFile(webAppFile: File): Boolean

    abstract fun createAdditionalDeploymentSettingsEditor(
        commonModel: CommonModel,
        source: DeploymentSource,
    ): SettingsEditor<DeploymentModel>?

    open fun getJmxUsername(): String? = null

    open fun getJmxPassword(): String? = null

    internal open fun getJmxCredentials(): JmxCredentials? {
        val username = getJmxUsername()
        val password = getJmxPassword()
        if (username == null && password == null) return null
        check(!username.isNullOrBlank() && password != null) { "Resin JMX credentials must contain both username and password" }
        return JmxCredentials(username, password)
    }

    private data class HelperCache(
        val applicationServer: ApplicationServer?,
        val helper: ResinPersistentDataHelper,
    )
}
