package com.jynx.scrollite.ui.screentime

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jynx.scrollite.data.ScreenTimeStore
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
)

class ScreenTimeViewModel(private val store: ScreenTimeStore) : ViewModel() {

    val dailyLimitMinutes: StateFlow<Int> = store.dailyLimitFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val uiState: StateFlow<ScreenTimeUiState> =
        combine(store.todayTotalFlow, store.dailyLimitFlow) { totalMillis, limitMinutes ->
            ScreenTimeUiState(
                minutesWatched = totalMillis / 60_000L,
                limitMinutes = limitMinutes,
                limitReached = totalMillis >= limitMinutes * 60_000L,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ScreenTimeUiState())

    fun setDailyLimit(minutes: Int) {
        viewModelScope.launch { store.setDailyLimit(minutes) }
    }

    /**
     * TEMP: lets the UI simulate real timing writes before Person A's
     * accessibility-service timing lands. Remove once real timing is live.
     */
    fun addDebugTime(millis: Long) {
        viewModelScope.launch { store.addElapsedTime(millis) }
    }

    companion object {
        fun Factory(store: ScreenTimeStore) = viewModelFactory {
            initializer { ScreenTimeViewModel(store) }
        }
    }
}
