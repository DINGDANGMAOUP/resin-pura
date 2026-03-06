package com.dingdangmaoup.resin.pura

import com.intellij.javaee.appServers.appServerIntegrations.AppServerDeployedFileUrlProvider
import com.intellij.javaee.appServers.appServerIntegrations.AppServerIntegration
import com.intellij.javaee.appServers.appServerIntegrations.ApplicationServerHelper
import com.intellij.javaee.appServers.appServerIntegrations.ApplicationServerUrlMapping
import com.intellij.javaee.appServers.context.FacetContextProvider
import com.intellij.javaee.appServers.deployment.DeploymentProvider
import com.intellij.javaee.appServers.openapi.ex.AppServerIntegrationsManager
import com.intellij.javaee.web.WebFacetContextProvider
import javax.swing.Icon

class ResinManager : AppServerIntegration() {
    private val deploymentProvider = ResinDeploymentProvider()
    private val resinApplicationServerHelper: ApplicationServerHelper = ResinApplicationServerHelper()
    private var myUrlMapping: ApplicationServerUrlMapping? = null

    override fun getIcon(): Icon = ICON_RESIN

    override fun getPresentableName(): String = ResinBundle.message("resin.application.server.name")

    override fun getDeploymentProvider(local: Boolean): DeploymentProvider? {
        return if (local) deploymentProvider else null
    }

    override fun getApplicationServerHelper(): ApplicationServerHelper = resinApplicationServerHelper

    override fun getDeployedFileUrlProvider(): AppServerDeployedFileUrlProvider {
        if (myUrlMapping == null) {
            myUrlMapping = object : ApplicationServerUrlMapping() {
                override fun collectFacetContextProviders(facetContextProvider: MutableList<FacetContextProvider>) {
                    facetContextProvider.add(WebFacetContextProvider())
                }
            }
        }
        return myUrlMapping!!
    }

    companion object {
        @JvmField
        val ICON_RESIN: Icon = ResinIdeaIcons.Resin

        @JvmStatic
        fun getInstance(): ResinManager {
            return AppServerIntegrationsManager.getInstance().getIntegration(ResinManager::class.java)
        }
    }
}
