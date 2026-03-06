package com.dingdangmaoup.resin.pura

import com.dingdangmaoup.resin.pura.resin.ResinConfiguration
import com.dingdangmaoup.resin.pura.resin.ResinInstallation
import com.dingdangmaoup.resin.pura.resin.version.ResinVersion
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.configurations.ParametersList
import com.intellij.javaee.appServers.deployment.DeploymentModel
import com.intellij.javaee.appServers.run.configuration.CommonModel
import com.intellij.javaee.appServers.run.configuration.JavaCommandLineStartupPolicy
import com.intellij.javaee.artifact.JavaeeArtifactUtil
import com.intellij.javaee.jmxremote.JmxRemotePrepareResult
import com.intellij.javaee.jmxremote.JmxRemoteUtil
import com.intellij.javaee.web.facet.WebFacet
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.packaging.artifacts.Artifact
import com.intellij.util.PathsList
import java.io.File
import java.io.FileReader
import java.io.IOException
import java.nio.charset.Charset
import java.text.MessageFormat
import java.util.ArrayList
import java.util.Arrays
import java.util.Collections
import java.util.Properties

class ResinStartupPolicy : JavaCommandLineStartupPolicy {
    private var resinRunProps: Properties? = null

    @Throws(ExecutionException::class)
    override fun createCommandLine(commonModel: CommonModel): JavaParameters {
        val resinModel = commonModel.serverModel as ResinModel
        val prepareResult: JmxRemotePrepareResult?

        if (resinModel.hasJmxStrategy()) {
            prepareResult = JmxRemoteUtil.prepare(resinModel)
            JmxRemoteUtil.apply(resinModel, prepareResult)
        } else {
            prepareResult = null
        }

        if (prepareResult == null) {
            resinModel.setAccessFile(null)
            resinModel.setPasswordFile(null)
        } else {
            resinModel.setAccessFile(prepareResult.accessFile)
            resinModel.setPasswordFile(prepareResult.passwordFile)
        }

        val resinConfiguration: ResinConfiguration = resinModel.getOrCreateResinConfiguration(true)
        val installation: ResinInstallation = resinConfiguration.getInstallation()
        val homePath = FileUtil.toSystemDependentName(installation.getResinHome().path)

        val parameters = JavaParameters()
        val charset = resinModel.charset
        if (charset.isNotEmpty()) {
            parameters.charset = Charset.forName(charset)
        }

        if (resinConfiguration.getInstallation().getVersion().allowXdebug()) {
            loadResinRunProp(DEBUG_VM_PARAMS_PROP, parameters)
        }

        val resinVersion: ResinVersion = resinConfiguration.getInstallation().getVersion()
        parameters.workingDirectory = homePath
        parameters.mainClass = resinVersion.getStartupClass()

        if (resinModel.hasJmxStrategy()) {
            loadResinRunProp(JMX_VM_PARAMS_PROP, parameters, resinModel.jmxPort.toString())
            JmxRemoteUtil.apply(parameters.vmParametersList, prepareResult)
        }

        loadResinRunProp(RESINHOME_VM_PARAMS_PROP, parameters, homePath)

        if (homePath.indexOf(' ') != -1 && !allowsRunWithWhiteSpace(resinVersion)) {
            throw ExecutionException(ResinBundle.message("resin.run.error.invalid.path", homePath))
        }

        loadResinRunProp(JAVA_LIB_PATH_VM_PARAMS_PROP, parameters, homePath)

        val parametersList: ParametersList = parameters.programParametersList
        parametersList.add(
            getResinRunProperty(COMMAND_LINE_CONF_ARG_PROP)[0],
            resinConfiguration.getConfigFile().absolutePath,
        )
        val serverId = resinConfiguration.getServerId()
        if (!StringUtil.isEmpty(serverId)) {
            val sid = serverId!!
            parametersList.add(getResinRunProperty(COMMAND_LINE_SERVER_ID_ARG_PROP)[0], sid)
            loadResinRunProp(SERVER_ID_VM_PARAMS_PROP, parameters, sid)
        }
        val additionalParameters = resinModel.getAdditionalParameters()
        if (!additionalParameters.isNullOrEmpty()) {
            parametersList.addParametersString(additionalParameters)
        }
        loadResinConfProperties(homePath, parameters)

        val classpath: PathsList = parameters.classPath

        val files = commonModel.applicationServer.library.getFiles(OrderRootType.CLASSES)
        for (file in files) {
            classpath.add(file.presentableUrl)
        }

        val allJars = resinConfiguration.getInstallation().getLibFiles(true)
        for (jar in allJars) {
            val path = jar.absolutePath
            if (!classpath.pathList.contains(path)) {
                classpath.add(path)
            }
        }

        if (resinModel.isAutoBuildClassPath()) {
            val outputAndLibs: MutableCollection<VirtualFile> = ArrayList()
            for (model: DeploymentModel in commonModel.deploymentModels) {
                val artifact: Artifact = model.artifact ?: continue
                val webFacets: Collection<WebFacet> = JavaeeArtifactUtil.getInstance()
                    .getFacetsIncludedInArtifact(commonModel.project, artifact, WebFacet.ID)
                for (webFacet in webFacets) {
                    val mrm = ModuleRootManager.getInstance(webFacet.module)
                    val roots = mrm.orderEntries().withoutSdk().recursively().exportedOnly().classesRoots
                    outputAndLibs.addAll(roots)
                }
            }
            for (vfile in outputAndLibs) {
                classpath.add(vfile)
            }
        }

        return parameters
    }

