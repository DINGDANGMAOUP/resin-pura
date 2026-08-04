package com.dingdangmaoup.resin.pura

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.ParametersList
import com.intellij.javaee.jmxremote.JmxRemotePrepareResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ResinStartupPolicyTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `whitespace restriction matches hierarchical version wildcard`() {
        val invalidVersions = listOf("2.x", "3.1.x")

        assertFalse(ResinStartupPolicy.isWhitespacePathSupported("3.1.13", invalidVersions))
        assertFalse(ResinStartupPolicy.isWhitespacePathSupported("2.0.0", invalidVersions))
        assertTrue(ResinStartupPolicy.isWhitespacePathSupported("3.2.0", invalidVersions))
        assertTrue(ResinStartupPolicy.isWhitespacePathSupported("4.0.0", invalidVersions))
    }

    @Test
    fun `whitespace restriction still honors an exact version`() {
        assertFalse(ResinStartupPolicy.isWhitespacePathSupported("3.1.3", listOf("3.1.3")))
        assertTrue(ResinStartupPolicy.isWhitespacePathSupported("3.1.4", listOf("3.1.3")))
    }

    @Test
    fun `jmx security properties replace every malicious resin property override`() {
        val accessFile = temporaryFolder.newFile("jmxremote.access")
        val passwordFile = temporaryFolder.newFile("jmxremote.password")
        val prepareResult = JmxRemotePrepareResult("resin", "secret", accessFile, passwordFile)
        val vmParameters = ParametersList().apply {
            add("-Xmx512m")
            add("-Dcom.sun.management.jmxremote.port=4444")
            add("-Dcom.sun.management.jmxremote.port=5555")
            add("-Dcom.sun.management.jmxremote.ssl=true")
            add("-Dcom.sun.management.jmxremote.ssl=true")
            add("-Dcom.sun.management.jmxremote.authenticate=false")
            add("-Dcom.sun.management.jmxremote.authenticate=false")
            add("-Dcom.sun.management.jmxremote.password.file=/tmp/attacker.password")
            add("-Dcom.sun.management.jmxremote.password.file=/tmp/attacker-2.password")
            add("-Dcom.sun.management.jmxremote.access.file=/tmp/attacker.access")
            add("-Dcom.sun.management.jmxremote.access.file=/tmp/attacker-2.access")
            add("-Dcom.sun.management.jmxremote.host=0.0.0.0")
            add("-Dcom.sun.management.jmxremote.host=192.0.2.1")
            add("-Djava.rmi.server.hostname=public.example")
            add("-Djava.rmi.server.hostname=public-2.example")
            add("-Djavax.management.builder.initial=attacker.Builder")
            add("-Dcom.sun.management.jmxremote.local.only=false")
        }
        val programParameters = ParametersList().apply {
            add("-server")
            add("app")
            add("-jmx-port")
            add("4444")
            add("--jmx-port=5555")
            add("-J-Dcom.sun.management.jmxremote.authenticate=false")
            add("-Dcom.sun.management.jmxremote.host=0.0.0.0")
            add("-J-Dcom.sun.management.jmxremote.local.only=false")
            add("-J-Djava.rmi.server.hostname=public.example")
            add("-J-Djavax.management.builder.initial=attacker.Builder")
            add("-verbose")
        }

        ResinStartupPolicy.enforceJmxSecurity(vmParameters, programParameters, 9999, prepareResult)

        val expected = mapOf(
            "com.sun.management.jmxremote.port" to "9999",
            "com.sun.management.jmxremote.ssl" to "false",
            "com.sun.management.jmxremote.authenticate" to "true",
            "com.sun.management.jmxremote.password.file" to passwordFile.canonicalPath,
            "com.sun.management.jmxremote.access.file" to accessFile.canonicalPath,
            "com.sun.management.jmxremote.host" to "127.0.0.1",
            "java.rmi.server.hostname" to "127.0.0.1",
        )
        for ((name, value) in expected) {
            assertEquals(value, vmParameters.getPropertyValue(name))
            val definitions = vmParameters.parameters.filter { it == "-D$name" || it.startsWith("-D$name=") }
            assertEquals(listOf("-D$name=$value"), definitions)
            assertEquals(listOf("-J-D$name=$value"), programParameters.parameters.filter { it.startsWith("-J-D$name") })
        }
        assertTrue(vmParameters.parameters.contains("-Xmx512m"))
        assertEquals(1, vmParameters.parameters.count { it == "-Dcom.sun.management.jmxremote" })
        assertEquals(
            1,
            vmParameters.parameters.count {
                it == "-Djavax.management.builder.initial=com.caucho.jmx.MBeanServerBuilderImpl"
            },
        )
        assertTrue(programParameters.parameters.containsAll(listOf("-server", "app", "-verbose")))
        assertFalse(programParameters.parameters.any { it == "-jmx-port" || it.startsWith("--jmx-port") })
        assertFalse(programParameters.parameters.any { it.contains("attacker") || it.contains("local.only") })
        assertEquals(1, programParameters.parameters.count { it == "-J-Dcom.sun.management.jmxremote" })
        assertEquals(
            1,
            programParameters.parameters.count {
                it == "-J-Djavax.management.builder.initial=com.caucho.jmx.MBeanServerBuilderImpl"
            },
        )
    }

    @Test
    fun `jmx security properties reject an invalid port`() {
        val accessFile = temporaryFolder.newFile("invalid-port.access")
        val passwordFile = temporaryFolder.newFile("invalid-port.password")
        val prepareResult = JmxRemotePrepareResult("resin", "secret", accessFile, passwordFile)

        assertThrows(ExecutionException::class.java) {
            ResinStartupPolicy.enforceJmxSecurity(ParametersList(), ParametersList(), 0, prepareResult)
        }
    }

    @Test
    fun `final command conversion removes vm overrides appended after startup policy`() {
        val accessFile = temporaryFolder.newFile("final.access")
        val passwordFile = temporaryFolder.newFile("final.password")
        val prepareResult = JmxRemotePrepareResult("resin", "secret", accessFile, passwordFile)
        val parameters = ResinStartupPolicy.SecureJmxJavaParameters(9999, prepareResult).apply {
            mainClass = "com.example.Main"
            vmParametersList.add("-Dcom.sun.management.jmxremote.authenticate=false")
            vmParametersList.add("-Dcom.sun.management.jmxremote.host=0.0.0.0")
            programParametersList.add("-jmx-port")
            programParametersList.add("4444")
        }

        runCatching { parameters.toCommandLine() }

        assertEquals("true", parameters.vmParametersList.getPropertyValue("com.sun.management.jmxremote.authenticate"))
        assertEquals("127.0.0.1", parameters.vmParametersList.getPropertyValue("com.sun.management.jmxremote.host"))
        assertFalse(parameters.programParametersList.parameters.contains("-jmx-port"))
        assertTrue(
            parameters.programParametersList.parameters.contains(
                "-J-Dcom.sun.management.jmxremote.authenticate=true",
            ),
        )
    }

    @Test
    fun `dangling resin jmx port flag does not consume the next option`() {
        val accessFile = temporaryFolder.newFile("dangling.access")
        val passwordFile = temporaryFolder.newFile("dangling.password")
        val prepareResult = JmxRemotePrepareResult("resin", "secret", accessFile, passwordFile)
        val vmParameters = ParametersList()
        val programParameters = ParametersList().apply {
            add("-jmx-port")
            add("--server")
            add("app")
        }

        ResinStartupPolicy.enforceJmxSecurity(vmParameters, programParameters, 9999, prepareResult)

        assertFalse(programParameters.parameters.contains("-jmx-port"))
        assertTrue(programParameters.parameters.contains("--server"))
        assertTrue(programParameters.parameters.contains("app"))
    }
}
