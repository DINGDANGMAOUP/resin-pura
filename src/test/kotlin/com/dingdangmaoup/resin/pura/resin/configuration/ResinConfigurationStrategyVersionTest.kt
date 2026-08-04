package com.dingdangmaoup.resin.pura.resin.configuration

import com.dingdangmaoup.resin.pura.resin.ResinInstallation
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ResinConfigurationStrategyVersionTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `Resin 2 fallback selects Resin 2 strategy`() {
        val installation = createInstallation("jsdk23.jar")

        assertTrue(ResinConfigurationStrategy.getForInstallation(installation) is Resin2XConfigurationStrategy)
    }

    @Test
    fun `Resin 3 fallback selects legacy Resin 3 strategy`() {
        val installation = createInstallation("jsdk-24.jar")

        assertTrue(ResinConfigurationStrategy.getForInstallation(installation) is Resin3XConfigurationStrategy)
    }

    @Test
    fun `unknown fallback selects compatibility strategy without throwing`() {
        val installation = createInstallation()

        assertTrue(ResinConfigurationStrategy.getForInstallation(installation) is Resin3XConfigurationStrategy)
    }

    private fun createInstallation(markerJar: String? = null): ResinInstallation {
        val home = temporaryFolder.newFolder("resin-${System.nanoTime()}")
        home.resolve("bin").mkdir()
        val lib = home.resolve("lib").apply { mkdir() }
        markerJar?.let { lib.resolve(it).createNewFile() }
        return ResinInstallation.create(home.absolutePath)
    }
}
