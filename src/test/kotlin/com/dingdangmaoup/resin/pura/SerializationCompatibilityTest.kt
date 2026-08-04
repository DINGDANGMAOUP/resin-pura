package com.dingdangmaoup.resin.pura

import com.intellij.configurationStore.serializeObjectInto
import com.intellij.util.xmlb.XmlSerializer
import org.jdom.Element
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SerializationCompatibilityTest {
    @Test
    fun `server model serialization omits defaults and round-trips state`() {
        val defaultState = Element("state")
        ResinModel().writeExternal(defaultState)
        assertTrue(defaultState.attributes.isEmpty())
        assertTrue(defaultState.children.isEmpty())

        val source = ResinModel().apply {
            port = 9090
            jmxPort = 1099
            charset = "UTF-8"
            setResinConf("conf/resin.xml")
            setDebugConfiguration(true)
            setDeployMode(ResinModel.DEPLOY_MODE_LAZY)
        }
        val serialized = Element("state")
        source.writeExternal(serialized)

        val restored = ResinModel()
        restored.readExternal(serialized)
        assertEquals(source.port, restored.port)
        assertEquals(source.jmxPort, restored.jmxPort)
        assertEquals(source.charset, restored.charset)
        assertEquals(source.getResinConf(), restored.getResinConf())
        assertEquals(source.isDebugConfiguration(), restored.isDebugConfiguration())
        assertEquals(source.getDeployMode(), restored.getDeployMode())
    }

    @Test
    fun `deployment model data serialization omits defaults and round-trips state`() {
        val defaultState = Element("state")
        serializeObjectInto(ResinModuleDeploymentModel.ResinModuleDeploymentModelData(), defaultState)
        assertTrue(defaultState.attributes.isEmpty())
        assertTrue(defaultState.children.isEmpty())

        val source = ResinModuleDeploymentModel.ResinModuleDeploymentModelData().apply {
            contextPath = "/console"
            host = "example.test"
            isDefaultContextPath = false
        }
        val serialized = Element("state")
        serializeObjectInto(source, serialized)

        val restored = ResinModuleDeploymentModel.ResinModuleDeploymentModelData()
        XmlSerializer.deserializeInto(restored, serialized)
        assertEquals(source.contextPath, restored.contextPath)
        assertEquals(source.host, restored.host)
        assertEquals(source.isDefaultContextPath, restored.isDefaultContextPath)
    }
}
