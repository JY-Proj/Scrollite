package com.jynx.scrollite.ui.limit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LimitOverlaySpecTest {

    private val limitMs = 30 * 60_000L
    private val extraMs = 15 * 60_000L

    private fun bump(
        watchedMs: Long,
        extraMs: Long = 0L,
        extensionsUsedToday: Int = 0,
        maxExtensionsPerDay: Int = 1,
        alreadyOverAtSessionStart: Boolean = false,
    ) = LimitOverlaySpec.forBump("Reels", watchedMs, limitMs, extraMs, extensionsUsedToday, maxExtensionsPerDay, alreadyOverAtSessionStart)

    @Test
    fun crossingLimitMidSession_showsFullCountdown() {
        val spec = bump(limitMs + 1_000L)

        assertEquals(LimitOverlaySpec.Variant.LIMIT_REACHED, spec.variant)
        assertEquals(LimitOverlaySpec.FULL_COUNTDOWN_SECONDS, spec.countdownSeconds)
    }

    @Test
    fun reenteringWhenAlreadyOver_withNoExtensionLeft_showsShortCountdown() {
        val spec = bump(limitMs + 5 * 60_000L, extensionsUsedToday = 1, alreadyOverAtSessionStart = true)

        assertEquals(LimitOverlaySpec.Variant.STILL_OVER_LIMIT, spec.variant)
        assertEquals(LimitOverlaySpec.SHORT_COUNTDOWN_SECONDS, spec.countdownSeconds)
    }

    @Test
    fun minutesAreFloored_soWatchedNeverReadsBelowLimit() {
        // 30m59s watched against a 30m limit should read "30 min", not round up to 31
        val spec = bump(limitMs + 59_000L)

        assertEquals(30L, spec.watchedMinutes)
        assertEquals(30L, spec.limitMinutes)
    }

    @Test
    fun extraTimeRunningOutMidSession_showsExtraTimeUp() {
        val spec = bump(limitMs + extraMs + 1_000L, extraMs = extraMs, extensionsUsedToday = 1)

        assertEquals(LimitOverlaySpec.Variant.EXTRA_TIME_UP, spec.variant)
        assertEquals(15L, spec.extraMinutes)
        assertEquals(LimitOverlaySpec.FULL_COUNTDOWN_SECONDS, spec.countdownSeconds)
    }

    @Test
    fun reenteringAfterExtraTimeUsed_isStillOverLimit() {
        val spec = bump(limitMs + extraMs + 60_000L, extraMs = extraMs, extensionsUsedToday = 1, alreadyOverAtSessionStart = true)

        assertEquals(LimitOverlaySpec.Variant.STILL_OVER_LIMIT, spec.variant)
    }

    @Test
    fun canExtend_onlyUntilDailyCapIsUsed() {
        assertTrue(bump(limitMs, extensionsUsedToday = 0).canExtend)
        assertFalse(bump(limitMs, extensionsUsedToday = 1).canExtend)
    }

    @Test
    fun flexible_countsDownRemainingExtensions() {
        assertEquals(2, bump(limitMs, extensionsUsedToday = 1, maxExtensionsPerDay = 3).extensionsRemaining)
    }

    @Test
    fun locked_neverOffersExtension_andUsesShortCountdownOnRepeat() {
        val spec = bump(limitMs + 60_000L, maxExtensionsPerDay = 0, alreadyOverAtSessionStart = true)

        assertFalse(spec.canExtend)
        assertEquals(LimitOverlaySpec.SHORT_COUNTDOWN_SECONDS, spec.countdownSeconds)
    }

    @Test
    fun canExtend_alsoOnRepeatVisits_ifNotUsedYet_withTimeToDecide() {
        // Leaving the first time shouldn't forfeit the day's extension
        val spec = bump(limitMs + 60_000L, alreadyOverAtSessionStart = true)

        assertTrue(spec.canExtend)
        assertEquals(LimitOverlaySpec.FULL_COUNTDOWN_SECONDS, spec.countdownSeconds)
    }
}
