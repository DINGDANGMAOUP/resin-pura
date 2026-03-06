package com.dingdangmaoup.resin.pura

import com.intellij.javaee.appServers.context.DeploymentModelContext
import com.intellij.javaee.appServers.deployment.DeploymentModel
import com.intellij.javaee.appServers.deployment.DeploymentSource
import com.intellij.javaee.appServers.run.configuration.CommonModel
import com.intellij.openapi.util.InvalidDataException
import com.intellij.openapi.util.WriteExternalException
import com.intellij.util.xmlb.SkipDefaultValuesSerializationFilters
import com.intellij.util.xmlb.XmlSerializer
import org.jdom.Element

class ResinModuleDeploymentModel(commonModel: CommonModel, source: DeploymentSource) :
    DeploymentModel(commonModel, source), DeploymentModelContext {
    private var myData = ResinModuleDeploymentModelData()

    override fun isDefaultContextRoot(): Boolean = isDefaultContextPath

    override fun getContextRoot(): String = contextPath

    var isDefaultContextPath: Boolean
        get() = myData.isDefaultContextPath
        set(defaultContextPath) {
            myData.isDefaultContextPath = defaultContextPath
        }

    var contextPath: String
        get() = myData.contextPath
        set(contextPath) {
            myData.contextPath = contextPath
        }

    var host: String
        get() = myData.host
        set(host) {
            myData.host = host
        }

    @Throws(WriteExternalException::class)
    @Suppress("DEPRECATION")
    override fun writeExternal(element: Element) {
        super.writeExternal(element)
        XmlSerializer.serializeInto(myData, element, SkipDefaultValuesSerializationFilters())
    }

    @Throws(InvalidDataException::class)
    override fun readExternal(element: Element) {
        super.readExternal(element)
        myData = ResinModuleDeploymentModelData()
        XmlSerializer.deserializeInto(myData, element)
    }

    class ResinModuleDeploymentModelData {
        var contextPath: String = "/"
        var host: String = ""
        var isDefaultContextPath: Boolean = true
    }
}
