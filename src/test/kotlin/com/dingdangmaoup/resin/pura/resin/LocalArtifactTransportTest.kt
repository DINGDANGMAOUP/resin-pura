package com.dingdangmaoup.resin.pura.resin

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalArtifactTransportTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun `custom archive and expansion directories are respected`() {
        val archives = temp.newFolder("custom-archives")
        val expanded = temp.newFolder("custom-expanded")
        val source = temp.newFile("app.war").apply { writeText("artifact") }
        val transport = LocalArtifactTransport(archives, expanded, "prefix-", "-suffix")
        assertTrue(transport.transfer(source))
        assertEquals("artifact", archives.resolve("app.war").readText())
        expanded.resolve("prefix-app-suffix").mkdir()
        assertTrue(transport.delete(source.resolveSibling("app")))
        assertFalse(expanded.resolve("prefix-app-suffix").exists())
        assertTrue(transport.delete(source))
        assertTrue(source.exists())
        assertFalse(archives.resolve("app.war").exists())
    }

    @Test fun `source inside managed destination is rejected before cleanup`() {
        val directory = temp.newFolder("webapps")
        val transport = LocalArtifactTransport(directory, directory, "", "")
        val archive = directory.resolve("app.war").apply { writeText("keep") }
        assertFalse(transport.accepts(archive))
        assertFalse(transport.transfer(archive))
        assertFalse(transport.delete(archive))
        val expanded = directory.resolve("app").apply { mkdir() }
        val nested = expanded.resolve("app.war").apply { writeText("keep") }
        assertFalse(transport.accepts(nested))
        assertTrue(archive.exists()); assertTrue(nested.exists())
    }

    @Test fun `missing source cannot produce a successful copy`() {
        val directory = temp.newFolder("target")
        val transport = LocalArtifactTransport(directory, directory, "", "")
        assertFalse(transport.transfer(temp.root.resolve("missing.war")))
        assertTrue(directory.listFiles()!!.isEmpty())
    }

    @Test fun `destination symlinks cannot escape the deployment directory`() {
        val directory = temp.newFolder("target")
        val outside = temp.newFile("outside.war").apply { writeText("keep") }
        val source = temp.newFolder("source").resolve("app.war").apply { writeText("replacement") }
        java.nio.file.Files.createSymbolicLink(directory.resolve("app.war").toPath(), outside.toPath())
        val transport = LocalArtifactTransport(directory, directory, "", "")
        assertFalse(transport.accepts(source))
        assertFalse(transport.transfer(source))
        assertFalse(transport.delete(source))
        assertEquals("keep", outside.readText())
    }
}
