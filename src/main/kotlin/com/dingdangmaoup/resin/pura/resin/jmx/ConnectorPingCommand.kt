package com.dingdangmaoup.resin.pura.resin.jmx

import com.dingdangmaoup.resin.pura.ResinRemoteModel
import java.io.IOException
import javax.management.JMException
import javax.management.MBeanServerConnection

internal class ConnectorPingCommand(
    resinModel: ResinRemoteModel,
    private val endpoint: JmxEndpoint,
    credentialSource: JmxCredentialSource,
) : ConnectorCommandBase<Boolean>(
    resinModel,
    { resolveCredentialSource(credentialSource) { resinModel.loadJmxCredentials(endpoint) } },
) {
    override fun getHost(): String = endpoint.connectionHost

    override fun getJmxPort(): Int = endpoint.port

    @Throws(JMException::class, IOException::class)
    override fun doExecute(connection: MBeanServerConnection): Boolean {
        val state = connection.getAttribute(
            JmxDeploymentClient.MBEAN_WEB_APP_DEPLOY,
            JmxDeploymentClient.STATE_JMX_ATTRIBUTE,
        ) as String
        return JmxDeploymentClient.STATE_JMX_ATTRIBUTE_ACTIVE.equals(state, ignoreCase = true)
    }
}
