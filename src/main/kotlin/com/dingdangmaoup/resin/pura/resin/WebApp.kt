package com.dingdangmaoup.resin.pura.resin

import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.util.PathUtil

class WebApp(
    private val myDefaultContextPath: Boolean,
    private val myContextPath: String?,
    private val myHost: String?,
    private val myLocation: String?,
    private val myCharset: String?,
) {
    fun getLocation(): String? = myLocation

    fun getContextPath(): String {
        return if (myDefaultContextPath) {
            val name = FileUtilRt.getNameWithoutExtension(PathUtil.getFileName(myLocation ?: ""))
            if (name.equals("ROOT", ignoreCase = true)) "/" else "/$name"
        } else {
            myContextPath ?: ""
        }
    }

    fun getHost(): String = myHost ?: ""

    fun getCharSet(): String? = myCharset

    fun target(): DeploymentTarget = DeploymentTarget(getHost(), getContextPath())

    fun usesDefaultContext(): Boolean = myDefaultContextPath

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
