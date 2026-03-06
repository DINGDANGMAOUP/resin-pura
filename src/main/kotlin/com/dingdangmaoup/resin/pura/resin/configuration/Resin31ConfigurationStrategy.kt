package com.dingdangmaoup.resin.pura.resin.configuration

import com.dingdangmaoup.resin.pura.resin.ResinInstallation
import com.intellij.openapi.util.NotNullLazyValue
import com.intellij.openapi.util.NullableLazyValue
import org.jdom.Element

open class Resin31ConfigurationStrategy(resinInstallation: ResinInstallation) : Resin3XConfigurationStrategy(resinInstallation) {
    override fun createElementsProvider(): ElementsProvider = Resin31ElementsProvider(getElement())

    protected open class Resin31ElementsProvider(element: Element) : ElementsProvider(element) {
        private val myCluster = NotNullLazyValue.lazy {
            getRootElement().getChild(CLUSTER_ELEMENT, getNS())
        }
        private val myClusterDefault = NotNullLazyValue.lazy {
            doGetClusterDefaultElement()
        }
        private val myServerDefault: NullableLazyValue<Element> = object : NullableLazyValue<Element>() {
            override fun compute(): Element? {
                return getClusterElement().getChild("server-default", getNS())
            }
        }

        fun getClusterElement(): Element = myCluster.value

        fun getClusterDefaultElement(): Element = myClusterDefault.value

        fun getServerDefaultElement(): Element? = myServerDefault.value

        override fun getHostParent(): Element = getClusterElement()

        protected open fun doGetClusterDefaultElement(): Element {
            return getOrCreateChildElement(getRootElement(), CLUSTER_DEFAULT_ELEMENT)
        }

        override fun doGetParamParent(): Element {
            val serverElement = getServerElement()
            if (serverElement != null) {
                return serverElement
            }
            val serverDefaultElement = getServerDefaultElement()
            if (serverDefaultElement != null) {
                return serverDefaultElement
            }
            return getOrCreateChildElement(getClusterDefaultElement(), "server-default")
        }
    }

    companion object {
        protected const val CLUSTER_ELEMENT = "cluster"
        protected const val CLUSTER_DEFAULT_ELEMENT = "cluster-default"
    }
}
