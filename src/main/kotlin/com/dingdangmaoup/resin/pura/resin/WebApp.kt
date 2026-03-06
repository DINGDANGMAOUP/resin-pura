package com.dingdangmaoup.resin.pura.resin

import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.util.PathUtil

class WebApp(
    private val myDefaultContextPath: Boolean,
    private var myContextPath: String?,
    private var myHost: String?,
    private var myLocation: String?,
    private var myCharset: String?,
) {
    fun getLocation(): String? = myLocation

    fun setLocation(location: String?) {
        myLocation = location
    }

    fun getContextPath(): String {
        return if (myDefaultContextPath) {
            "/" + FileUtilRt.getNameWithoutExtension(PathUtil.getFileName(myLocation ?: ""))
        } else {
            myContextPath ?: ""
        }
    }

    fun setContextPath(contextPath: String?) {
        myContextPath = contextPath
    }

    fun getHost(): String = myHost ?: ""

    fun setHost(host: String?) {
        myHost = host
    }

    fun getCharSet(): String? = myCharset

    fun setCharSet(charset: String?) {
        myCharset = charset
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WebApp) return false
        if (myContextPath != other.myContextPath) return false
        if (myHost != other.myHost) return false
        if (myLocation != other.myLocation) return false
        if (myDefaultContextPath != other.myDefaultContextPath) return false
        return true
    }

    override fun hashCode(): Int {
        var result = myLocation?.hashCode() ?: 0
        result = 29 * result + (myContextPath?.hashCode() ?: 0)
        result = 29 * result + (myHost?.hashCode() ?: 0)
        result = 29 * result + if (myDefaultContextPath) 1 else 0
        return result
    }
}