    @Throws(ExecutionException::class)
    private fun allowsRunWithWhiteSpace(resinVersion: ResinVersion): Boolean {
        val value = getResinRunProperty(RESIN_VERSIONS_INVALID_PATHS_PROP)
        val invalids: List<String> = Arrays.asList(*value)
        val verNumber = resinVersion.getVersionNumber()
        if (invalids.contains(verNumber)) {
            return false
        }
        val toCheck = verNumber.split("\\.".toRegex())
        for (actual in toCheck) {
            if (invalids.contains("$actual.x")) {
                return false
            }
        }
        return true
    }

    @Throws(ExecutionException::class)
    private fun loadResinRunProp(prop: String, parameters: JavaParameters, vararg substitution: Any) {
        val values = getResinRunProperty(prop, *substitution)
        for (value in values) {
            parameters.vmParametersList.add(value)
        }
    }

    @Throws(ExecutionException::class)
    private fun getResinRunProperty(prop: String, vararg substitution: Any): Array<String> {
        loadResinRunProperties()
        val value = resinRunProps!!.getProperty(prop)
            ?: throw ExecutionException(ResinBundle.message("resin.run.property.missing", prop))
        val res = value.split(" ").toTypedArray()
        for (i in res.indices) {
            res[i] = MessageFormat.format(res[i], *substitution)
        }
        return res
    }

    @Throws(ExecutionException::class)
    private fun loadResinRunProperties() {
        if (resinRunProps != null) return
        resinRunProps = Properties()
        try {
            resinRunProps!!.load(javaClass.getResourceAsStream(RESIN_RUN_PROP_FILE))
        } catch (e: IOException) {
            throw ExecutionException(ResinBundle.message("resin.run.startup.no.prop"))
        }
    }

    companion object {
        private val LOG = Logger.getInstance(ResinStartupPolicy::class.java)
        private const val RESIN_RUN_PROP_FILE = "ResinRun.properties"
        private const val DEBUG_VM_PARAMS_PROP = "resin.debug.vm.param"
        private const val JMX_VM_PARAMS_PROP = "resin.jmx.vm.param"
        private const val RESINHOME_VM_PARAMS_PROP = "resin.home.vm.param"
        private const val JAVA_LIB_PATH_VM_PARAMS_PROP = "java.lib.path.vm.param"
        private const val SERVER_ID_VM_PARAMS_PROP = "server.id.vm.param"
        private const val COMMAND_LINE_CONF_ARG_PROP = "resin.command.line.conf.arg.name"
        private const val COMMAND_LINE_SERVER_ID_ARG_PROP = "resin.command.line.server.id.arg.name"
        private const val RESIN_VERSIONS_INVALID_PATHS_PROP = "resin.versions.not.allow.white.spaces"
        private const val RESIN_PROPERTIES_FILE_PATH = "/conf/resin.properties"
        private const val RESIN_JVM_ARGS_PROP = "jvm_args"

        private fun loadResinConfProperties(homePath: String, parameters: JavaParameters) {
            val propertiesFile = File(FileUtil.toSystemDependentName(homePath + RESIN_PROPERTIES_FILE_PATH))
            if (!propertiesFile.exists()) return
            val props = Properties()
            try {
                props.load(FileReader(propertiesFile))
            } catch (e: IOException) {
                LOG.info(e)
            }
            val jvmArgs = props.getProperty(RESIN_JVM_ARGS_PROP)
            if (!jvmArgs.isNullOrEmpty()) {
                parameters.vmParametersList.addParametersString(jvmArgs)
            }
        }
    }
}
