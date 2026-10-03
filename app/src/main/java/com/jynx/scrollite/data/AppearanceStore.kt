package com.jynx.scrollite.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.appearanceDataStore: DataStore<Preferences> by preferencesDataStore(name = "appearance")

/** Look-and-feel preferences, read by both the app UI and the limit overlay. */
class AppearanceStore(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
    }

    val themeModeFlow: Flow<ThemeMode> = context.appearanceDataStore.data.map { prefs ->
        ThemeMode.fromNameOrNull(prefs[Keys.THEME_MODE]) ?: ThemeMode.DEFAULT
    }

    suspend fun getThemeMode(): ThemeMode = themeModeFlow.first()

    suspend fun setThemeMode(mode: ThemeMode) {
        context.appearanceDataStore.edit { prefs -> prefs[Keys.THEME_MODE] = mode.name }
    }
}
