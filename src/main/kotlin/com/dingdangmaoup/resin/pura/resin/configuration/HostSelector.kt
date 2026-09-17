package com.dingdangmaoup.resin.pura.resin.configuration

import org.jdom.Element

/** Uses the same host identity for configuration deployment and removal. */
internal object HostSelector {
    fun find(hosts: List<Element>, hostName: String, allowRegexp: Boolean = true): Element? {
        // An explicit virtual host takes precedence over a catch-all regexp host.
        return hosts.firstOrNull { it.getAttributeValue("id") == hostName }
            ?: if (allowRegexp) hosts.firstOrNull {
                it.getAttributeValue("regexp")?.toRegex()?.matches(hostName) == true
            } else null
    }
}
