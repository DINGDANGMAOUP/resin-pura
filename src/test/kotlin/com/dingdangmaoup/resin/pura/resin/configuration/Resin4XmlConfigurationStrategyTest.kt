package com.dingdangmaoup.resin.pura.resin.configuration

import com.intellij.execution.ExecutionException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Properties

class Resin4XmlConfigurationStrategyTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `jmx arguments require authentication and bind to loopback`() {
        val accessFile = temporaryFolder.newFile("jmxremote.access")
        val passwordFile = temporaryFolder.newFile("jmxremote.password")

        val arguments = Resin4XmlConfigurationStrategy.buildJmxJvmArgs(
            9999,
            accessFile,
            passwordFile,
        ).toMap()

        assertEquals("9999", arguments["-Dcom.sun.management.jmxremote.port"])
        assertEquals("true", arguments["-Dcom.sun.management.jmxremote.authenticate"])
        assertEquals(accessFile.canonicalPath, arguments["-Dcom.sun.management.jmxremote.access.file"])
        assertEquals(passwordFile.canonicalPath, arguments["-Dcom.sun.management.jmxremote.password.file"])
        assertEquals("127.0.0.1", arguments["-Dcom.sun.management.jmxremote.host"])
        assertEquals("127.0.0.1", arguments["-Djava.rmi.server.hostname"])
    }

    @Test
    fun `jmx arguments fail closed when credentials are absent`() {
        val passwordFile = temporaryFolder.newFile("jmxremote.password")

        assertThrows(ExecutionException::class.java) {
            Resin4XmlConfigurationStrategy.buildJmxJvmArgs(9999, null, passwordFile)
        }
    }

    @Test
    fun `jmx arguments fail closed when a credential file does not exist`() {
        val accessFile = temporaryFolder.newFile("jmxremote.access")
        val missingPasswordFile = File(temporaryFolder.root, "missing.password")

        assertThrows(ExecutionException::class.java) {
            Resin4XmlConfigurationStrategy.buildJmxJvmArgs(9999, accessFile, missingPasswordFile)
        }
    }

    @Test
    fun `jmx arguments reject ports outside the TCP range`() {
        val accessFile = temporaryFolder.newFile("jmxremote.access")
        val passwordFile = temporaryFolder.newFile("jmxremote.password")

        assertThrows(ExecutionException::class.java) {
            Resin4XmlConfigurationStrategy.buildJmxJvmArgs(0, accessFile, passwordFile)
        }
        assertThrows(ExecutionException::class.java) {
            Resin4XmlConfigurationStrategy.buildJmxJvmArgs(65536, accessFile, passwordFile)
        }
    }

    @Test
    fun `startup jmx arguments also require authentication and loopback`() {
        val properties = Properties()
        javaClass.getResourceAsStream("/com/dingdangmaoup/resin/pura/ResinRun.properties").use { stream ->
            requireNotNull(stream)
            properties.load(stream)
        }

        val arguments = properties.getProperty("resin.jmx.vm.param").split(' ')

        assertTrue(arguments.contains("-Dcom.sun.management.jmxremote.authenticate=true"))
        assertTrue(arguments.contains("-Dcom.sun.management.jmxremote.host=127.0.0.1"))
        assertTrue(arguments.contains("-Djava.rmi.server.hostname=127.0.0.1"))
        assertTrue(arguments.none { it == "-Dcom.sun.management.jmxremote.authenticate=false" })
    }
}
