package com.dingdangmaoup.resin.pura.ui

import java.util.concurrent.ExecutorService
import java.util.concurrent.Future

/** Cancel superseded work and reject late completions, including work that ignores interruption. */
internal class LatestRequestRunner<I, O>(
    private val executor: ExecutorService,
    private val dispatch: (() -> Unit) -> Unit,
    private val work: (I) -> O,
    private val publish: (O) -> Unit,
) : AutoCloseable {
    private var generation = 0L
    private var closed = false
    private var pending: Future<*>? = null

    @Synchronized
    fun invalidate() {
        generation++
        pending?.cancel(true)
        pending = null
    }

    @Synchronized
    fun submit(input: I) {
        if (closed) return
        invalidate()
        val request = generation
        pending = executor.submit {
            val result = work(input)
            dispatch {
                synchronized(this) {
                    if (!closed && generation == request) publish(result)
                }
            }
        }
    }

    @Synchronized
    override fun close() {
        closed = true
        invalidate()
    }
}
