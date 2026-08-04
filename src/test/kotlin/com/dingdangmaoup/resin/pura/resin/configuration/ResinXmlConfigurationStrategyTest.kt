package com.dingdangmaoup.resin.pura.resin.configuration

import com.dingdangmaoup.resin.pura.resin.ResinInstallation
import com.intellij.openapi.util.JDOMUtil
import org.jdom.Element
import org.jdom.Namespace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ResinXmlConfigurationStrategyTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

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

    @Test
    fun `directory variable follows the selected custom configuration`() {
        val customDirectory = temporaryFolder.newFolder("custom-conf")

        val resolved = ResinXmlConfigurationStrategy.resolveDirectoryVariable(
            "\${__DIR__}/imports/app-default.xml",
            customDirectory,
        )

        assertEquals(
            File(customDirectory, "imports/app-default.xml").absolutePath.replace(File.separatorChar, '/'),
            resolved,
        )
    }

    @Test
    fun `plain relative import keeps Resin runtime semantics but inspection uses Resin home`() {
        val resinHome = temporaryFolder.newFolder("resin-home")
        val path = "host.xml"

        assertEquals(path, ResinXmlConfigurationStrategy.resolveDirectoryVariable(path, temporaryFolder.root))
        assertEquals(File(resinHome, path), ResinXmlConfigurationStrategy.resolveImportFile(path, resinHome))
    }

    @Test
    fun `uri and unresolved expression imports are not treated as local files`() {
        val resinHome = temporaryFolder.newFolder("uri-resin-home")

        assertEquals(null, ResinXmlConfigurationStrategy.resolveImportFile("classpath:/resin.xml", resinHome))
        assertEquals(null, ResinXmlConfigurationStrategy.resolveImportFile("\${resin.home}/conf/resin.xml", resinHome))
        assertEquals(null, ResinXmlConfigurationStrategy.resolveImportFile("/opt/\${stage}/cluster.xml", resinHome))
    }

    @Test
    fun `nested copied import retains its original directory`() {
        val resinHome = temporaryFolder.newFolder("nested-resin-home")
        File(resinHome, "bin").mkdir()
        File(resinHome, "lib").mkdir()
        val importsDirectory = temporaryFolder.newFolder("imports")
        val sourceFile = File(importsDirectory, "cluster-default.xml")
        val resinNamespace = Namespace.getNamespace("resin", "urn:resin")
        val sourceDocument = Element("cluster-default").addContent(
            Element("import", resinNamespace).setAttribute("path", "\${__DIR__}/nested.xml"),
        )
        JDOMUtil.write(sourceDocument, sourceFile.toPath())
        val importElement = Element("import", resinNamespace).setAttribute("path", sourceFile.absolutePath)

        val configImport = ResinConfigImport(importElement, sourceFile)
        val strategy = ExposedResinXmlConfigurationStrategy(ResinInstallation.create(resinHome.absolutePath))
        strategy.resolveImportedDocument(requireNotNull(configImport.getImportDoc()), configImport.getSourceDirectory())
        configImport.copy()
        configImport.save()

        val copiedFile = File(importElement.getAttributeValue("path"))
        try {
            val copiedDocument = JDOMUtil.load(copiedFile)
            val nestedImport = ResinXmlConfigurationStrategy.findImportElements(copiedDocument).single()
            assertEquals(
                File(importsDirectory, "nested.xml").absolutePath.replace(File.separatorChar, '/'),
                nestedImport.getAttributeValue("path"),
            )
        } finally {
            copiedFile.delete()
        }
    }

    private class ExposedResinXmlConfigurationStrategy(installation: ResinInstallation) :
        ResinXmlConfigurationStrategy(installation) {
        fun resolveImportedDocument(element: Element, sourceDirectory: File) {
            resolveImports(element, null, sourceDirectory)
        }
    }
}
