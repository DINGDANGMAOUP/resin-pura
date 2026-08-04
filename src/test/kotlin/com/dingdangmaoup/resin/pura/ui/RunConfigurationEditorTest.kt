package com.dingdangmaoup.resin.pura.ui

import com.intellij.openapi.options.ConfigurationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import javax.swing.JTextField

class RunConfigurationEditorTest {
    @Test
    fun `port parser accepts only TCP port range`() {
        assertEquals(1, parsePort("1"))
        assertEquals(65535, parsePort("65535"))
        assertThrows(ConfigurationException::class.java) { parsePort("0") }
        assertThrows(ConfigurationException::class.java) { parsePort("65536") }
        assertThrows(ConfigurationException::class.java) { parsePort("not-a-port") }
    }

    @Test
    fun `charset parser normalizes valid names and rejects invalid names`() {
        assertEquals("UTF-8", RunConfigurationEditor.parseCharset(" UTF-8 "))
        assertEquals("", RunConfigurationEditor.parseCharset("  "))
        assertThrows(ConfigurationException::class.java) {
            RunConfigurationEditor.parseCharset("not a charset")
        }
    }

    private fun parsePort(value: String): Int = RunConfigurationEditor.parseInt(
        JTextField(value),
        "run.config.dlg.http.port.error",
    )
}
