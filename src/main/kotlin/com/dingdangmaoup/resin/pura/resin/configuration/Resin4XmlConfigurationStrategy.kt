package com.dingdangmaoup.resin.pura.resin.configuration

import com.dingdangmaoup.resin.pura.ResinModel
import com.dingdangmaoup.resin.pura.resin.ResinInstallation
import com.intellij.execution.ExecutionException
import org.jdom.Element
import org.jdom.Namespace
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.HashMap

class Resin4XmlConfigurationStrategy(resinInstallation: ResinInstallation) : ResinXmlConfigurationStrategy(resinInstallation) {
    private var myServerId: String? = null

    @Throws(ExecutionException::class)
    override fun init(serverModel: ResinModel, element: Element) {
        super.init(serverModel, element)

        val elementsProvider = getElementsProvider() as Resin4ElementsProvider
        val ns: Namespace = elementsProvider.getNS()

        run {
            var hostDefaultElement = elementsProvider.getClusterElement().getChild(HOST_DEFAULT_ELEMENT, ns)
            if (hostDefaultElement == null) {
                hostDefaultElement = elementsProvider.getOrCreateChildElement(
                    elementsProvider.getClusterDefaultElement(),
                    HOST_DEFAULT_ELEMENT,
                )
            }
            val webAppDeployElement = elementsProvider.getOrCreateChildElement(hostDefaultElement, WEB_APP_DEPLOY_ELEMENT)
            webAppDeployElement.setAttribute(STARTUP_MODE_ATTRIBUTE, serverModel.getDeployMode())
        }

        val serverElement = elementsProvider.getServerElement()
        if (serverElement != null) {
            myServerId = serverElement.getAttributeValue(ID_ATTRIBUTE)
        } else if (elementsProvider.getServerDefaultElement() == null) {
            val serverMultiElement = elementsProvider.getClusterElement().getChild(SERVER_MULTI_ELEMENT, ns)
            if (serverMultiElement != null) {
                myServerId = serverMultiElement.getAttributeValue(ID_PREFIX_ATTRIBUTE) + FIRST_SERVER_ID_SUFFIX
            } else {
                throw ExecutionException("Can't find neither 'server' nor 'server-default' nor 'server-multi' element")
            }
        }

        if (serverModel.hasJmxStrategy()) {
            val jvmArgName2Element = HashMap<String, Element>()
            val jvmArgElements = elementsProvider.getParamParentElement().getChildren(JVM_ARG, ns)
            for (jvmArg in jvmArgElements) {
                if (jvmArg !is Element) continue
                val jvmArgText = jvmArg.text
                val jvmArgNameValue = jvmArgText.split(JVM_ARG_SEPARATOR, limit = 2)
                jvmArgName2Element[jvmArgNameValue[0]] = jvmArg
            }

            val jmxJvmArgs = buildJmxJvmArgs(
                serverModel.jmxPort,
                serverModel.getAccessFile(),
                serverModel.getPasswordFile(),
            )

            for ((name, value) in jmxJvmArgs) {
                var jvmArgElement = jvmArgName2Element[name]
                if (jvmArgElement == null) {
                    jvmArgElement = Element(JVM_ARG, ns)
                    elementsProvider.getParamParentElement().addContent(jvmArgElement)
                }
                jvmArgElement.text = name + JVM_ARG_SEPARATOR + value
            }
        }
    }

    override fun getServerId(): String? = myServerId

    override fun getExpandDirAttr(): String = ROOT_DIR_ATTRIBUTE

    override fun getDefaultResinConfContent(): InputStream? = javaClass.getResourceAsStream(RESIN_CONF)

    override fun createElementsProvider(): ElementsProvider = Resin4ElementsProvider(getElement())

    private inner class Resin4ElementsProvider(element: Element) : Resin31ElementsProvider(element) {
        fun getDirectClusterDefaultElement(): Element? = getRootElement().getChild("cluster-default", getNS())

        override fun doGetClusterDefaultElement(): Element {
            var result = getDirectClusterDefaultElement()
            if (result != null) {
                return result
            }
            for (configImport in getImports()) {
                val element = configImport.getImportDoc()
                if (element != null) {
                    val importElementsProvider = Resin4ElementsProvider(element)
                    result = importElementsProvider.getDirectClusterDefaultElement()
                    if (result != null) {
                        if (getInstallation().getVersion().getParsed().compare(4, 0, 41) >= 0) {
                            resolveImports(element, null, configImport.getSourceDirectory())
                        }
                        configImport.copy()
                        return result
                    }
                }
            }
            return super.doGetClusterDefaultElement()
        }
    }

    companion object {
        protected const val RESIN_CONF = "resin4.xml"
        private const val HOST_DEFAULT_ELEMENT = "host-default"
        private const val WEB_APP_DEPLOY_ELEMENT = "web-app-deploy"
        private const val SERVER_MULTI_ELEMENT = "server-multi"
        private const val ROOT_DIR_ATTRIBUTE = "root-directory"
        private const val STARTUP_MODE_ATTRIBUTE = "startup-mode"
        private const val ID_PREFIX_ATTRIBUTE = "id-prefix"
        private const val ID_ATTRIBUTE = "id"
        private const val FIRST_SERVER_ID_SUFFIX = "0"
        private const val JVM_ARG = "jvm-arg"
        private const val JVM_ARG_SEPARATOR = "="
        private const val LOOPBACK_ADDRESS = "127.0.0.1"

        @Throws(ExecutionException::class)
        internal fun buildJmxJvmArgs(
            jmxPort: Int,
            accessFile: File?,
            passwordFile: File?,
        ): List<Pair<String, String>> {
            if (jmxPort !in 1..65535) {
                throw ExecutionException("JMX port must be between 1 and 65535")
            }
            val canonicalAccessFile = requireCredentialFile("access", accessFile)
            val canonicalPasswordFile = requireCredentialFile("password", passwordFile)

            return listOf(
                "-Dcom.sun.management.jmxremote.port" to jmxPort.toString(),
                "-Dcom.sun.management.jmxremote.ssl" to "false",
                "-Dcom.sun.management.jmxremote.authenticate" to "true",
                "-Dcom.sun.management.jmxremote.password.file" to canonicalPasswordFile.path,
                "-Dcom.sun.management.jmxremote.access.file" to canonicalAccessFile.path,
                "-Dcom.sun.management.jmxremote.host" to LOOPBACK_ADDRESS,
                "-Djava.rmi.server.hostname" to LOOPBACK_ADDRESS,
            )
        }

        @Throws(ExecutionException::class)
        private fun requireCredentialFile(kind: String, file: File?): File {
            val canonicalFile = try {
                file?.canonicalFile
            } catch (e: IOException) {
                throw ExecutionException("Can't resolve the JMX $kind file", e)
            }
            if (canonicalFile == null || !canonicalFile.isFile || !canonicalFile.canRead()) {
                throw ExecutionException("Authenticated JMX requires a readable $kind file")
            }
            return canonicalFile
        }
    }
}
