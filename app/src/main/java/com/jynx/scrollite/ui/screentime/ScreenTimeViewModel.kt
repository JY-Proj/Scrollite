package com.jynx.scrollite.ui.screentime

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jynx.scrollite.data.Platform
import com.jynx.scrollite.data.ScreenTimeStore
import com.jynx.scrollite.data.Section
import com.jynx.scrollite.data.UsageRepository
import com.jynx.scrollite.data.UsageSession
import com.jynx.scrollite.data.Strictness
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Derived UI state for the screen-time summary. [limitReached] is computed
 * from the same numbers shown so the warning treatment can never disagree
 * with the displayed minutes.
 */
data class ScreenTimeUiState(
    val minutesWatched: Long = 0L,
    val limitMinutes: Int = 0,
    val limitReached: Boolean = false,
    val minutesLeft: Long = 0L,
    /** The last fifth of the limit, when a gentle heads-up is worth showing. */
    val showHeadsUp: Boolean = false,
) {
    companion object {
        fun of(totalMillis: Long, limitMinutes: Int): ScreenTimeUiState {
            val minutesWatched = totalMillis / 60_000L
            val limitReached = totalMillis >= limitMinutes * 60_000L
            val minutesLeft = (limitMinutes - minutesWatched).coerceAtLeast(0L)
            return ScreenTimeUiState(
                minutesWatched = minutesWatched,
                limitMinutes = limitMinutes,
                limitReached = limitReached,
                minutesLeft = minutesLeft,
                showHeadsUp = !limitReached && minutesLeft * HEADS_UP_FRACTION_DENOMINATOR <= limitMinutes,
            )
        }

        /** Heads-up once 1/N of the limit is left. */
        private const val HEADS_UP_FRACTION_DENOMINATOR = 5
    }
}

class ScreenTimeViewModel(
    private val store: ScreenTimeStore,
    private val usage: UsageRepository,
) : ViewModel() {

    val dailyLimitMinutes: StateFlow<Int> = store.dailyLimitFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val strictness: StateFlow<Strictness> = store.strictnessFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Strictness.DEFAULT)

    /** Loosening changes made today; sets the tone of the next confirmation. */
    val looseningsToday: StateFlow<Int> = store.looseningsTodayFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val extensionsUsedToday: StateFlow<Int> = store.extensionsUsedTodayFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val uiState: StateFlow<ScreenTimeUiState> =
        combine(usage.observeShortFormTotalToday(), store.dailyLimitFlow, ScreenTimeUiState::of).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ScreenTimeUiState())

    fun setDailyLimit(minutes: Int) {
        viewModelScope.launch { store.setDailyLimit(minutes) }
    }

    fun setStrictness(strictness: Strictness) {
        viewModelScope.launch { store.setStrictness(strictness) }
    }

    /**
     * Simulates Reels watch time (as a Reels session ending now) so the limit
     * can be tested without actually watching. Only reachable from debug builds.
     */
    fun addDebugTime(millis: Long) {
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            usage.record(UsageSession(startMs = now - millis, endMs = now, platform = Platform.INSTAGRAM, section = Section.SHORT_VIDEO))
        }
    }

    companion object {
        fun Factory(store: ScreenTimeStore, usage: UsageRepository) = viewModelFactory {
            initializer { ScreenTimeViewModel(store, usage) }
        }
    }
}
