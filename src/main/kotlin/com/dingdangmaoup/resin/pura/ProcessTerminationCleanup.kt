package com.dingdangmaoup.resin.pura

import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessListener
import java.util.concurrent.atomic.AtomicBoolean

/** Also handles termination before registration, when no further process event will arrive. */
internal fun onProcessTermination(processHandler: ProcessHandler, cleanup: () -> Unit) {
    val completed = AtomicBoolean()
    val listener = object : ProcessListener {
        fun complete() {
            if (!completed.compareAndSet(false, true)) return
            // Detach before running cleanup, including when cleanup fails.
            processHandler.removeProcessListener(this)
            cleanup()
        }

        override fun processTerminated(event: ProcessEvent) = complete()
    }
    processHandler.addProcessListener(listener)
    if (processHandler.isProcessTerminated) listener.complete()
}
