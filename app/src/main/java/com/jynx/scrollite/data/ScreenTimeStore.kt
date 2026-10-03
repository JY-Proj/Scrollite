package com.jynx.scrollite.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
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
        val EXTENSION_MILLIS_TODAY = longPreferencesKey("extension_millis_today")
        val EXTENSIONS_USED_TODAY = intPreferencesKey("extensions_used_today")
        val STRICTNESS = stringPreferencesKey("strictness")
        val PENDING_STRICTNESS = stringPreferencesKey("pending_strictness")
        val PENDING_STRICTNESS_FROM_DAY = intPreferencesKey("pending_strictness_from_day")
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

    /** Extra watch time granted today on top of the daily limit, in milliseconds. */
    val extensionTodayFlow: Flow<Long> = dataStore.data.map { prefs ->
        if (isStale(prefs)) 0L else prefs[Keys.EXTENSION_MILLIS_TODAY] ?: 0L
    }

    /** How many times the user extended their limit today. */
    val extensionsUsedTodayFlow: Flow<Int> = dataStore.data.map { prefs ->
        if (isStale(prefs)) 0 else prefs[Keys.EXTENSIONS_USED_TODAY] ?: 0
    }

    /** The strictness in effect today (a looser pending one counts once its day arrives). */
    val strictnessFlow: Flow<Strictness> = dataStore.data.map { prefs ->
        strictnessSchedule(prefs).effectiveOn(currentEpochDay())
    }

    /** A looser strictness the user picked that only takes effect tomorrow, if any. */
    val pendingStrictnessFlow: Flow<Strictness?> = dataStore.data.map { prefs ->
        strictnessSchedule(prefs).pendingOn(currentEpochDay())
    }

    suspend fun getTodayTotal(): Long = todayTotalFlow.first()

    suspend fun getStrictness(): Strictness = strictnessFlow.first()

    suspend fun getExtensionToday(): Long = extensionTodayFlow.first()

    suspend fun getExtensionsUsedToday(): Int = extensionsUsedTodayFlow.first()

    suspend fun getDailyLimit(): Int = dailyLimitFlow.first()

    suspend fun isNotifiedToday(): Boolean = notifiedTodayFlow.first()

    /**
     * Adds [millis] to today's running total. If the stored total belongs
     * to a previous day it is discarded (reset, not accumulated onto) and
     * the per-day flags (notified, extensions) are cleared for the new day.
     */
    suspend fun addElapsedTime(millis: Long) {
        val today = currentEpochDay()
        dataStore.edit { prefs ->
            val stale = prefs[Keys.TODAY_EPOCH_DAY] != today
            val base = if (stale) 0L else (prefs[Keys.TODAY_TOTAL_MILLIS] ?: 0L)
            prefs[Keys.TODAY_TOTAL_MILLIS] = base + millis
            prefs[Keys.TODAY_EPOCH_DAY] = today
            if (stale) resetDailyFlags(prefs)
        }
    }

    /**
     * Adds [millis] of extra time for today, without touching the daily
     * limit itself. Rolls the day over first (like [addElapsedTime]) so an
     * extension can never be credited to a previous day's counters.
     */
    suspend fun grantExtension(millis: Long) {
        val today = currentEpochDay()
        dataStore.edit { prefs ->
            if (prefs[Keys.TODAY_EPOCH_DAY] != today) {
                prefs[Keys.TODAY_TOTAL_MILLIS] = 0L
                prefs[Keys.TODAY_EPOCH_DAY] = today
                resetDailyFlags(prefs)
            }
            prefs[Keys.EXTENSION_MILLIS_TODAY] = (prefs[Keys.EXTENSION_MILLIS_TODAY] ?: 0L) + millis
            prefs[Keys.EXTENSIONS_USED_TODAY] = (prefs[Keys.EXTENSIONS_USED_TODAY] ?: 0) + 1
        }
    }

    /**
     * Changing the limit re-arms today's notification — "notified today" was
     * about the old limit, so without this, raising it mid-day would mean
     * crossing the new one silently.
     */
    suspend fun setDailyLimit(minutes: Int) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.DAILY_LIMIT_MINUTES] ?: DEFAULT_DAILY_LIMIT_MINUTES
            prefs[Keys.DAILY_LIMIT_MINUTES] = minutes
            if (minutes != current) prefs[Keys.NOTIFIED_TODAY] = false
        }
    }

    /** See [StrictnessSchedule] — stricter applies now, looser from tomorrow. */
    suspend fun setStrictness(requested: Strictness) {
        dataStore.edit { prefs ->
            val next = strictnessSchedule(prefs).request(requested, currentEpochDay())
            prefs[Keys.STRICTNESS] = next.active.name
            val pending = next.pending
            val pendingFrom = next.pendingFromEpochDay
            if (pending != null && pendingFrom != null) {
                prefs[Keys.PENDING_STRICTNESS] = pending.name
                prefs[Keys.PENDING_STRICTNESS_FROM_DAY] = pendingFrom
            } else {
                prefs.remove(Keys.PENDING_STRICTNESS)
                prefs.remove(Keys.PENDING_STRICTNESS_FROM_DAY)
            }
        }
    }

    /**
     * Does not touch the stored day marker — if the day has since rolled
     * over, [isStale] makes this write read back as false regardless, so
     * a notification fired late on a previous day can't suppress today's.
     */
    suspend fun setNotifiedToday(notified: Boolean) {
        dataStore.edit { prefs -> prefs[Keys.NOTIFIED_TODAY] = notified }
    }

    private fun resetDailyFlags(prefs: MutablePreferences) {
        prefs[Keys.NOTIFIED_TODAY] = false
        prefs[Keys.EXTENSION_MILLIS_TODAY] = 0L
        prefs[Keys.EXTENSIONS_USED_TODAY] = 0
    }

    private fun strictnessSchedule(prefs: Preferences) = StrictnessSchedule(
        active = Strictness.fromNameOrNull(prefs[Keys.STRICTNESS]) ?: Strictness.DEFAULT,
        pending = Strictness.fromNameOrNull(prefs[Keys.PENDING_STRICTNESS]),
        pendingFromEpochDay = prefs[Keys.PENDING_STRICTNESS_FROM_DAY],
    )

    private fun isStale(prefs: Preferences): Boolean {
        val storedDay = prefs[Keys.TODAY_EPOCH_DAY] ?: return false
        return storedDay != currentEpochDay()
    }

    private fun currentEpochDay(): Int = LocalDate.now().toEpochDay().toInt()
}
