package com.dingdangmaoup.resin.pura.resin.configuration

import com.dingdangmaoup.resin.pura.ResinModel
import com.dingdangmaoup.resin.pura.resin.ResinInstallation
import com.dingdangmaoup.resin.pura.resin.WebApp
import com.intellij.execution.ExecutionException
import org.jdom.Element
import java.io.InputStream

abstract class ResinConfigurationStrategy {
    private lateinit var myElement: Element

    @Throws(ExecutionException::class)
    open fun init(serverModel: ResinModel, element: Element) {
        myElement = element
    }

    protected fun getElement(): Element = myElement

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
            val verNumber = installation.getVersion().getVersionNumber()
            val resinVersion = verNumber.substring(0, verNumber.indexOf('.')).toInt()
            val buildVersion = verNumber.substring(2, 3).toInt()
            return when (resinVersion) {
                2 -> Resin2XConfigurationStrategy()
                3 -> when (buildVersion) {
                    0 -> Resin3XConfigurationStrategy(installation)
                    1 -> Resin31ConfigurationStrategy(installation)
                    else -> ResinXmlConfigurationStrategy(installation)
                }

                4 -> Resin4XmlConfigurationStrategy(installation)
                else -> Resin3XConfigurationStrategy(installation)
            }
        }
    }
}
