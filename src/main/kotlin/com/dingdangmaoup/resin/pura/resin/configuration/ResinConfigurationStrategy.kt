package com.dingdangmaoup.resin.pura.resin.configuration

import com.dingdangmaoup.resin.pura.ResinModel
import com.dingdangmaoup.resin.pura.resin.ResinInstallation
import com.dingdangmaoup.resin.pura.resin.WebApp
import com.intellij.execution.ExecutionException
import org.jdom.Element
import java.io.File
import java.io.InputStream

abstract class ResinConfigurationStrategy {
    private lateinit var myElement: Element
    private var mySourceConfig: File? = null

    @Throws(ExecutionException::class)
    internal fun init(serverModel: ResinModel, element: Element, sourceConfig: File) {
        mySourceConfig = sourceConfig.absoluteFile
        init(serverModel, element)
    }

    @Throws(ExecutionException::class)
    open fun init(serverModel: ResinModel, element: Element) {
        myElement = element
    }

    protected fun getElement(): Element = myElement

    protected fun getSourceConfigDirectory(): File? = mySourceConfig?.parentFile

    @Throws(ExecutionException::class)
    open fun save() {}

    abstract fun setPort(port: Int)

    @Throws(ExecutionException::class)
    abstract fun deploy(webApp: WebApp): Boolean

    @Throws(ExecutionException::class)
    abstract fun undeploy(webApp: WebApp): Boolean

    abstract fun getDefaultResinConfContent(): InputStream?

    open fun getServerId(): String? = null

    companion object {
        @JvmStatic
        fun getForInstallation(installation: ResinInstallation): ResinConfigurationStrategy {
            val versionParts = installation.getVersion().getVersionNumber().split('.')
            val resinVersion = versionParts.firstOrNull()?.toIntOrNull()
            val minorVersion = versionParts.getOrNull(1)?.toIntOrNull()
            return when (resinVersion) {
                2 -> Resin2XConfigurationStrategy()
                3 -> when (minorVersion) {
                    1 -> Resin31ConfigurationStrategy(installation)
                    null, 0 -> Resin3XConfigurationStrategy(installation)
                    else -> ResinXmlConfigurationStrategy(installation)
                }

                4 -> Resin4XmlConfigurationStrategy(installation)
                else -> Resin3XConfigurationStrategy(installation)
            }
        }
    }
}
