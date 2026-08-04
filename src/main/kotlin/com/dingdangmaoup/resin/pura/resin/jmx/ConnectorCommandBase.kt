package com.dingdangmaoup.resin.pura.resin.jmx

import com.dingdangmaoup.resin.pura.ResinModelBase
import com.intellij.javaee.oss.util.AbstractConnectorCommand
import com.intellij.openapi.diagnostic.Logger
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeoutException

internal abstract class ConnectorCommandBase<T : Any>(
    private val myResinModel: ResinModelBase<*>,
    credentialProvider: () -> JmxCredentials? = myResinModel::getJmxCredentials,
) : AbstractConnectorCommand<T>() {
    private var myResult: T? = null
    private val myCredentials: JmxCredentials? by lazy(credentialProvider)

    override fun getHost(): String = myResinModel.getCommonModel().host

    override fun getJmxPort(): Int = myResinModel.jmxPort

    fun safeExecute(): Boolean {
        return try {
            myResult = execute()
            myResult != null
        } catch (e: TimeoutException) {
            LOG.debug(e)
            false
        } catch (e: ExecutionException) {
            LOG.debug(e)
            false
        }
    }

    fun getResult(): T? = myResult

    open override fun getJmxUsername(): String? = myCredentials?.username

    open override fun getJmxPassword(): String? = myCredentials?.password

    companion object {
        private val LOG = Logger.getInstance(ConnectorCommandBase::class.java)
    }
}
