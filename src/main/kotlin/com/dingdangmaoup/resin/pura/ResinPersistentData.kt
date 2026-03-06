package com.dingdangmaoup.resin.pura

import com.intellij.javaee.appServers.appServerIntegrations.DefaultPersistentData

class ResinPersistentData : DefaultPersistentData() {
    @JvmField
    var RESIN_HOME: String = ""

    @JvmField
    var INCLUDE_ALL_JARS: Boolean = false

    @JvmField
    var RESIN_CONF: String = ""
}
