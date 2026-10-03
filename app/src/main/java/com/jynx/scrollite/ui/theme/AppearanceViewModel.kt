package com.jynx.scrollite.ui.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jynx.scrollite.data.AppearanceStore
import com.jynx.scrollite.data.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppearanceViewModel(private val store: AppearanceStore) : ViewModel() {

    /** Null until the stored choice has loaded, so the UI doesn't flash the wrong theme first. */
    val themeMode: StateFlow<ThemeMode?> = store.themeModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { store.setThemeMode(mode) }
    }

    companion object {
        fun Factory(store: AppearanceStore) = viewModelFactory {
            initializer { AppearanceViewModel(store) }
        }
    }
}
