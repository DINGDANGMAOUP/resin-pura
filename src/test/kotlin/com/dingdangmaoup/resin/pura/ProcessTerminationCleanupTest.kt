package com.dingdangmaoup.resin.pura

import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessListener
import org.junit.Assert.*
import org.junit.Test
import java.io.OutputStream

class ProcessTerminationCleanupTest {
    @Test
    fun `already terminated process releases cleanup listener immediately`() {
        val handler = TestProcessHandler().apply { terminate() }
        var cleanups = 0

        onProcessTermination(handler) { cleanups++ }

        assertEquals(1, cleanups)
        assertTrue(handler.listeners.isEmpty())
    }

    @Test
    fun `running process keeps resources until exit and ignores duplicate notification`() {
        val handler = TestProcessHandler()
        var cleanups = 0
        onProcessTermination(handler) { cleanups++ }
        val listener = handler.listeners.single()
        assertEquals(0, cleanups)

        handler.terminate()
        listener.processTerminated(ProcessEvent(handler, 0))

        assertEquals(1, cleanups)
        assertTrue(handler.listeners.isEmpty())
    }

    @Test
    fun `exit during listener registration does not run cleanup twice`() {
        val handler = TestProcessHandler().apply { terminateOnRegistration = true }
        var cleanups = 0

        onProcessTermination(handler) { cleanups++ }

        assertEquals(1, cleanups)
        assertTrue(handler.listeners.isEmpty())
    }

    @Test
    fun `cleanup failure does not retain the listener`() {
        val handler = TestProcessHandler().apply { terminate() }

        assertThrows(IllegalStateException::class.java) {
            onProcessTermination(handler) { error("cleanup failed") }
        }

        assertTrue(handler.listeners.isEmpty())
    }

    private class TestProcessHandler : ProcessHandler() {
        val listeners = mutableSetOf<ProcessListener>()
        var terminateOnRegistration = false

        init { startNotify() }

        override fun addProcessListener(listener: ProcessListener) {
            listeners.add(listener)
            super.addProcessListener(listener)
            if (terminateOnRegistration) terminate()
        }

        override fun removeProcessListener(listener: ProcessListener) {
            listeners.remove(listener)
            super.removeProcessListener(listener)
        }

        fun terminate() = notifyProcessTerminated(0)
        override fun destroyProcessImpl() = terminate()
        override fun detachProcessImpl() = notifyProcessDetached()
        override fun detachIsDefault() = false
        override fun getProcessInput(): OutputStream? = null
    }
}
