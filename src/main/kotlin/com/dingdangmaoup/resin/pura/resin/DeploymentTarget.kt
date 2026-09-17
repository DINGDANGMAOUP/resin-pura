package com.dingdangmaoup.resin.pura.resin

import com.intellij.javaee.appServers.deployment.DeploymentStatus

/** Identity of an application within a virtual host, shared by validation and observation. */
data class DeploymentTarget(val host: String, val contextPath: String) {
    val jmxHost: String get() = host.ifEmpty { "default" }
}

data class DeploymentObservation(val status: DeploymentStatus, val terminal: Boolean)

/** Identity, not structural equality, distinguishes superseded operations. */
internal class DeploymentOperation(val removing: Boolean)

/** A bounded wait: a missing application or an unavailable connection must not poll forever. */
internal class DeploymentWait(private val startedAt: Long = System.nanoTime()) {
    fun finish(observation: DeploymentObservation, now: Long = System.nanoTime()): Boolean =
        observation.terminal || now - startedAt >= TIMEOUT_NANOS

    companion object {
        internal const val TIMEOUT_NANOS = 120_000_000_000L
    }
}
