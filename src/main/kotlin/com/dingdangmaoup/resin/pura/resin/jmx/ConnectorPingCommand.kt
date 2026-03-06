package com.dingdangmaoup.resin.pura.resin.jmx

import com.dingdangmaoup.resin.pura.ResinModelBase
import com.dingdangmaoup.resin.pura.resin.configuration.Resin3XConfigurationStrategy
import java.io.IOException
import javax.management.JMException
import javax.management.MBeanServerConnection

class ConnectorPingCommand(resinModel: ResinModelBase<*>, private val myJmxPort: Int) : ConnectorCommandBase<Boolean>(resinModel) {
    override fun getJmxPort(): Int = myJmxPort

    // TODO: check behavior if ping is insuccessful - ex should not be logged
    @Throws(JMException::class, IOException::class)
    override fun doExecute(connection: MBeanServerConnection): Boolean {
        val state = connection.getAttribute(
            Resin3XConfigurationStrategy.MBEAN_WEB_APP_DEPLOY,
            Resin3XConfigurationStrategy.STATE_JMX_ATTRIBUTE,
        ) as String
        return Resin3XConfigurationStrategy.STATE_JMX_ATTRIBUTE_ACTIVE.equals(state, ignoreCase = true)
    }
}
