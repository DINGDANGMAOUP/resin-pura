package com.dingdangmaoup.resin.pura.resin.configuration

import com.dingdangmaoup.resin.pura.resin.WebApp
import org.jdom.Element
import java.io.InputStream

class Resin2XConfigurationStrategy : ResinConfigurationStrategy() {
    override fun setPort(port: Int) {
        val rootElement = getElement()
        var httpElement = rootElement.getChild(HTTP_SERVER).getChild(HTTP)
        if (httpElement == null) {
            httpElement = Element(HTTP)
            httpElement.setAttribute(PORT, port.toString())
            rootElement.getChild(HTTP_SERVER).addContent(httpElement)
        } else if (httpElement.getAttribute(PORT).value != port.toString()) {
            httpElement.setAttribute(PORT, port.toString())
        }
    }

    override fun deploy(webApp: WebApp): Boolean {
        var dirty = false
        val host = getHost(getElement().getChild(HTTP_SERVER), webApp)
        if (host.getAttribute(APP_DIR) != null) {
            host.removeAttribute(APP_DIR)
            dirty = true
        }
        if (host.getAttribute(DIRTY) != null) {
            host.removeAttribute(DIRTY)
            dirty = true
        }

        var webAppFound = false
        val webapps = host.getChildren(WEB_APP)
        for (webappEl in webapps) {
            if (webappEl.getAttribute(ID).value == webApp.getContextPath()) {
                webAppFound = true
                val location = webApp.getLocation() ?: ""
                if (webappEl.getAttribute(APP_DIR) == null || webappEl.getAttribute(APP_DIR).value != location) {
                    webappEl.setAttribute(APP_DIR, location)
                    dirty = true
                }

                val charset = webApp.getCharSet()
                if (webappEl.getAttribute(CHARSET) == null || webappEl.getAttribute(CHARSET).value != charset) {
                    if (!charset.isNullOrBlank()) {
                        webappEl.setAttribute(CHARSET, charset)
                        dirty = true
                    }
                }
            }
        }

        if (!webAppFound) {
            dirty = true
            val newWebAppEl = Element(WEB_APP)
            newWebAppEl.setAttribute(ID, webApp.getContextPath())
            newWebAppEl.setAttribute(APP_DIR, webApp.getLocation() ?: "")
            val charset = webApp.getCharSet()
            if (!charset.isNullOrBlank()) {
                newWebAppEl.setAttribute(CHARSET, charset)
            }
            host.addContent(newWebAppEl)
        }

        return dirty
    }

    override fun undeploy(webApp: WebApp): Boolean {
        var dirty = false
        val hosts = getElement().getChild(HTTP_SERVER).getChildren(HOST)
        for (host in hosts) {
            val webapps = host.getChildren(WEB_APP)
            for (webappEl in webapps.toList()) {
                if (webappEl.getAttribute(ID).value == webApp.getContextPath()) {
                    host.removeContent(webappEl)
                    dirty = true
                }
            }
        }
        return dirty
    }

    override fun getDefaultResinConfContent(): InputStream? = javaClass.getResourceAsStream(RESIN_CONF)

    companion object {
        private const val HTTP_SERVER = "http-server"
        private const val HTTP = "http"
        private const val PORT = "port"
        private const val APP_DIR = "app-dir"
        private const val DIRTY = "dirty"
        private const val WEB_APP = "web-app"
        private const val ID = "id"
        private const val HOST = "host"
        private const val RESIN_CONF = "resin2.conf"
        private const val CHARSET = "character-encoding"

        private fun getHost(parent: Element, webApp: WebApp): Element {
            val hosts = parent.getChildren(HOST)
            for (host in hosts) {
                if (host.getAttribute(ID).value == webApp.getHost()) {
                    return host
                }
            }
            val host = Element(HOST)
            host.setAttribute(ID, webApp.getHost())
            host.setAttribute(DIRTY, "true")
            parent.addContent(host)
            return host
        }
    }
}
