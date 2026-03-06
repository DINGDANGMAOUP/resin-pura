package com.dingdangmaoup.resin.pura.resin.jmx

import com.dingdangmaoup.resin.pura.ResinModelBase
import com.intellij.javaee.oss.util.AbstractConnectorCommand
import com.intellij.openapi.diagnostic.Logger
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeoutException

abstract class ConnectorCommandBase<T>(private val myResinModel: ResinModelBase<*>) : AbstractConnectorCommand<T>() {
    private var myResult: T? = null

    override fun getHost(): String = myResinModel.getCommonModel().host

    override fun getJmxPort(): Int = myResinModel.jmxPort

    fun safeExecute(): Boolean {
        return try {
            myResult = execute()
            true
        } catch (e: TimeoutException) {
            LOG.debug(e)
            false
        } catch (e: ExecutionException) {
            LOG.debug(e)
            false
        }
    }

    fun getResult(): T? = myResult

    override fun getJmxUsername(): String? = myResinModel.getJmxUsername()

    override fun getJmxPassword(): String? = myResinModel.getJmxPassword()

    companion object {
        private val LOG = Logger.getInstance(ConnectorCommandBase::class.java)
    }
}
