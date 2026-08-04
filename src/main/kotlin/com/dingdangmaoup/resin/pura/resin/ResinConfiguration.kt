package com.dingdangmaoup.resin.pura.resin

import com.dingdangmaoup.resin.pura.ResinBundle
import com.dingdangmaoup.resin.pura.ResinDeploymentProvider
import com.dingdangmaoup.resin.pura.ResinModel
import com.dingdangmaoup.resin.pura.resin.configuration.ResinConfigurationStrategy
import com.dingdangmaoup.resin.pura.resin.configuration.ResinGeneratedConfig
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.RuntimeConfigurationException
import com.intellij.javaee.appServers.deployment.DeploymentModel
import com.intellij.openapi.util.JDOMUtil
import org.jdom.Attribute
import org.jdom.Element
import org.jdom.JDOMException
import java.io.File
import java.io.IOException

class ResinConfiguration(serverModel: ResinModel) {
    private val myInstallation: ResinInstallation
    private val myGeneratedConfig: ResinGeneratedConfig?
    private val mySourceConfig: File
    private val myStrategy: ResinConfigurationStrategy

    init {
        val helper = serverModel.helper
        myInstallation = helper.getInstallationOrError()
            ?: throw ExecutionException(ResinBundle.message("message.error.resin.home.doesnt.exist"))

        try {
            mySourceConfig = serverModel.findConfFile()
        } catch (e: RuntimeConfigurationException) {
            throw ExecutionException(e.localizedMessage ?: "")
        }

        myStrategy = ResinConfigurationStrategy.getForInstallation(myInstallation)

        if (serverModel.isReadOnlyConfiguration()) {
            myGeneratedConfig = null
        } else {
            try {
                val usesBundledTemplate = mySourceConfig.length() == 0L
                val document: Element = if (usesBundledTemplate) {
                    val stream = myStrategy.getDefaultResinConfContent()
                        ?: throw ExecutionException(ResinBundle.message("run.resin.conf.doesnt.exist"))
                    stream.use(JDOMUtil::load)
                } else {
                    JDOMUtil.load(mySourceConfig)
                }

                myGeneratedConfig = ResinGeneratedConfig(document, "resin")
                patchConfigToMakeDebuggerWork(document)
                val configOrigin = if (usesBundledTemplate) {
                    File(File(myInstallation.getResinHome(), "conf"), mySourceConfig.name)
                } else {
                    mySourceConfig
                }
                myStrategy.init(serverModel, document, configOrigin)
                myStrategy.setPort(serverModel.port)
            } catch (e: JDOMException) {
                throw ExecutionException(ResinBundle.message("run.resin.conf.load.error"), e)
            } catch (e: IOException) {
                throw ExecutionException(ResinBundle.message("run.resin.conf.load.error"), e)
            }

            for (model: DeploymentModel in serverModel.getCommonModel().deploymentModels) {
                if (model.deploymentMethod == ResinDeploymentProvider.CONF_DEPLOYMENT_METHOD) {
                    val webApp = ResinDeploymentProvider.getWebApp(model)
                    if (webApp != null) {
                        myStrategy.deploy(webApp)
                    }
                }
            }
            save()
        }
    }

    fun getServerId(): String? = myStrategy.getServerId()

    fun getInstallation(): ResinInstallation = myInstallation

    private fun isWritable(): Boolean = myGeneratedConfig != null

    fun getConfigFile(): File = if (isWritable()) myGeneratedConfig!!.getFile() else mySourceConfig

    @Throws(ExecutionException::class)
    fun deploy(webApp: WebApp) {
        if (!isWritable()) return
        myStrategy.deploy(webApp)
        save()
    }

    @Throws(ExecutionException::class)
    fun undeploy(webApp: WebApp): Boolean {
        if (!isWritable()) return false
        val result = myStrategy.undeploy(webApp)
        save()
        return result
    }

    @Throws(ExecutionException::class)
    private fun save() {
        myGeneratedConfig!!.save()
        myStrategy.save()
    }

    companion object {
        private const val JAVAC_ELEMENT = "javac"
        private const val ARGS_ATTRIBUTE = "args"
        private const val COMPILER_ATTRIBUTE = "compiler"
        private const val COMPILER_ATTRIBUTE_VALUE = "internal"
        private const val ARGS_ATTRIBUTE_VALUE = "-source 1.5"
        private const val DEBUG_OPTION = "-g"
        private val DEBUG_OPTION_PATTERN = Regex("""(?<!\S)-g(?::[^\s]+)?(?=\s|$)""")

        @JvmStatic
        internal fun patchConfigToMakeDebuggerWork(element: Element) {
            var javac = element.getChild(JAVAC_ELEMENT, element.namespace)
            if (javac == null) {
                javac = Element(JAVAC_ELEMENT, element.namespace)
                javac.setAttribute(COMPILER_ATTRIBUTE, COMPILER_ATTRIBUTE_VALUE)
                javac.setAttribute(ARGS_ATTRIBUTE, ARGS_ATTRIBUTE_VALUE)
                element.addContent(javac)
            }
            val args: Attribute = javac.getAttribute(ARGS_ATTRIBUTE)
                ?: javac.setAttribute(ARGS_ATTRIBUTE, ARGS_ATTRIBUTE_VALUE).getAttribute(ARGS_ATTRIBUTE)
            val debugOptions = DEBUG_OPTION_PATTERN.findAll(args.value).toList()
            args.value = when {
                debugOptions.isEmpty() -> args.value.trim().let { remaining ->
                    if (remaining.isEmpty()) DEBUG_OPTION else "$DEBUG_OPTION $remaining"
                }
                debugOptions.size == 1 && debugOptions.single().value == DEBUG_OPTION -> args.value
                else -> DEBUG_OPTION_PATTERN.replace(args.value, "").trim().let { remaining ->
                    if (remaining.isEmpty()) DEBUG_OPTION else "$DEBUG_OPTION $remaining"
                }
            }
        }
    }
}
