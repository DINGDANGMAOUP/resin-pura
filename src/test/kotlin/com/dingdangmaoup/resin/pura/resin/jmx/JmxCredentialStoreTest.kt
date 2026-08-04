package com.dingdangmaoup.resin.pura.resin.jmx

import com.dingdangmaoup.resin.pura.ResinRemoteModel
import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.CredentialStore
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.generateServiceName
import org.jdom.Element
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class JmxCredentialStoreTest {
    @Test
    fun `endpoint normalization is stable for dns and ipv6 hosts`() {
        val canonicalDns = JmxEndpoint.of("example.com", 9999)
        val mixedCaseDns = JmxEndpoint.of(" Example.COM ", 9999)
        val bareIpv6 = JmxEndpoint.of("2001:DB8::1", 9999)
        val bracketedIpv6 = JmxEndpoint.of("[2001:db8::1]", 9999)

        assertEquals(
            "example.com:9999",
            PasswordSafeJmxCredentialStore.normalizeEndpoint(" Example.COM ", 9999),
        )
        assertEquals(
            "[2001:db8::1]:9999",
            PasswordSafeJmxCredentialStore.normalizeEndpoint("[2001:DB8::1]", 9999),
        )
        assertEquals(canonicalDns, mixedCaseDns)
        assertEquals(bareIpv6, bracketedIpv6)
        assertEquals("[2001:DB8::1]", bareIpv6.connectionHost)
        assertEquals("[2001:db8::1]", bracketedIpv6.connectionHost)
    }

    @Test
    fun `endpoint validation rejects ambiguous brackets blanks and invalid ports`() {
        for (host in listOf("", "   ", "[]", "[2001:db8::1", "2001:db8::1]")) {
            assertThrows(IllegalArgumentException::class.java) { JmxEndpoint.of(host, 9999) }
        }
        assertThrows(IllegalArgumentException::class.java) { JmxEndpoint.of("example.com", 0) }
        assertThrows(IllegalArgumentException::class.java) { JmxEndpoint.of("example.com", 65536) }
    }

    @Test
    fun `password safe adapter saves loads and explicitly clears credentials`() {
        val delegate = InMemoryCredentialStore()
        val store = PasswordSafeJmxCredentialStore(delegate)
        val credentials = JmxCredentials("resin-admin", "secret")
        val endpoint = JmxEndpoint.of("Example.COM", 9999)

        store.save(endpoint, credentials)

        assertEquals(credentials, store.load(JmxEndpoint.of("example.com", 9999)))
        store.save(JmxEndpoint.of("example.com", 9999), null)
        assertNull(store.load(endpoint))
    }

    @Test
    fun `password safe keys isolate hosts ports and address families`() {
        val delegate = InMemoryCredentialStore()
        val store = PasswordSafeJmxCredentialStore(delegate)
        val primary = JmxEndpoint.of("example.com", 9999)
        val otherHost = JmxEndpoint.of("other.example.com", 9999)
        val otherPort = JmxEndpoint.of("example.com", 10000)
        val ipv6 = JmxEndpoint.of("2001:db8::1", 9999)
        val primaryCredentials = JmxCredentials("primary", "primary-secret")
        val otherHostCredentials = JmxCredentials("other-host", "host-secret")
        val otherPortCredentials = JmxCredentials("other-port", "port-secret")
        val ipv6Credentials = JmxCredentials("ipv6", "ipv6-secret")

        store.save(primary, primaryCredentials)
        store.save(otherHost, otherHostCredentials)
        store.save(otherPort, otherPortCredentials)
        store.save(ipv6, ipv6Credentials)

        assertEquals(primaryCredentials, store.load(JmxEndpoint.of("EXAMPLE.COM", 9999)))
        assertEquals(otherHostCredentials, store.load(otherHost))
        assertEquals(otherPortCredentials, store.load(otherPort))
        assertEquals(ipv6Credentials, store.load(JmxEndpoint.of("[2001:DB8::1]", 9999)))

        store.save(primary, null)
        assertNull(store.load(primary))
        assertEquals(otherHostCredentials, store.load(otherHost))
        assertEquals(otherPortCredentials, store.load(otherPort))
        assertEquals(ipv6Credentials, store.load(ipv6))
    }

    @Test
    fun `incomplete stored credentials fail closed`() {
        val delegate = InMemoryCredentialStore()
        val endpoint = PasswordSafeJmxCredentialStore.normalizeEndpoint("example.com", 9999)
        val attributes = CredentialAttributes(generateServiceName("Resin Pura JMX", endpoint))
        delegate.set(attributes, Credentials("resin-admin", ""))
        val store = PasswordSafeJmxCredentialStore(delegate)

        assertThrows(IllegalStateException::class.java) { store.load(JmxEndpoint.of("example.com", 9999)) }
    }

    @Test
    fun `credential diagnostics redact the password`() {
        val credentials = JmxCredentials("resin-admin", "top-secret")

        assertTrue(credentials.toString().contains("resin-admin"))
        assertFalse(credentials.toString().contains("top-secret"))
    }

    @Test
    fun `remote model serialization never contains password safe credentials`() {
        val store = RecordingJmxCredentialStore()
        val model = ResinRemoteModel(store)
        val endpoint = JmxEndpoint.of("example.com", 9999)
        model.applyJmxCredentialEdit(endpoint, endpoint, JmxCredentialEdit.Replace(JmxCredentials("resin-admin", "top-secret")))
        val state = Element("state")

        model.writeExternal(state)

        val serialized = serializeValues(state)
        assertFalse(serialized.contains("resin-admin"))
        assertFalse(serialized.contains("top-secret"))
    }

    @Test
    fun `credential edits are endpoint scoped and clear both changed endpoints`() {
        val store = RecordingJmxCredentialStore()
        val original = JmxEndpoint.of("old.example.com", 9999)
        val target = JmxEndpoint.of("new.example.com", 9998)
        val replacement = JmxCredentials("new-admin", "new-secret")

        applyCredentialEdit(store, original, target, JmxCredentialEdit.Keep)
        assertTrue(store.saves.isEmpty())

        applyCredentialEdit(store, original, target, JmxCredentialEdit.Replace(replacement))
        assertEquals(listOf(target to replacement), store.saves)

        store.saves.clear()
        applyCredentialEdit(store, original, target, JmxCredentialEdit.Clear)
        assertEquals(listOf(original to null, target to null), store.saves)

        store.saves.clear()
        applyCredentialEdit(
            store,
            JmxEndpoint.of("EXAMPLE.com", 9999),
            JmxEndpoint.of("example.COM", 9999),
            JmxCredentialEdit.Clear,
        )
        assertEquals(1, store.saves.size)
    }

    @Test
    fun `credential source is explicit and stored credentials are loaded once per resolution`() {
        val stored = JmxCredentials("stored", "stored-secret")
        val draft = JmxCredentials("draft", "draft-secret")
        var loads = 0
        val loader = {
            loads++
            stored
        }

        assertSame(stored, resolveCredentialSource(JmxCredentialSource.Stored, loader))
        assertEquals(1, loads)
        assertSame(draft, resolveCredentialSource(JmxCredentialSource.Draft(draft), loader))
        assertNull(resolveCredentialSource(JmxCredentialSource.Anonymous, loader))
        assertEquals(1, loads)
    }

    private fun serializeValues(element: Element): String = buildString {
        for (attribute in element.attributes) {
            append(attribute.name).append('=').append(attribute.value).append(';')
        }
        append(element.text)
        for (child in element.children) {
            append(serializeValues(child))
        }
    }

    private class InMemoryCredentialStore : CredentialStore {
        private val values = mutableMapOf<CredentialAttributes, Credentials>()

        override fun get(attributes: CredentialAttributes): Credentials? = values[attributes]

        override fun set(attributes: CredentialAttributes, credentials: Credentials?) {
            if (credentials == null) {
                values.remove(attributes)
            } else {
                values[attributes] = credentials
            }
        }
    }

    private class RecordingJmxCredentialStore : JmxCredentialStore {
        private val values = mutableMapOf<JmxEndpoint, JmxCredentials>()
        val saves = mutableListOf<Pair<JmxEndpoint, JmxCredentials?>>()

        override fun load(endpoint: JmxEndpoint): JmxCredentials? = values[endpoint]

        override fun save(endpoint: JmxEndpoint, credentials: JmxCredentials?) {
            saves.add(endpoint to credentials)
            if (credentials == null) values.remove(endpoint) else values[endpoint] = credentials
        }
    }
}
