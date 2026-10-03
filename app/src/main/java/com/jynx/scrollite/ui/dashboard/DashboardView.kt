package com.jynx.scrollite.ui.dashboard

import com.jynx.scrollite.data.Platform
import com.jynx.scrollite.data.Section
import com.jynx.scrollite.data.UsageSlice
import com.jynx.scrollite.data.WeeklyUsage

/**
 * One stacked series, listed bottom-first. [colorSlot] indexes the categorical
 * palette by the entity's fixed order (never its rank, so a color always means
 * the same app or section); null is the neutral "Other" bucket.
 */
data class SeriesSpec(
    val key: String,
    val label: String,
    val colorSlot: Int?,
    val values: List<Long>,
)

/** What the chart shows for the selected filter. */
class DashboardView(
    val series: List<SeriesSpec>,
    /** Which slices the headline numbers cover. */
    val filter: (UsageSlice) -> Boolean,
    /**
     * Whether the daily short-video limit line belongs on this chart. It only
     * does when the bottom series *is* all short-video time — i.e. a single
     * app's view while it's the only app being tracked. Otherwise one app's
     * short videos are only part of what the limit counts, and the line
     * would mislead.
     */
    val showLimitLine: Boolean,
) {
    companion object {
        /** [selectedApp] null = all apps, stacked by app; otherwise that app, stacked by section. */
        fun of(week: WeeklyUsage, selectedApp: Platform?, trackedApps: List<Platform>): DashboardView =
            if (selectedApp == null) allApps(week, trackedApps) else singleApp(week, selectedApp, trackedApps)

        private fun allApps(week: WeeklyUsage, trackedApps: List<Platform>): DashboardView {
            val byApp = week.dailyTotalsBy({ it.platform })
            val series = trackedApps.map { app ->
                SeriesSpec(
                    key = app.name,
                    label = app.displayName,
                    colorSlot = app.ordinal,
                    values = byApp[app] ?: List(7) { 0L },
                )
            }
            return DashboardView(series, filter = { true }, showLimitLine = false)
        }

        private fun singleApp(week: WeeklyUsage, app: Platform, trackedApps: List<Platform>): DashboardView {
            val inApp: (UsageSlice) -> Boolean = { it.platform == app }
            val bySection = week.dailyTotalsBy({ it.section }, inApp)
            // Short video first so it sits on the baseline, under the limit line
            val series = Section.entries.filter { it in bySection }.map { section ->
                SeriesSpec(
                    key = section.name,
                    label = app.labelFor(section),
                    colorSlot = if (section == Section.OTHER) null else section.ordinal,
                    values = bySection.getValue(section),
                )
            }
            return DashboardView(series, filter = inApp, showLimitLine = trackedApps.size <= 1)
        }
    }
}
