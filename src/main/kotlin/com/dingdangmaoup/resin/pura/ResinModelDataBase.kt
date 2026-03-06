package com.dingdangmaoup.resin.pura

import com.intellij.util.xmlb.annotations.Tag

open class ResinModelDataBase {
    @field:Tag("port")
    var port: Int = ResinUtil.DEFAULT_PORT

    @field:Tag("mbean-port")
    var jmxPort: Int = 9999 // TODO: move to const

    var charset: String = ""
}
