package com.dingdangmaoup.resin.pura

import com.intellij.util.xmlb.annotations.Tag

open class ResinModelDataBase {
    @field:Tag("port")
    var port: Int = ResinUtil.LEGACY_DEFAULT_PORT

    @field:Tag("mbean-port")
    var jmxPort: Int = ResinUtil.DEFAULT_JMX_PORT

    var charset: String = ""
}
