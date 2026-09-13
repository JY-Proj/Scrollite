package com.jynx.scrollite.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate

private val Context.screenTimeDataStore: DataStore<Preferences> by preferencesDataStore(name = "screen_time")

/**
 * Persists today's Reels watch time and the user's daily limit across
 * process/service restarts. All reads fall back to sane defaults so
 * every flow emits immediately, even before anything has been written.
 */
class ScreenTimeStore(private val context: Context) {

    private object Keys {
        val TODAY_TOTAL_MILLIS = longPreferencesKey("today_total_millis")
        val TODAY_EPOCH_DAY = intPreferencesKey("today_epoch_day")
        val DAILY_LIMIT_MINUTES = intPreferencesKey("daily_limit_minutes")
        val NOTIFIED_TODAY = booleanPreferencesKey("notified_today")
    }

    private companion object {
        const val DEFAULT_DAILY_LIMIT_MINUTES = 30
    }

    private val dataStore get() = context.screenTimeDataStore

    /**
     * Running total for the current day, in milliseconds. Reads as 0 as
     * soon as the stored day no longer matches today (see [isStale]) —
     * the actual reset write happens lazily, on the next [addElapsedTime].
     */
    val todayTotalFlow: Flow<Long> = dataStore.data.map { prefs ->
        if (isStale(prefs)) 0L else prefs[Keys.TODAY_TOTAL_MILLIS] ?: 0L
    }

    val dailyLimitFlow: Flow<Int> = dataStore.data.map { prefs ->
        prefs[Keys.DAILY_LIMIT_MINUTES] ?: DEFAULT_DAILY_LIMIT_MINUTES
    }

    /** Whether the once-per-day limit notification has already fired today. */
    val notifiedTodayFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        if (isStale(prefs)) false else prefs[Keys.NOTIFIED_TODAY] ?: false
    }

    suspend fun getTodayTotal(): Long = todayTotalFlow.first()

    suspend fun getDailyLimit(): Int = dailyLimitFlow.first()

    suspend fun isNotifiedToday(): Boolean = notifiedTodayFlow.first()

    /**
     * Adds [millis] to today's running total. If the stored total belongs
     * to a previous day it is discarded (reset, not accumulated onto) and
     * the "already notified" flag is cleared for the new day.
     */
    suspend fun addElapsedTime(millis: Long) {
        val today = currentEpochDay()
        dataStore.edit { prefs ->
            val stale = prefs[Keys.TODAY_EPOCH_DAY] != today
            val base = if (stale) 0L else (prefs[Keys.TODAY_TOTAL_MILLIS] ?: 0L)
            prefs[Keys.TODAY_TOTAL_MILLIS] = base + millis
            prefs[Keys.TODAY_EPOCH_DAY] = today
            if (stale) prefs[Keys.NOTIFIED_TODAY] = false
        }
    }

    suspend fun setDailyLimit(minutes: Int) {
        dataStore.edit { prefs -> prefs[Keys.DAILY_LIMIT_MINUTES] = minutes }
    }

    /**
     * Does not touch the stored day marker — if the day has since rolled
     * over, [isStale] makes this write read back as false regardless, so
     * a notification fired late on a previous day can't suppress today's.
     */
    suspend fun setNotifiedToday(notified: Boolean) {
        dataStore.edit { prefs -> prefs[Keys.NOTIFIED_TODAY] = notified }
    }

    private fun isStale(prefs: Preferences): Boolean {
        val storedDay = prefs[Keys.TODAY_EPOCH_DAY] ?: return false
        return storedDay != currentEpochDay()
    }

    private fun currentEpochDay(): Int = LocalDate.now().toEpochDay().toInt()
}
