package com.jynx.scrollite.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IdleRecheckTest {

    /** Stand-in for Handler: holds pending runnables until [fireAll]. */
    private val pending = mutableListOf<Pair<Runnable, Long>>()
    private var rechecks = 0

    private val idleRecheck = IdleRecheck(
        intervalMs = 5_000,
        postDelayed = { runnable, delay -> pending += runnable to delay },
        removeCallbacks = { runnable -> pending.removeAll { it.first == runnable } },
        onRecheck = { rechecks++ },
    )

    private fun fireAll() {
        val due = pending.toList()
        pending.clear()
        due.forEach { it.first.run() }
    }

    @Test
    fun openSession_schedulesOneRecheckAfterInterval() {
        idleRecheck.afterDetection(sessionOpen = true)

        assertEquals(listOf(5_000L), pending.map { it.second })
        fireAll()
        assertEquals(1, rechecks)
    }

    @Test
    fun eachDetection_pushesTheRecheckBack_neverStacks() {
        repeat(10) { idleRecheck.afterDetection(sessionOpen = true) }

        assertEquals(1, pending.size)
    }

    @Test
    fun noOpenSession_nothingScheduled() {
        idleRecheck.afterDetection(sessionOpen = true)
        idleRecheck.afterDetection(sessionOpen = false)

        assertTrue(pending.isEmpty())
    }

    @Test
    fun stop_leavesNothingScheduled() {
        idleRecheck.afterDetection(sessionOpen = true)
        idleRecheck.stop()

        assertTrue(pending.isEmpty())
        fireAll()
        assertEquals(0, rechecks)
    }
}
