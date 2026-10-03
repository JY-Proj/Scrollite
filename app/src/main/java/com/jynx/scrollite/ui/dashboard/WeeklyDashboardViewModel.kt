package com.jynx.scrollite.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jynx.scrollite.data.Platform
import com.jynx.scrollite.data.ScreenTimeStore
import com.jynx.scrollite.data.UsageRepository
import com.jynx.scrollite.data.WeekSummary
import com.jynx.scrollite.data.WeeklyUsage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class WeeklyDashboardState(
    val week: WeeklyUsage,
    val today: LocalDate,
    val weekOffset: Int,
    val dailyLimitMs: Long,
    val trackedApps: List<Platform>,
    /** null = all apps */
    val selectedApp: Platform?,
    val view: DashboardView,
    val summary: WeekSummary,
)

/**
 * Live data for the weekly chart. Re-queries whenever a session is saved
 * (Room re-emits), and rolls over to a new week on Monday without a restart.
 * The app filter stays put while browsing weeks.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WeeklyDashboardViewModel(
    private val usage: UsageRepository,
    private val store: ScreenTimeStore,
) : ViewModel() {

    /** 0 = this week, -1 = last week, … */
    private val weekOffset = MutableStateFlow(0)
    private val selectedApp = MutableStateFlow<Platform?>(null)

    /** Checks the date periodically so "today" and the current week roll over at midnight. */
    private val today = flow {
        while (true) {
            emit(usage.today())
            delay(DATE_CHECK_INTERVAL_MS)
        }
    }.distinctUntilChanged()

    val state: StateFlow<WeeklyDashboardState?> =
        combine(today, weekOffset) { today, offset -> today to offset }
            .flatMapLatest { (today, offset) ->
                val weekStart = WeeklyUsage.weekStartOf(today).plusWeeks(offset.toLong())
                combine(
                    usage.observeWeek(weekStart),
                    usage.observeWeek(weekStart.minusWeeks(1)),
                    store.dailyLimitFlow,
                    usage.observeTrackedPlatforms(),
                    selectedApp,
                ) { week, previousWeek, limitMinutes, trackedApps, app ->
                    val view = DashboardView.of(week, app, trackedApps)
                    WeeklyDashboardState(
                        week = week,
                        today = today,
                        weekOffset = offset,
                        dailyLimitMs = limitMinutes * 60_000L,
                        trackedApps = trackedApps,
                        selectedApp = app,
                        view = view,
                        summary = WeekSummary.of(week, previousWeek, today, view.filter),
                    )
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun previousWeek() {
        weekOffset.value -= 1
    }

    fun nextWeek() {
        if (weekOffset.value < 0) weekOffset.value += 1
    }

    fun selectApp(app: Platform?) {
        selectedApp.value = app
    }

    companion object {
        private const val DATE_CHECK_INTERVAL_MS = 30_000L

        fun Factory(usage: UsageRepository, store: ScreenTimeStore) = viewModelFactory {
            initializer { WeeklyDashboardViewModel(usage, store) }
        }
    }
}
