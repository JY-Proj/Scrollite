package com.jynx.scrollite.data

/** Bounds, step and presets for the daily short-video limit picker. */
object DailyLimitOptions {
    const val MIN_MINUTES = 5
    const val MAX_MINUTES = 180
    const val STEP_MINUTES = 5
    val PRESETS_MINUTES = listOf(15, 30, 45, 60)

    /** Next step up, snapping an off-step value (e.g. 6) to the step grid (10). */
    fun stepUp(minutes: Int): Int =
        ((minutes / STEP_MINUTES + 1) * STEP_MINUTES).coerceIn(MIN_MINUTES, MAX_MINUTES)

    /** Next step down, snapping an off-step value (e.g. 6) to the step grid (5). */
    fun stepDown(minutes: Int): Int =
        ((minutes - 1) / STEP_MINUTES * STEP_MINUTES).coerceIn(MIN_MINUTES, MAX_MINUTES)

    /** Raising the limit asks for confirmation first; lowering it never does. */
    fun isRaise(currentMinutes: Int, newMinutes: Int): Boolean = newMinutes > currentMinutes
}
