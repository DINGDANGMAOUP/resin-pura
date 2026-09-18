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
import com.dingdangmaoup.resin.pura.resin.DeploymentOperation
import com.intellij.javaee.appServers.deployment.DeploymentModel
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

class ResinServerInstance(runConfiguration: CommonModel) : DefaultServerInstance(runConfiguration) {
    private val myPoller = ServerInstancePoller()
    private val activeProcess = AtomicReference<ProcessHandler?>()
    internal val deploymentOperations = ConcurrentHashMap<DeploymentModel, DeploymentOperation>()

    fun getPoller(): ServerInstancePoller = myPoller

    override fun start(processHandler: ProcessHandler) {
        val session = (serverModel as? ResinModel)?.runSession
        activeProcess.set(processHandler)
        var removeDebugListener: () -> Unit = {}
        try {
            super.start(processHandler)
            fireServerListeners(DefaultJ2EEServerEvent(true, false))

            val resinModel = serverModel as ResinModelBase<*>
            val debuggerManager = DebuggerManager.getInstance(resinModel.project)
            val debugListener = object : DebugProcessListener {
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
            }

            removeDebugListener = { debuggerManager.removeDebugProcessListener(processHandler, debugListener) }
            debuggerManager.addDebugProcessListener(processHandler, debugListener)
            myPoller.onInstanceStart()
        } finally {
            // Register after startup so an early exit cannot be followed by restarting the poller.
            // The helper checks an already terminated handler and always removes its listener.
            onProcessTermination(processHandler) {
                try {
                    if (activeProcess.compareAndSet(processHandler, null)) {
                        myPoller.onInstanceShutdown()
                        deploymentOperations.clear()
                    }
                } finally {
                    try {
                        removeDebugListener()
                    } finally {
                        session?.close()
                    }
                }
            }
        }
    }

    override fun shutdown() {
        myPoller.onInstanceShutdown()
        deploymentOperations.clear()
        super.shutdown()
        val ph = processHandler
        if (ph is OSProcessHandler) {
            ph.process.destroy()
        }
        if (ph == null || ph.isProcessTerminated) {
            (serverModel as? ResinModel)?.runSession?.close()
        }
    }

    companion object {
        private val LOG = Logger.getInstance(ResinServerInstance::class.java)
    }
}
