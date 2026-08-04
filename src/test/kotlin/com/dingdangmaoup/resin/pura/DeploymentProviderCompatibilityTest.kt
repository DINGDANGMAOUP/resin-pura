package com.dingdangmaoup.resin.pura

import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class DeploymentProviderCompatibilityTest {
    @Test
    fun `integration exposes one provider for local and remote models`() {
        val manager = ResinManager()

        assertSame(manager.getDeploymentProvider(true), manager.getDeploymentProvider(false))
    }

    @Test
    fun `server model keeps the required nullable ABI bridge`() {
        assertNull(ResinModel().getDeploymentProvider())
    }
}
