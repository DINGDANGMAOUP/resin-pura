package com.dingdangmaoup.resin.pura.resin

import com.dingdangmaoup.resin.pura.ResinModel
import com.dingdangmaoup.resin.pura.ResinStartupPolicy
import com.intellij.execution.ExecutionException
import com.intellij.openapi.util.JDOMUtil
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ResinRunSessionTest {
    @get:Rule val temp = TemporaryFolder()
    private val original = "<resin><http-server><host id=\"a.example\"/></http-server></resin>"

    @Test fun `undeploy then redeploy restores the generated config without touching its source`() {
        val source = temp.newFile("resin.conf").apply { writeText(original) }
        val model = resinTestModel(temp.newFolder("home"), source)
        val artifact = temp.newFile("app.war")
        val app = WebApp(false, "/app", "a.example", artifact.path, null)
        val configuration = model.getOrCreateResinConfiguration(false)
        val generated = configuration.getConfigFile()
        fun appCount() = JDOMUtil.load(generated).getChild("http-server").getChild("host").getChildren("web-app").size
        configuration.deploy(app)
        assertEquals(1, appCount())
        assertTrue(configuration.undeploy(app))
        assertEquals(0, appCount())
        assertTrue(configuration.undeploy(app))
        configuration.deploy(app)
        assertEquals(1, appCount())
        assertEquals(original, source.readText())
        model.runSession.close()
        model.runSession.close()
        assertFalse(generated.exists())
        assertTrue(source.exists())
    }

    @Test fun `read only config rejects mutation and cleanup preserves source`() {
        val source = temp.newFile("read-only.conf").apply { writeText(original) }
        val model = resinTestModel(temp.newFolder("home"), source).apply { setReadOnlyConfiguration(true) }
        val configuration = model.getOrCreateResinConfiguration(false)
        val app = WebApp(false, "/app", "a.example", temp.newFile("app.war").path, null)
        assertThrows(ExecutionException::class.java) { configuration.deploy(app) }
        assertThrows(ExecutionException::class.java) { configuration.undeploy(app) }
        model.runSession.close()
        assertEquals(original, source.readText())
    }

    @Test fun `snapshot has no session resources and cannot close a running configuration`() {
        val source = temp.newFile("resin.conf").apply { writeText(original) }
        val model = resinTestModel(temp.newFolder("home"), source)
        val configuration = model.getOrCreateResinConfiguration(false)
        model.setJmxUsername("admin"); model.setJmxPassword("secret")
        val snapshot = model.clone() as ResinModel
        assertNotSame(model.runSession, snapshot.runSession)
        assertNull(snapshot.runSession.configuration)
        assertNull(snapshot.getJmxUsername()); assertNull(snapshot.getJmxPassword())
        snapshot.runSession.close()
        assertTrue(configuration.getConfigFile().exists())
        model.runSession.close()
    }

    @Test fun `new launch replaces old resources and stale cleanup cannot close the new session`() {
        val source = temp.newFile("resin.conf").apply { writeText(original) }
        val model = resinTestModel(temp.newFolder("home"), source)
        val oldFile = model.getOrCreateResinConfiguration(false).getConfigFile()
        val oldSession = model.runSession
        model.beginRunSession()
        assertFalse(oldFile.exists())
        val newFile = model.getOrCreateResinConfiguration(false).getConfigFile()
        oldSession.close()
        assertTrue(newFile.exists())
        assertFalse(model.runSession.isClosed)
        model.runSession.close()
    }

    @Test fun `command preparation failure removes generated files and permits retry`() {
        val source = temp.newFile("resin.conf").apply { writeText(original) }
        lateinit var model: ResinModel
        var generated: File? = null
        model = resinTestModel(temp.newFolder("home"), source) {
            generated = model.runSession.configuration?.getConfigFile()
            error("Simulated library resolution failure")
        }
        repeat(2) {
            assertThrows(IllegalStateException::class.java) { ResinStartupPolicy().createCommandLine(model.getCommonModel()) }
            assertNotNull(generated)
            assertFalse(requireNotNull(generated).exists())
            assertTrue(model.runSession.isClosed)
        }
        assertEquals(original, source.readText())
    }
    @Test fun `process exit releases configuration without waiting for IDE shutdown`() {
        val source = temp.newFile("resin.conf").apply { writeText(original) }
        val model = resinTestModel(temp.newFolder("home"), source)
        val generated = model.getOrCreateResinConfiguration(false).getConfigFile()
        val process = ResinStartupPolicy.SessionCommandLine(
            com.intellij.execution.configurations.GeneralCommandLine(File(System.getProperty("java.home"), "bin/java").path, "-version"),
            model.runSession,
        ).createProcess()
        try {
            assertTrue(process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS))
            val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5)
            while (generated.exists() && System.nanoTime() < deadline) Thread.sleep(10)
            assertFalse(generated.exists())
            assertTrue(model.runSession.isClosed)
            assertEquals(original, source.readText())
        } finally {
            process.destroyForcibly()
            model.runSession.close()
        }
    }

    @Test fun `a live process prevents replacement of its session`() {
        val source = temp.newFile("resin.conf").apply { writeText(original) }
        val model = resinTestModel(temp.newFolder("home"), source)
        val generated = model.getOrCreateResinConfiguration(false).getConfigFile()
        val process = object : Process() {
            override fun isAlive() = true
            override fun onExit() = java.util.concurrent.CompletableFuture<Process>()
            override fun getOutputStream() = java.io.ByteArrayOutputStream()
            override fun getInputStream() = java.io.ByteArrayInputStream(byteArrayOf())
            override fun getErrorStream() = java.io.ByteArrayInputStream(byteArrayOf())
            override fun waitFor() = 0
            override fun exitValue() = 0
            override fun destroy() {}
        }
        model.runSession.own(process)
        assertThrows(ExecutionException::class.java) { model.beginRunSession() }
        assertTrue(generated.exists())
        assertFalse(model.runSession.isClosed)
        model.runSession.close()
    }

    @Test fun `OS process creation failure closes the prepared session`() {
        val source = temp.newFile("resin.conf").apply { writeText(original) }
        val model = resinTestModel(temp.newFolder("home"), source)
        val generated = model.getOrCreateResinConfiguration(false).getConfigFile()
        val command = ResinStartupPolicy.SessionCommandLine(
            com.intellij.execution.configurations.GeneralCommandLine(temp.root.resolve("missing-java").path), model.runSession,
        )
        assertThrows(ExecutionException::class.java) { command.createProcess() }
        assertTrue(model.runSession.isClosed)
        assertFalse(generated.exists())
    }

}
