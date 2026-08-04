package com.dingdangmaoup.resin.pura.resin.version

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.jar.Manifest

class ResinVersionDetectionTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `class initializer version detection never initializes Resin classes`() {
        System.clearProperty(STATIC_INITIALIZER_PROPERTY)
        try {
            val home = createResinHome()
            createResinJar(home)

            val detected = ClassCallDetector.getResinVersion(home)

            assertEquals("3.1.13", detected?.getVersionNumber())
            assertTrue(detected?.allowXdebug() == true)
            assertTrue(detected?.allowJmx() == true)
            assertFalse(
                "Resin class static initializer must not run in the IDE",
                System.getProperties().containsKey(STATIC_INITIALIZER_PROPERTY),
            )
        } finally {
            System.clearProperty(STATIC_INITIALIZER_PROPERTY)
        }
    }

    @Test
    fun `class version takes precedence over a stale manifest version`() {
        System.clearProperty(STATIC_INITIALIZER_PROPERTY)
        try {
            val home = createResinHome()
            createResinJar(home, implementationVersion = "3.1.0")

            val detected = ClassCallDetector.getResinVersion(home)

            assertEquals("3.1.13", detected?.getVersionNumber())
            assertFalse(
                "Class-file inspection must not initialize Resin classes",
                System.getProperties().containsKey(STATIC_INITIALIZER_PROPERTY),
            )
        } finally {
            System.clearProperty(STATIC_INITIALIZER_PROPERTY)
        }
    }

    @Test
    fun `manifest version is used when the version class is absent`() {
        val home = createResinHome()
        createResinJar(home, implementationVersion = "4.0.66", includeVersionClass = false)

        val detected = ClassCallDetector.getResinVersion(home)

        assertEquals("4.0.66", detected?.getVersionNumber())
    }

    @Test
    fun `manifest version is used when the version class is malformed`() {
        val home = createResinHome()
        createResinJar(
            home,
            implementationVersion = "4.0.66",
            versionClassBytes = byteArrayOf(0x13, 0x37),
        )

        val detected = ClassCallDetector.getResinVersion(home)

        assertEquals("4.0.66", detected?.getVersionNumber())
    }

    @Test
    fun `corrupt Resin jar safely falls back to unknown version`() {
        val home = createResinHome()
        Files.write(home.toPath().resolve("lib/resin.jar"), byteArrayOf(0x13, 0x37))

        assertSame(ResinVersion.UNKNOWN_VERSION, ResinVersionDetector.getResinVersion(home))
    }

    @Test
    fun `startup class mappings are safely shared by concurrent version detection`() {
        val executor = Executors.newFixedThreadPool(8)
        try {
            val tasks = List(64) {
                Callable { StartupClassFinder.getStartupClassForVersion("4.0.66") }
            }

            val results = executor.invokeAll(tasks).map { it.get() }

            assertTrue(results.all { it == "com.caucho.server.resin.Resin" })
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `resin libraries have deterministic classpath order`() {
        val lib = temporaryFolder.newFolder("ordered-libs")
        listOf("z-last.jar", "a-first.jar", "middle.jar").forEach { lib.resolve(it).createNewFile() }

        assertEquals(
            listOf("a-first.jar", "middle.jar", "z-last.jar"),
            ResinLibCollector.getLibFiles(lib, true).map { it.name },
        )
    }

    private fun createResinHome() = temporaryFolder.newFolder("resin-${System.nanoTime()}").apply {
        resolve("bin").mkdir()
        resolve("lib").mkdir()
    }

    private fun createResinJar(
        home: java.io.File,
        implementationVersion: String? = null,
        includeVersionClass: Boolean = true,
        versionClassBytes: ByteArray? = null,
    ) {
        val manifest = implementationVersion?.let { version ->
            Manifest().apply {
                mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
                mainAttributes.putValue("Implementation-Version", version)
            }
        }
        val jarPath = home.toPath().resolve("lib/resin.jar")
        val output = Files.newOutputStream(jarPath)
        val jar = if (manifest == null) JarOutputStream(output) else JarOutputStream(output, manifest)
        jar.use {
            if (includeVersionClass) {
                it.putNextEntry(JarEntry("com/caucho/Version.class"))
                if (versionClassBytes == null) {
                    checkNotNull(javaClass.classLoader.getResourceAsStream("com/caucho/Version.class")).use { fixture ->
                        fixture.copyTo(it)
                    }
                } else {
                    it.write(versionClassBytes)
                }
                it.closeEntry()
            }
            for (capabilityClass in CAPABILITY_CLASS_ENTRIES) {
                it.putNextEntry(JarEntry(capabilityClass))
                it.write(byteArrayOf(0)) // Entry presence is enough; detector must never define this class.
                it.closeEntry()
            }
        }
    }

    companion object {
        private const val STATIC_INITIALIZER_PROPERTY = "resin.pura.test.version-class-initialized"
        private val CAPABILITY_CLASS_ENTRIES = listOf(
            "com/caucho/log/LogManagerImpl.class",
            "com/caucho/jmx/MBeanServerBuilderImpl.class",
        )
    }
}
