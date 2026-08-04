package com.dingdangmaoup.resin.pura.resin.jmx

import com.dingdangmaoup.resin.pura.ResinRemoteModel
import com.dingdangmaoup.resin.pura.resin.configuration.Resin3XConfigurationStrategy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy
import javax.management.MBeanServerConnection

class ConnectorCommandBaseTest {
    @Test
    fun `connector renders an ipv6-safe url and resolves credentials only once`() {
        val endpoint = JmxEndpoint.of("2001:DB8::1", 9999)
        val credentials = JmxCredentials("resin-admin", "secret")
        var resolutions = 0
        val command = TestConnectorCommand(emptyModel(), endpoint) {
            resolutions++
            credentials
        }

        assertEquals(
            "service:jmx:rmi:///jndi/rmi://[2001:DB8::1]:9999/jmxrmi",
            command.jmxUrl(),
        )
        assertEquals("resin-admin", command.username())
        assertEquals("secret", command.password())
        assertEquals("resin-admin", command.username())
        assertEquals(1, resolutions)
    }

    @Test
    fun `connector caches an explicit anonymous credential resolution`() {
        var resolutions = 0
        val command = TestConnectorCommand(emptyModel(), JmxEndpoint.of("example.com", 9999)) {
            resolutions++
            null
        }

        assertNull(command.username())
        assertNull(command.password())
        assertEquals(1, resolutions)
    }

    @Test
    fun `ping reads the deploy mbean state without opening an external connection`() {
        val command = ConnectorPingCommand(
            emptyModel(),
            JmxEndpoint.of("example.com", 9999),
            JmxCredentialSource.Anonymous,
        )

        assertTrue(invokePing(command, "AcTiVe"))
        assertFalse(invokePing(command, "failed"))
    }

    @Test
    fun `ping keeps stored draft and anonymous credential sources distinct`() {
        val endpoint = JmxEndpoint.of("example.com", 9999)
        val stored = JmxCredentials("stored-admin", "stored-secret")
        val draft = JmxCredentials("draft-admin", "draft-secret")
        val store = CountingJmxCredentialStore(stored)
        val model = ResinRemoteModel(store)

        val storedCommand = ConnectorPingCommand(model, endpoint, JmxCredentialSource.Stored)
        assertEquals("stored-admin", connectorCredential(storedCommand, "getJmxUsername"))
        assertEquals("stored-secret", connectorCredential(storedCommand, "getJmxPassword"))
        assertEquals("stored-admin", connectorCredential(storedCommand, "getJmxUsername"))
        assertEquals(listOf(endpoint), store.loads)

        val draftCommand = ConnectorPingCommand(model, endpoint, JmxCredentialSource.Draft(draft))
        assertEquals("draft-admin", connectorCredential(draftCommand, "getJmxUsername"))
        assertEquals("draft-secret", connectorCredential(draftCommand, "getJmxPassword"))
        assertEquals(listOf(endpoint), store.loads)

        val anonymousCommand = ConnectorPingCommand(model, endpoint, JmxCredentialSource.Anonymous)
        assertNull(connectorCredential(anonymousCommand, "getJmxUsername"))
        assertNull(connectorCredential(anonymousCommand, "getJmxPassword"))
        assertEquals(listOf(endpoint), store.loads)
    }

    private fun invokePing(command: ConnectorPingCommand, state: String): Boolean {
        val connection = Proxy.newProxyInstance(
            javaClass.classLoader,
            arrayOf(MBeanServerConnection::class.java),
        ) { _, method, arguments ->
            when (method.name) {
                "getAttribute" -> {
                    assertEquals(Resin3XConfigurationStrategy.MBEAN_WEB_APP_DEPLOY, arguments?.get(0))
                    assertEquals(Resin3XConfigurationStrategy.STATE_JMX_ATTRIBUTE, arguments?.get(1))
                    state
                }

                else -> throw AssertionError("Unexpected MBean call: ${method.name}")
            }
        } as MBeanServerConnection
        val doExecute = ConnectorPingCommand::class.java.getDeclaredMethod(
            "doExecute",
            MBeanServerConnection::class.java,
        )
        doExecute.isAccessible = true
        return doExecute.invoke(command, connection) as Boolean
    }

    private fun connectorCredential(command: ConnectorPingCommand, methodName: String): String? {
        val method = ConnectorCommandBase::class.java.getDeclaredMethod(methodName)
        method.isAccessible = true
        return method.invoke(command) as String?
    }

    private fun emptyModel(): ResinRemoteModel = ResinRemoteModel(object : JmxCredentialStore {
        override fun load(endpoint: JmxEndpoint): JmxCredentials? = null

        override fun save(endpoint: JmxEndpoint, credentials: JmxCredentials?) = Unit
    })

    private class CountingJmxCredentialStore(private val credentials: JmxCredentials?) : JmxCredentialStore {
        val loads = mutableListOf<JmxEndpoint>()

        override fun load(endpoint: JmxEndpoint): JmxCredentials? {
            loads.add(endpoint)
            return credentials
        }

        override fun save(endpoint: JmxEndpoint, credentials: JmxCredentials?) = Unit
    }

    private class TestConnectorCommand(
        resinModel: ResinRemoteModel,
        private val endpoint: JmxEndpoint,
        credentialProvider: () -> JmxCredentials?,
    ) : ConnectorCommandBase<Boolean>(resinModel, credentialProvider) {
        override fun getHost(): String = endpoint.connectionHost

        override fun getJmxPort(): Int = endpoint.port

        override fun doExecute(connection: MBeanServerConnection): Boolean = true

        fun jmxUrl(): String = getJmxUrl()

        fun username(): String? = getJmxUsername()

        fun password(): String? = getJmxPassword()
    }
}
