package com.jynx.scrollite.tracking

/**
 * Lets work run at most once per [intervalMs] without losing the latest
 * request: a request inside the window becomes a single trailing run at the
 * window's end, so the final state is always handled even if no more
 * requests arrive. Holds no timers itself — the caller schedules — which
 * keeps it testable with plain numbers.
 */
class Throttle(private val intervalMs: Long) {

    sealed interface Decision {
        data object RunNow : Decision

        /** Schedule one run after [delayMs]; later requests in the window share it. */
        data class RunLater(val delayMs: Long) : Decision

        /** A trailing run is already scheduled and will cover this request. */
        data object AlreadyScheduled : Decision
    }

    private var lastRunAtMs: Long? = null
    private var trailingScheduled = false

    fun request(nowMs: Long): Decision {
        if (trailingScheduled) return Decision.AlreadyScheduled
        val last = lastRunAtMs
        if (last == null || nowMs - last >= intervalMs) return Decision.RunNow
        trailingScheduled = true
        return Decision.RunLater(last + intervalMs - nowMs)
    }

    /** Call whenever the work actually runs, immediate or trailing. */
    fun onRan(nowMs: Long) {
        lastRunAtMs = nowMs
        trailingScheduled = false
    }

    /** The caller dropped its scheduled trailing run. */
    fun cancelTrailing() {
        trailingScheduled = false
    }
}
