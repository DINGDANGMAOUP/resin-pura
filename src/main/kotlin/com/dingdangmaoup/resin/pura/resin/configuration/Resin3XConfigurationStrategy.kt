package com.dingdangmaoup.resin.pura.resin.configuration

import com.dingdangmaoup.resin.pura.ResinBundle
import com.dingdangmaoup.resin.pura.ResinModelBase
import com.dingdangmaoup.resin.pura.resin.ResinInstallation
import com.dingdangmaoup.resin.pura.resin.WebApp
import com.dingdangmaoup.resin.pura.resin.common.MBeanUtil
import com.dingdangmaoup.resin.pura.resin.jmx.ConnectorCommandBase
import com.intellij.execution.ExecutionException
import com.intellij.javaee.appServers.deployment.DeploymentStatus
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.NotNullLazyValue
import com.intellij.openapi.util.NullableLazyValue
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.text.StringUtil
import org.jdom.Attribute
import org.jdom.Element
import org.jdom.Namespace
import java.io.File
import java.io.IOException
import java.io.InputStream
import javax.management.InstanceNotFoundException
import javax.management.JMException
import javax.management.MBeanServerConnection
import javax.management.ObjectName

open class Resin3XConfigurationStrategy(private val myResinInstallation: ResinInstallation) :
    ResinConfigurationStrategy(),
    JmxConfigurationStrategy {
    private val myElementsProvider: NotNullLazyValue<ElementsProvider> = NotNullLazyValue.lazy { createElementsProvider() }

    protected fun getInstallation(): ResinInstallation = myResinInstallation

    override fun setPort(port: Int) {
        val elementsProvider = getElementsProvider()
        val httpElement = elementsProvider.getOrCreateChildElement(elementsProvider.getParamParentElement(), HTTP)
        httpElement.setAttribute(PORT, port.toString())
    }

    @Throws(ExecutionException::class)
    override fun deploy(webApp: WebApp): Boolean {
        val dirty = Ref.create(false)
        val elementsProvider = getElementsProvider()
        val ns = elementsProvider.getNS()

        val host = getHost(webApp)
        removeAttribute(host, DIRTY_ATTR, dirty)

        val location = webApp.getLocation() ?: ""
        val isExploded = File(location).isDirectory
        val contextPath = webApp.getContextPath()

        var webAppEl = findWebAppElement(host, contextPath)
        if (webAppEl == null) {
            webAppEl = Element(WEB_APP_ELEMENT, ns)
            webAppEl.setAttribute(ID, contextPath)
            host.addContent(webAppEl)
            dirty.set(true)
        }

        val expandDirAttr = getExpandDirAttr()
        setAttribute(webAppEl, if (isExploded) expandDirAttr else ARCHIVE_PATH_ATTR, location, dirty)
        if (!isExploded) {
            var expandDir: StringBuilder? = null
            val hostParent = elementsProvider.getHostParent()
            val hostDefaultEl = hostParent.getChild(HOST_DEFAULT_ELEMENT, ns)
            if (hostDefaultEl != null) {
                val webAppDeployEl = hostDefaultEl.getChild(WEB_APP_DEPLOY_ELEMENT, ns)
                if (webAppDeployEl != null) {
                    val webAppDeployPath = webAppDeployEl.getAttributeValue(PATH_ATTR)
                    if (StringUtil.isNotEmpty(webAppDeployPath)) {
                        expandDir = StringBuilder(webAppDeployPath)
                    }
                }
            }
            if (expandDir == null) {
                expandDir = StringBuilder(DEFAULT_EXPAND_DIR)
            }
            expandDir.append("/")
            expandDir.append(
                if (ROOT_CONTEXT_PATH == contextPath) {
                    ROOT_EXPAND_DIR
                } else {
                    FileUtil.sanitizeFileName(StringUtil.trimStart(contextPath, ROOT_CONTEXT_PATH))
                },
            )
            setAttribute(webAppEl, expandDirAttr, expandDir.toString(), dirty, true)
        }

        val charset = webApp.getCharSet()
        if (charset == null || StringUtil.isEmptyOrSpaces(charset)) {
            removeAttribute(webAppEl, CHARSET, dirty)
        } else {
            setAttribute(webAppEl, CHARSET, charset.trim(), dirty)
        }

        return dirty.get()
    }

    protected open fun getExpandDirAttr(): String = DOCUMENT_DIRECTORY_ATTR

    @Throws(ExecutionException::class)
    private fun getHost(webApp: WebApp): Element {
        try {
            val elementsProvider = getElementsProvider()
            val ns = elementsProvider.getNS()
            val hostParent = elementsProvider.getHostParent()
            val webAppHost = webApp.getHost()

            val hosts = hostParent.getChildren(HOST, ns)
            for (host in hosts) {
                val idAttribute = host.getAttribute(ID)
                if (idAttribute != null && StringUtil.equals(idAttribute.value, webAppHost)) {
                    return host
                }
                val regexpAttribute = host.getAttribute(REGEXP_ATTR)
                if (regexpAttribute != null && webAppHost.matches(regexpAttribute.value.toRegex())) {
                    return host
                }
            }

            val host = Element(HOST, ns)
            host.setAttribute(ID, webAppHost)
            host.setAttribute(DIRTY_ATTR, "true")
            hostParent.addContent(host)
            return host
        } catch (e: Exception) {
            throw ExecutionException(ResinBundle.message("resin.conf.parse.error"), e)
        }
    }

    override fun undeploy(webApp: WebApp): Boolean {
        var dirty = false
        val elementsProvider = getElementsProvider()
        val ns = elementsProvider.getNS()
        val hosts = elementsProvider.getHostParent().getChildren(HOST, ns)
        for (hostEl in hosts) {
            val webAppEl = findWebAppElement(hostEl, webApp.getContextPath())
            if (webAppEl != null) {
                hostEl.removeContent(webAppEl)
                dirty = true
            }
        }
        return dirty
    }

    override fun getDefaultResinConfContent(): InputStream? = javaClass.getResourceAsStream(RESIN_CONF)

    override fun deployWithJmx(resinModel: ResinModelBase<*>, webApp: WebApp): Boolean {
        val location = webApp.getLocation() ?: return false
        val webAppFile = File(FileUtil.toSystemDependentName(location))
        if (!webAppFile.exists()) {
            LOG.error("Can't find web app")
            return false
        }

        if (getDeployStateWithJmx(resinModel, webApp, Ref.create(false)) != DeploymentStatus.UNKNOWN &&
            !UndeployCommand(resinModel, webAppFile).safeExecute()
        ) {
            return false
        }

        if (!cleanUpWebApp(resinModel, webAppFile)) {
            return true
        }
        if (!resinModel.transferFile(webAppFile)) {
            return false
        }
        if (!DeployCommand(resinModel, "start", webAppFile.name).safeExecute()) {
            return false
        }
        return true
    }

    override fun getDeployStateWithJmx(
        resinModel: ResinModelBase<*>,
        webApp: WebApp,
        isFinal: Ref<Boolean>,
    ): DeploymentStatus {
        val location = webApp.getLocation() ?: return DeploymentStatus.FAILED
        val webAppFile = File(FileUtil.toSystemDependentName(location))
        if (!webAppFile.exists()) {
            isFinal.set(true)
            return DeploymentStatus.FAILED
        }

        val getStateCommand = GetStateCommand(resinModel, webAppFile)
        if (!getStateCommand.safeExecute()) {
            isFinal.set(true)
            return DeploymentStatus.FAILED
        }

        val state = getStateCommand.getResult()
        return if (STATE_JMX_ATTRIBUTE_ACTIVE.equals(state, ignoreCase = true)) {
            isFinal.set(true)
            DeploymentStatus.DEPLOYED
        } else if (STATE_JMX_ATTRIBUTE_ERROR.equals(state, ignoreCase = true) ||
            STATE_JMX_ATTRIBUTE_FAILED.equals(state, ignoreCase = true)
        ) {
            isFinal.set(true)
            DeploymentStatus.FAILED
        } else {
            DeploymentStatus.UNKNOWN
        }
    }

    override fun undeployWithJmx(resinModel: ResinModelBase<*>, webApp: WebApp): Boolean {
        val location = webApp.getLocation() ?: return false
        val webAppFile = File(FileUtil.toSystemDependentName(location))
        if (!webAppFile.exists()) {
            LOG.error("Can't find web app")
            return false
        }

        if (!UndeployCommand(resinModel, webAppFile).safeExecute()) {
            return false
        }
        if (!cleanUpWebApp(resinModel, webAppFile)) {
            return true
        }

        val getStateCommand = GetStateCommand(resinModel, webAppFile)
        if (!getStateCommand.safeExecute()) {
            return false
        }
        return getStateCommand.getResult() == null
    }

    private fun getWebAppName(webAppFile: File): String {
        val fileName = webAppFile.name
        val trimExtension = !(webAppFile.isDirectory && myResinInstallation.getVersion().getParsed().compare(4, 0, 10) > 0)
        return if (trimExtension) FileUtilRt.getNameWithoutExtension(fileName) else fileName
    }

    protected open fun createElementsProvider(): ElementsProvider = ElementsProvider(getElement())

    protected fun getElementsProvider(): ElementsProvider = myElementsProvider.value

    private class DeployCommand(
        resinModel: ResinModelBase<*>,
        private val myCommand: String,
        private val myArg: String,
    ) : ConnectorCommandBase<Any>(resinModel) {
        @Throws(JMException::class, IOException::class)
        override fun doExecute(connection: MBeanServerConnection): Any? {
            return invokeOperation(connection, MBEAN_WEB_APP_DEPLOY, myCommand, myArg)
        }
    }

    private abstract inner class WebAppCommandBase<T>(resinModel: ResinModelBase<*>, webAppFile: File) :
        ConnectorCommandBase<T>(resinModel) {
        private val myObjectName: ObjectName = MBeanUtil.newObjectName(MBEAN_WEB_APP_PREFIX + getWebAppName(webAppFile))

        @Throws(JMException::class, IOException::class)
        override fun doExecute(connection: MBeanServerConnection): T? = doExecute(connection, myObjectName)

        @Throws(JMException::class, IOException::class)
        protected abstract fun doExecute(connection: MBeanServerConnection, objectName: ObjectName): T?
    }

    private inner class GetStateCommand(resinModel: ResinModelBase<*>, webAppFile: File) :
        WebAppCommandBase<String>(resinModel, webAppFile) {
        @Throws(JMException::class, IOException::class)
        override fun doExecute(connection: MBeanServerConnection, objectName: ObjectName): String? {
            return try {
                connection.getAttribute(objectName, STATE_JMX_ATTRIBUTE) as String
            } catch (_: InstanceNotFoundException) {
                null
            }
        }
    }

    private inner class UndeployCommand(resinModel: ResinModelBase<*>, webAppFile: File) :
        WebAppCommandBase<Boolean>(resinModel, webAppFile) {
        @Throws(JMException::class, IOException::class)
        override fun doExecute(connection: MBeanServerConnection, objectName: ObjectName): Boolean? {
            return invokeOperation(connection, objectName, "destroy")
        }
    }

    protected open class ElementsProvider(private val myRootElement: Element) {
        private val myNS: Namespace = myRootElement.namespace
        private val myServer: NullableLazyValue<Element> = object : NullableLazyValue<Element>() {
            override fun compute(): Element? {
                return getRootElement().getChild(SERVER_ELEMENT, getNS())
            }
        }
        private val myParamParent = NotNullLazyValue.lazy { doGetParamParent() }

        protected fun getRootElement(): Element = myRootElement

        fun getNS(): Namespace = myNS

        private fun getOrCreateServerElement(): Element = getOrCreateChildElement(getRootElement(), SERVER_ELEMENT)

        open fun getHostParent(): Element = getOrCreateServerElement()

        protected open fun doGetParamParent(): Element = getOrCreateServerElement()

        fun getOrCreateChildElement(parentElement: Element, childName: String): Element {
            var result = parentElement.getChild(childName, getNS())
            if (result == null) {
                result = Element(childName, getNS())
                parentElement.addContent(result)
            }
            return result
        }

        fun getServerElement(): Element? = myServer.value

        fun getParamParentElement(): Element = myParamParent.value
    }

    companion object {
        private val LOG = Logger.getInstance(Resin3XConfigurationStrategy::class.java)
        protected const val SERVER_ELEMENT = "server"
        protected const val SERVER_DEFAULT_ELEMENT = "server-default"
        protected const val HTTP = "http"
        protected const val PORT = "port"
        protected const val DIRTY_ATTR = "dirty"
        protected const val WEB_APP_ELEMENT = "web-app"
        protected const val ID = "id"
        private const val DOCUMENT_DIRECTORY_ATTR = "document-directory"
        private const val ARCHIVE_PATH_ATTR = "archive-path"
        protected const val HOST = "host"
        protected const val CHARSET = "character-encoding"
        private const val HOST_DEFAULT_ELEMENT = "host-default"
        private const val WEB_APP_DEPLOY_ELEMENT = "web-app-deploy"
        private const val ROOT_CONTEXT_PATH = "/"
        private const val ROOT_EXPAND_DIR = "ROOT"
        private const val DEFAULT_EXPAND_DIR = "webapps"
        private const val PATH_ATTR = "path"
        private const val REGEXP_ATTR = "regexp"
        protected const val RESIN_CONF = "resin3.conf"

        @JvmField
        val MBEAN_WEB_APP_DEPLOY: ObjectName = MBeanUtil.newObjectName("resin:type=WebAppDeploy,Host=default,name=webapps")

        private const val MBEAN_WEB_APP_PREFIX = "resin:type=WebApp,Host=default,name=/"

        @JvmField
        val STATE_JMX_ATTRIBUTE: String = "State"

        @JvmField
        val STATE_JMX_ATTRIBUTE_ACTIVE: String = "active"

        @JvmField
        val STATE_JMX_ATTRIBUTE_ERROR: String = "error"

        @JvmField
        val STATE_JMX_ATTRIBUTE_FAILED: String = "failed"

        private fun findWebAppElement(host: Element, contextPath: String): Element? {
            val webApps = host.getChildren(WEB_APP_ELEMENT, host.namespace)
            for (webAppEl in webApps) {
                if (webAppEl.getAttribute(ID).value == contextPath) {
                    return webAppEl
                }
            }
            return null
        }

        private fun setAttribute(element: Element, name: String, value: String, dirty: Ref<Boolean>) {
            setAttribute(element, name, value, dirty, false)
        }

        private fun setAttribute(element: Element, name: String, value: String, dirty: Ref<Boolean>, keepExisting: Boolean) {
            val existingAttribute: Attribute? = element.getAttribute(name)
            if (existingAttribute == null || (!keepExisting && existingAttribute.value != value)) {
                element.setAttribute(name, value)
                dirty.set(true)
            }
        }

        private fun removeAttribute(element: Element, name: String, dirty: Ref<Boolean>) {
            if (element.getAttribute(name) != null) {
                element.removeAttribute(name)
                dirty.set(true)
            }
        }

        private fun cleanUpWebApp(resinModel: ResinModelBase<*>, webAppFile: File): Boolean {
            if (!webAppFile.isDirectory &&
                !resinModel.deleteFile(File(webAppFile.parent, FileUtilRt.getNameWithoutExtension(webAppFile.name)))
            ) {
                return false
            }
            if (!resinModel.deleteFile(webAppFile)) {
                return false
            }
            return true
        }
    }
}
