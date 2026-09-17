package com.dingdangmaoup.resin.pura.ui

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class LatestRequestRunnerTest {
    @Test fun `late result from cancelled work cannot overwrite the latest input`() {
        val executor = Executors.newFixedThreadPool(2)
        val firstStarted = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val dispatched = CountDownLatch(2)
        val callbacks = ConcurrentLinkedQueue<() -> Unit>()
        val published = mutableListOf<Int>()
        val runner = LatestRequestRunner<Int, Int>(executor, {
            callbacks.add(it); dispatched.countDown()
        }, { value ->
            if (value == 1) {
                firstStarted.countDown()
                while (releaseFirst.count > 0) {
                    try { releaseFirst.await() } catch (_: InterruptedException) { /* emulate non-cancellable filesystem I/O */ }
                }
            }
            value
        }, { published.add(it) })
        try {
            runner.submit(1)
            assertTrue(firstStarted.await(5, TimeUnit.SECONDS))
            runner.submit(2)
            releaseFirst.countDown()
            assertTrue(dispatched.await(5, TimeUnit.SECONDS))
            callbacks.forEach { it() }
            assertEquals(listOf(2), published)
        } finally {
            releaseFirst.countDown(); runner.close(); executor.shutdownNow()
        }
    }

    @Test fun `closing editor rejects already queued completion and subsequent requests`() {
        val executor = Executors.newSingleThreadExecutor()
        val dispatched = CountDownLatch(1)
        val callbacks = ConcurrentLinkedQueue<() -> Unit>()
        val published = mutableListOf<Int>()
        val runner = LatestRequestRunner<Int, Int>(executor, {
            callbacks.add(it); dispatched.countDown()
        }, { it }, { published.add(it) })
        try {
            runner.submit(1)
            assertTrue(dispatched.await(5, TimeUnit.SECONDS))
            runner.close()
            callbacks.forEach { it() }
            runner.submit(2)
            assertTrue(published.isEmpty())
        } finally { runner.close(); executor.shutdownNow() }
    }
}
