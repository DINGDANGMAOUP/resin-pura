package com.dingdangmaoup.resin.pura.resin.jmx

import com.dingdangmaoup.resin.pura.resin.DeploymentObservation
import com.dingdangmaoup.resin.pura.resin.DeploymentTarget
import com.dingdangmaoup.resin.pura.resin.DeploymentWait
import com.dingdangmaoup.resin.pura.resin.WebApp
import com.intellij.javaee.appServers.deployment.DeploymentStatus
import org.junit.Assert.*
import org.junit.Test
import javax.management.ObjectName

class DeploymentObservationTest {
    @Test fun `only an active application confirms successful deployment`() {
        val active = JmxDeploymentClient.observeState(JmxDeploymentClient.WebAppStateResult.Found("active"), false)
        assertEquals(DeploymentStatus.DEPLOYED, active.status)
        assertTrue(active.terminal)
        for (state in listOf(null, JmxDeploymentClient.WebAppStateResult.Missing, JmxDeploymentClient.WebAppStateResult.Found("starting"))) {
            val result = JmxDeploymentClient.observeState(state, false)
            assertEquals(DeploymentStatus.UNKNOWN, result.status)
            assertFalse(result.terminal)
        }
        assertEquals(DeploymentStatus.FAILED, JmxDeploymentClient.observeState(JmxDeploymentClient.WebAppStateResult.Found("error"), false).status)
    }

    @Test fun `unavailable connection is not confirmation of undeployment`() {
        assertEquals(DeploymentStatus.UNKNOWN, JmxDeploymentClient.observeState(null, true).status)
        assertEquals(DeploymentStatus.UNKNOWN, JmxDeploymentClient.observeState(JmxDeploymentClient.WebAppStateResult.Found("active"), true).status)
        val missing = JmxDeploymentClient.observeState(JmxDeploymentClient.WebAppStateResult.Missing, true)
        assertEquals(DeploymentStatus.NOT_DEPLOYED, missing.status)
        assertTrue(missing.terminal)
    }

    @Test fun `unknown deployment states have a finite polling lifetime`() {
        val wait = DeploymentWait(100)
        val unknown = DeploymentObservation(DeploymentStatus.UNKNOWN, false)
        assertFalse(wait.finish(unknown, 101))
        assertTrue(wait.finish(unknown, 100 + DeploymentWait.TIMEOUT_NANOS))
        assertTrue(wait.finish(DeploymentObservation(DeploymentStatus.DEPLOYED, true), 101))
    }

    @Test fun `configuration observation includes the exact host and context`() {
        val objectName = JmxDeploymentClient.createApplicationObjectName(DeploymentTarget("a,b.example", "/custom?context"))
        assertFalse(objectName.isPattern)
        assertEquals("a,b.example", ObjectName.unquote(objectName.getKeyProperty("Host")))
        assertEquals("/custom?context", ObjectName.unquote(objectName.getKeyProperty("name")))
    }

    @Test fun `archive capability validation rejects ignored custom settings`() {
        assertNotNull(JmxDeploymentClient.archiveTargetError(WebApp(true, "/", "other.example", "app.war", null), "app"))
        assertNotNull(JmxDeploymentClient.archiveTargetError(WebApp(false, "/custom", "", "app.war", null), "app"))
        assertNull(JmxDeploymentClient.archiveTargetError(WebApp(false, "/app", "", "app.war", null), "app"))
        assertNull(JmxDeploymentClient.archiveTargetError(WebApp(false, "/", "default", "ROOT.war", null), "ROOT"))
        assertEquals("/", WebApp(true, "/ignored", "", "ROOT.war", null).getContextPath())
    }
}
