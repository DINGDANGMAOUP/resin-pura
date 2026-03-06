package com.dingdangmaoup.resin.pura.resin.common

import com.intellij.openapi.options.ConfigurationException
import javax.swing.JTextField

abstract class ParseUtil {
    @Throws(ConfigurationException::class)
    fun parseInt(text: JTextField): Int {
        val toParse = (text.text ?: "").trim()
        return try {
            toParse.toInt()
        } catch (_: NumberFormatException) {
            throw ConfigurationException(getErrorMessage(toParse))
        }
    }

    protected abstract fun getErrorMessage(unparsableValue: String): String
}
