package com.dingdangmaoup.resin.pura.resin

import com.dingdangmaoup.resin.pura.ResinModelDataBase
import com.dingdangmaoup.resin.pura.ResinUtil
import org.jdom.Element
import org.junit.Assert.assertEquals
import org.junit.Test

class ResinConfigurationTest {
    @Test
    fun `debug patch creates missing javac args`() {
        val root = Element("resin")
        root.addContent(Element("javac").setAttribute("compiler", "internal"))

        ResinConfiguration.patchConfigToMakeDebuggerWork(root)

        assertEquals("-g -source 1.5", root.getChild("javac").getAttributeValue("args"))
    }

    @Test
    fun `debug patch preserves existing args and is idempotent`() {
        val root = Element("resin")
        root.addContent(Element("javac").setAttribute("args", "-source 8"))

        ResinConfiguration.patchConfigToMakeDebuggerWork(root)
        ResinConfiguration.patchConfigToMakeDebuggerWork(root)

        assertEquals("-g -source 8", root.getChild("javac").getAttributeValue("args"))
    }

    @Test
    fun `debug patch replaces disabled symbols without mistaking similar options`() {
        val disabled = Element("resin")
        disabled.addContent(Element("javac").setAttribute("args", "-g:none -source 8"))
        val similar = Element("resin")
        similar.addContent(Element("javac").setAttribute("args", "-generate -source 8"))

        ResinConfiguration.patchConfigToMakeDebuggerWork(disabled)
        ResinConfiguration.patchConfigToMakeDebuggerWork(similar)

        assertEquals("-g -source 8", disabled.getChild("javac").getAttributeValue("args"))
        assertEquals("-g -generate -source 8", similar.getChild("javac").getAttributeValue("args"))
    }

    @Test
    fun `debug patch normalizes an explicitly empty args attribute`() {
        val root = Element("resin")
        root.addContent(Element("javac").setAttribute("args", "  "))

        ResinConfiguration.patchConfigToMakeDebuggerWork(root)

        assertEquals("-g", root.getChild("javac").getAttributeValue("args"))
    }

    @Test
    fun `new configuration default is 8080 while legacy data keeps port 80`() {
        assertEquals(8080, ResinUtil.DEFAULT_PORT)
        assertEquals(80, ResinModelDataBase().port)
    }
}
