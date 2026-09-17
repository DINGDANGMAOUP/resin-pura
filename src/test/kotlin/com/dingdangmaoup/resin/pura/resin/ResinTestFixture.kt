package com.dingdangmaoup.resin.pura.resin

import com.dingdangmaoup.resin.pura.ResinModel
import com.dingdangmaoup.resin.pura.ResinPersistentData
import com.intellij.javaee.appServers.appServerIntegrations.ApplicationServer
import com.intellij.javaee.appServers.run.configuration.CommonModel
import java.io.File
import java.lang.reflect.Proxy

internal fun resinTestModel(home: File, source: File, onLibrary: (() -> Any?)? = null): ResinModel {
    File(home, "bin").mkdirs()
    File(home, "lib").mkdirs()
    File(home, "lib/jsdk23.jar").createNewFile()
    val data = ResinPersistentData().apply { RESIN_HOME = home.path; RESIN_CONF = source.path }
    val server = Proxy.newProxyInstance(ApplicationServer::class.java.classLoader, arrayOf(ApplicationServer::class.java)) { _, method, _ ->
        when (method.name) {
            "getPersistentData" -> data
            "getLibrary" -> onLibrary?.invoke() ?: error("Library not needed by fixture")
            else -> error("Unexpected ApplicationServer call: ${method.name}")
        }
    } as ApplicationServer
    val model = ResinModel().apply { port = 8080 }
    val common = Proxy.newProxyInstance(CommonModel::class.java.classLoader, arrayOf(CommonModel::class.java)) { _, method, _ ->
        when (method.name) {
            "getApplicationServer" -> server
            "getServerModel" -> model
            "getDeploymentModels" -> emptyList<Any>()
            "getHost" -> "localhost"
            else -> error("Unexpected CommonModel call: ${method.name}")
        }
    } as CommonModel
    model.setCommonModel(common)
    return model
}
