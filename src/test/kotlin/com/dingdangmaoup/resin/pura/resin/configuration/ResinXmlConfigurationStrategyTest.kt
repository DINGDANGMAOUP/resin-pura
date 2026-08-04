package com.dingdangmaoup.resin.pura.resin.configuration

import org.jdom.Element
import org.jdom.Namespace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ResinXmlConfigurationStrategyTest {
    @Test
    fun `import scan preserves xpath descendant and qualified-name semantics`() {
        val resinNamespace = Namespace.getNamespace("resin", "urn:resin")
        val otherNamespace = Namespace.getNamespace("other", "urn:resin")
        val rootImport = Element("import", resinNamespace)
        val nestedImport = Element("import", resinNamespace)
        val nestedProperties = Element("properties", resinNamespace)
        val wrongPrefix = Element("import", otherNamespace)
        rootImport.addContent(
            Element("wrapper").addContent(nestedImport).addContent(nestedProperties).addContent(wrongPrefix),
        )

        val elements = ResinXmlConfigurationStrategy.findImportElements(rootImport)

        assertEquals(2, elements.size)
        assertSame(nestedImport, elements[0])
        assertSame(nestedProperties, elements[1])
    }
}
