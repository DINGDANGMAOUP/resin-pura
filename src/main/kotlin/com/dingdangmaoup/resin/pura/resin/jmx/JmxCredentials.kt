package com.dingdangmaoup.resin.pura.resin.jmx

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.CredentialStore
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe
import java.util.Locale

internal class JmxCredentials(
    val username: String,
    val password: String,
) {
    override fun equals(other: Any?): Boolean =
        other is JmxCredentials && username == other.username && password == other.password

    override fun hashCode(): Int = 31 * username.hashCode() + password.hashCode()

    override fun toString(): String = "JmxCredentials(username=$username, password=<redacted>)"
}

internal class JmxEndpoint private constructor(
    val connectionHost: String,
    val port: Int,
    internal val credentialKey: String,
) {
    override fun equals(other: Any?): Boolean = other is JmxEndpoint && credentialKey == other.credentialKey

    override fun hashCode(): Int = credentialKey.hashCode()

    override fun toString(): String = credentialKey

    companion object {
        fun of(host: String, port: Int): JmxEndpoint {
            require(port in 1..65535) { "JMX port must be between 1 and 65535" }
            var unwrappedHost = host.trim()
            require(unwrappedHost.isNotEmpty()) { "JMX host must not be blank" }
            val hasOpeningBracket = unwrappedHost.startsWith('[')
            val hasClosingBracket = unwrappedHost.endsWith(']')
            require(hasOpeningBracket == hasClosingBracket) { "JMX IPv6 host brackets are incomplete" }
            if (hasOpeningBracket) {
                unwrappedHost = unwrappedHost.substring(1, unwrappedHost.length - 1)
            }
            require(unwrappedHost.isNotEmpty() && '[' !in unwrappedHost && ']' !in unwrappedHost) {
                "JMX host is invalid"
            }
            val normalizedHost = unwrappedHost.lowercase(Locale.ROOT)
            val connectionHost = if (':' in unwrappedHost) "[$unwrappedHost]" else unwrappedHost
            val credentialKey = if (':' in normalizedHost) "[$normalizedHost]:$port" else "$normalizedHost:$port"
            return JmxEndpoint(connectionHost, port, credentialKey)
        }
    }
}

internal sealed interface JmxCredentialEdit {
    data object Keep : JmxCredentialEdit

    data object Clear : JmxCredentialEdit

    class Replace(val credentials: JmxCredentials) : JmxCredentialEdit
}

internal sealed interface JmxCredentialSource {
    data object Stored : JmxCredentialSource

    data object Anonymous : JmxCredentialSource

    class Draft(val credentials: JmxCredentials) : JmxCredentialSource
}

internal interface JmxCredentialStore {
    fun load(endpoint: JmxEndpoint): JmxCredentials?

    fun save(endpoint: JmxEndpoint, credentials: JmxCredentials?)
}

internal class PasswordSafeJmxCredentialStore(
    private val credentialStore: CredentialStore = PasswordSafe.instance,
) : JmxCredentialStore {
    override fun load(endpoint: JmxEndpoint): JmxCredentials? {
        val stored = credentialStore.get(attributes(endpoint)) ?: return null
        val username = stored.userName?.trim()
        val password = stored.password?.toString()
        if (username.isNullOrEmpty() || password.isNullOrEmpty()) {
            throw IllegalStateException("Stored Resin JMX credentials are incomplete or unavailable")
        }
        return JmxCredentials(username, password)
    }

    override fun save(endpoint: JmxEndpoint, credentials: JmxCredentials?) {
        credentialStore.set(
            attributes(endpoint),
            credentials?.let { Credentials(it.username, it.password) },
        )
    }

    private fun attributes(endpoint: JmxEndpoint): CredentialAttributes =
        CredentialAttributes(generateServiceName(SERVICE_NAME, endpoint.credentialKey))

    companion object {
        private const val SERVICE_NAME = "Resin Pura JMX"

        internal fun normalizeEndpoint(host: String, port: Int): String = JmxEndpoint.of(host, port).credentialKey
    }
}

internal fun applyCredentialEdit(
    store: JmxCredentialStore,
    originalEndpoint: JmxEndpoint,
    targetEndpoint: JmxEndpoint,
    edit: JmxCredentialEdit,
) {
    when (edit) {
        JmxCredentialEdit.Keep -> Unit
        is JmxCredentialEdit.Replace -> store.save(targetEndpoint, edit.credentials)
        JmxCredentialEdit.Clear -> {
            store.save(originalEndpoint, null)
            if (targetEndpoint != originalEndpoint) {
                store.save(targetEndpoint, null)
            }
        }
    }
}

internal fun resolveCredentialSource(
    source: JmxCredentialSource,
    storedCredentials: () -> JmxCredentials?,
): JmxCredentials? = when (source) {
    JmxCredentialSource.Stored -> storedCredentials()
    JmxCredentialSource.Anonymous -> null
    is JmxCredentialSource.Draft -> source.credentials
}
