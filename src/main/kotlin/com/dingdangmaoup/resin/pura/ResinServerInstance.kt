package com.dingdangmaoup.resin.pura

import com.dingdangmaoup.resin.pura.resin.ResinConfiguration
import com.dingdangmaoup.resin.pura.resin.ResinInstallation
import com.dingdangmaoup.resin.pura.resin.version.ResinVersion
import com.intellij.debugger.DebuggerManager
import com.intellij.debugger.engine.DebugProcess
import com.intellij.debugger.engine.DebugProcessListener
import com.intellij.execution.ExecutionException
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.javaee.appServers.run.configuration.CommonModel
import com.intellij.javaee.appServers.serverInstances.DefaultJ2EEServerEvent
import com.intellij.javaee.appServers.serverInstances.DefaultServerInstance
import com.intellij.javaee.util.ServerInstancePoller
import com.intellij.javaee.web.debugger.engine.DefaultJSPPositionManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.io.FileUtil
import java.io.IOException
import java.io.StringWriter

class ResinServerInstance(runConfiguration: CommonModel) : DefaultServerInstance(runConfiguration) {
    private val myPoller = ServerInstancePoller()

    fun getPoller(): ServerInstancePoller = myPoller

    override fun start(processHandler: ProcessHandler) {
        super.start(processHandler)
        fireServerListeners(DefaultJ2EEServerEvent(true, false))

        val resinModel = serverModel as ResinModelBase<*>
        DebuggerManager.getInstance(resinModel.project).addDebugProcessListener(
            processHandler,
            object : DebugProcessListener {
                override fun processAttached(process: DebugProcess) {
                    if (resinModel is ResinModel) {
                        try {
                            if (resinModel.isDebugConfiguration()) {
                                val configuration: ResinConfiguration = resinModel.getOrCreateResinConfiguration(false)
                                val configFile = configuration.getConfigFile()
                                val sw = StringWriter()
                                sw.append("\n---\n")
                                sw.append(ResinBundle.message("message.text.resin.conf.debug", configFile.absolutePath))
                                sw.append("\n")
                                sw.append(FileUtil.loadFile(configFile))
                                sw.append("\n---\n")
                                processHandler.notifyTextAvailable(sw.toString(), ProcessOutputTypes.SYSTEM)
                            }
                        } catch (e: ExecutionException) {
                            LOG.error(e)
                        } catch (e: IOException) {
                            LOG.error(e)
                        }
                    }

                    val installation: ResinInstallation? = resinModel.installation
                    if (installation != null && installation.getVersion() != ResinVersion.VERSION_2_X) {
                        process.appendPositionManager(object : DefaultJSPPositionManager(process, getScopeFacets(commonModel)) {
                            override fun getGeneratedClassesPackage(): String = "_jsp"
                        })
                    }
                }
            },
        )

        myPoller.onInstanceStart()
    }

    override fun shutdown() {
        myPoller.onInstanceShutdown()
        super.shutdown()
        val ph = processHandler
        if (ph is OSProcessHandler) {
            ph.process.destroy()
        }
    }

    companion object {
        private val LOG = Logger.getInstance(ResinServerInstance::class.java)
    }
}
