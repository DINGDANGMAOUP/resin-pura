package com.dingdangmaoup.resin.pura

import com.dingdangmaoup.resin.pura.resin.ResinConfiguration
import com.dingdangmaoup.resin.pura.resin.ResinInstallation
import com.dingdangmaoup.resin.pura.resin.version.ResinVersion
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
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

        val parameters = if (prepareResult == null) {
            JavaParameters()
        } else {
            SecureJmxJavaParameters(resinModel.jmxPort, prepareResult)
        }
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
        if (additionalParameters.isNotEmpty()) {
            parametersList.addParametersString(additionalParameters)
        }
        loadResinConfProperties(homePath, parameters)

        if (prepareResult != null) {
            enforceJmxSecurity(
                parameters.vmParametersList,
                parameters.programParametersList,
                resinModel.jmxPort,
                prepareResult,
            )
        }

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
        return isWhitespacePathSupported(resinVersion.getVersionNumber(), value.asList())
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
        val stream = javaClass.getResourceAsStream(RESIN_RUN_PROP_FILE)
            ?: throw ExecutionException(ResinBundle.message("resin.run.startup.no.prop"))
        try {
            stream.use { resinRunProps!!.load(it) }
        } catch (_: IOException) {
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

        internal fun isWhitespacePathSupported(versionNumber: String, invalidVersions: Collection<String>): Boolean {
            if (versionNumber in invalidVersions) return false

            val versionParts = versionNumber.split('.')
            for (index in 1 until versionParts.size) {
                val wildcard = versionParts.take(index).joinToString(".") + ".x"
                if (wildcard in invalidVersions) return false
            }
            return true
        }

        @Throws(ExecutionException::class)
        internal fun enforceJmxSecurity(
            vmParameters: ParametersList,
            programParameters: ParametersList,
            jmxPort: Int,
            prepareResult: JmxRemotePrepareResult,
        ) {
            if (jmxPort !in 1..65535) {
                throw ExecutionException("JMX port must be between 1 and 65535")
            }

            val passwordPath: String
            val accessPath: String
            try {
                passwordPath = prepareResult.passwordFile.canonicalPath
                accessPath = prepareResult.accessFile.canonicalPath
            } catch (e: IOException) {
                throw ExecutionException(e)
            }

            // resin.properties is user-controlled and is loaded after the standard VM arguments.
            // Remove every security-sensitive definition before appending one authoritative value;
            // ParametersList#defineProperty intentionally keeps an earlier duplicate unchanged.
            val protectedProperties = linkedMapOf(
                JMX_PORT_PROPERTY to jmxPort.toString(),
                JMX_SSL_PROPERTY to "false",
                JMX_AUTHENTICATE_PROPERTY to "true",
                JMX_PASSWORD_FILE_PROPERTY to passwordPath,
                JMX_ACCESS_FILE_PROPERTY to accessPath,
                JMX_HOST_PROPERTY to LOOPBACK_ADDRESS,
                RMI_HOSTNAME_PROPERTY to LOOPBACK_ADDRESS,
            )
            replaceProperties(vmParameters, protectedProperties)
            replaceResinChildJmxArguments(programParameters, protectedProperties)
        }

        private fun replaceProperties(parameters: ParametersList, properties: Map<String, String>) {
            val retained = parameters.list.filterNot { parameter ->
                getProtectedPropertyName(parameter) != null
            }
            parameters.clearAll()
            parameters.addAll(retained)
            parameters.add("-D$JMX_ENABLE_PROPERTY")
            parameters.add("-D$JMX_BUILDER_PROPERTY=$JMX_BUILDER_CLASS")
            for ((name, value) in properties) {
                parameters.add("-D$name=$value")
            }
        }

        private fun replaceResinChildJmxArguments(parameters: ParametersList, properties: Map<String, String>) {
            val retained = ArrayList<String>()
            val current = parameters.list
            var index = 0
            while (index < current.size) {
                val parameter = current[index]
                if (parameter == RESIN_JMX_PORT_ARGUMENT || parameter == RESIN_JMX_PORT_LONG_ARGUMENT) {
                    index++
                    // A split Resin port option normally owns the following value. Preserve a
                    // following option, though, so a dangling/malformed -jmx-port cannot make the
                    // security filter accidentally consume an unrelated Resin argument.
                    if (index < current.size && isResinJmxPortValue(current[index])) {
                        index++
                    }
                    continue
                }
                if (parameter.startsWith("$RESIN_JMX_PORT_ARGUMENT=") ||
                    parameter.startsWith("$RESIN_JMX_PORT_LONG_ARGUMENT=") ||
                    getProtectedPropertyName(parameter) != null
                ) {
                    index++
                    continue
                }
                retained.add(parameter)
                index++
            }

            parameters.clearAll()
            parameters.addAll(retained)
            parameters.add("-J-D$JMX_ENABLE_PROPERTY")
            parameters.add("-J-D$JMX_BUILDER_PROPERTY=$JMX_BUILDER_CLASS")
            for ((name, value) in properties) {
                parameters.add("-J-D$name=$value")
            }
        }

        private fun isResinJmxPortValue(parameter: String): Boolean =
            parameter.toIntOrNull() != null || !parameter.startsWith('-')

        private fun getProtectedPropertyName(parameter: String): String? {
            val definition = when {
                parameter.startsWith("-J-D") -> parameter.substring(4)
                parameter.startsWith("-D") -> parameter.substring(2)
                else -> return null
            }
            val name = definition.substringBefore('=')
            return name.takeIf {
                it == JMX_ENABLE_PROPERTY ||
                    it.startsWith("$JMX_ENABLE_PROPERTY.") ||
                    it == RMI_HOSTNAME_PROPERTY ||
                    it == JMX_BUILDER_PROPERTY
            }
        }

        private fun loadResinConfProperties(homePath: String, parameters: JavaParameters) {
            val propertiesFile = File(FileUtil.toSystemDependentName(homePath + RESIN_PROPERTIES_FILE_PATH))
            if (!propertiesFile.exists()) return
            val props = Properties()
            try {
                FileReader(propertiesFile).use(props::load)
            } catch (e: IOException) {
                LOG.info(e)
            }
            val jvmArgs = props.getProperty(RESIN_JVM_ARGS_PROP)
            if (!jvmArgs.isNullOrEmpty()) {
                parameters.vmParametersList.addParametersString(jvmArgs)
            }
        }

        private const val JMX_PORT_PROPERTY = "com.sun.management.jmxremote.port"
        private const val JMX_ENABLE_PROPERTY = "com.sun.management.jmxremote"
        private const val JMX_SSL_PROPERTY = "com.sun.management.jmxremote.ssl"
        private const val JMX_AUTHENTICATE_PROPERTY = "com.sun.management.jmxremote.authenticate"
        private const val JMX_PASSWORD_FILE_PROPERTY = "com.sun.management.jmxremote.password.file"
        private const val JMX_ACCESS_FILE_PROPERTY = "com.sun.management.jmxremote.access.file"
        private const val JMX_HOST_PROPERTY = "com.sun.management.jmxremote.host"
        private const val RMI_HOSTNAME_PROPERTY = "java.rmi.server.hostname"
        private const val JMX_BUILDER_PROPERTY = "javax.management.builder.initial"
        private const val JMX_BUILDER_CLASS = "com.caucho.jmx.MBeanServerBuilderImpl"
        private const val RESIN_JMX_PORT_ARGUMENT = "-jmx-port"
        private const val RESIN_JMX_PORT_LONG_ARGUMENT = "--jmx-port"
        private const val LOOPBACK_ADDRESS = "127.0.0.1"
    }

    internal class SecureJmxJavaParameters(
        private val jmxPort: Int,
        private val prepareResult: JmxRemotePrepareResult,
    ) : JavaParameters() {
        override fun toCommandLine(): GeneralCommandLine {
            // JavaCommandLineLocalState appends common VM options and run extensions after
            // the startup policy returns. Re-apply the invariant at the final conversion
            // boundary so no later duplicate can weaken authentication or loopback binding.
            enforceJmxSecurity(vmParametersList, programParametersList, jmxPort, prepareResult)
            return super.toCommandLine()
        }
    }
}
