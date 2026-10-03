package com.jynx.scrollite.ui.dashboard

import com.jynx.scrollite.data.DayBounds
import com.jynx.scrollite.data.Platform
import com.jynx.scrollite.data.Section
import com.jynx.scrollite.data.UsageSession
import com.jynx.scrollite.data.WeeklyUsage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class DashboardViewTest {

    private val zone = ZoneId.of("America/New_York")
    private val monday = LocalDate.of(2026, 9, 28)
    private val min = 60_000L

    private fun session(platform: Platform, section: Section, minutes: Long) = UsageSession(
        startMs = DayBounds.of(monday, zone).startMs,
        endMs = DayBounds.of(monday, zone).startMs + minutes * min,
        platform = platform,
        section = section,
    )

    private val week = WeeklyUsage.from(
        listOf(
            session(Platform.INSTAGRAM, Section.OTHER, 5),
            session(Platform.INSTAGRAM, Section.SHORT_VIDEO, 20),
            session(Platform.TIKTOK, Section.SHORT_VIDEO, 15),
        ),
        monday, zone,
    )

    @Test
    fun allApps_oneSeriesPerApp_coloredByFixedAppOrder() {
        val view = DashboardView.of(week, selectedApp = null, trackedApps = listOf(Platform.INSTAGRAM, Platform.TIKTOK))

        assertEquals(listOf("Instagram", "TikTok"), view.series.map { it.label })
        assertEquals(listOf(Platform.INSTAGRAM.ordinal, Platform.TIKTOK.ordinal), view.series.map { it.colorSlot })
        assertEquals(25 * min, view.series[0].values[0])
        assertFalse(view.showLimitLine)
    }

    @Test
    fun singleApp_stacksSections_shortVideoFirst_withAppLabels() {
        val view = DashboardView.of(week, selectedApp = Platform.INSTAGRAM, trackedApps = listOf(Platform.INSTAGRAM, Platform.TIKTOK))

        assertEquals(listOf("Reels", "Other"), view.series.map { it.label })
        assertEquals(20 * min, view.series[0].values[0])
        assertNull("Other uses the neutral color", view.series[1].colorSlot)
    }

    @Test
    fun limitLine_onlyWhenOneAppsShortVideosAreAllShortVideoTime() {
        val onlyInstagram = DashboardView.of(week, Platform.INSTAGRAM, trackedApps = listOf(Platform.INSTAGRAM))
        val twoApps = DashboardView.of(week, Platform.INSTAGRAM, trackedApps = listOf(Platform.INSTAGRAM, Platform.TIKTOK))

        assertTrue(onlyInstagram.showLimitLine)
        assertFalse(twoApps.showLimitLine)
    }

    @Test
    fun singleApp_filterOnlyCountsThatApp() {
        val view = DashboardView.of(week, Platform.TIKTOK, trackedApps = listOf(Platform.INSTAGRAM, Platform.TIKTOK))

        assertEquals(15 * min, week.totalMs(view.filter))
        assertEquals(listOf("For You"), view.series.map { it.label })
    }
}
