package com.dingdangmaoup.resin.pura

import java.nio.charset.Charset
import java.nio.charset.IllegalCharsetNameException

object ResinUtil {
    const val DEFAULT_PORT: Int = 8080
    const val DEFAULT_JMX_PORT: Int = 9999
    internal const val LEGACY_DEFAULT_PORT: Int = 80

    internal fun isValidPort(port: Int): Boolean = port in 1..65535

    internal fun isValidCharset(charset: String): Boolean {
        if (charset.isEmpty()) return true
        return try {
            Charset.isSupported(charset)
        } catch (_: IllegalCharsetNameException) {
            false
        }
    }
}
