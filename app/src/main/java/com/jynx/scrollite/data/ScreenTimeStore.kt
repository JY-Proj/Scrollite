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
 * Limit settings and per-day flags. Watch time itself lives in the
 * usage_sessions table (see [UsageRepository]).
 *
 * Per-day values are stamped with the epoch day they belong to and read back
 * as empty once that day has passed, so nothing needs resetting at midnight.
 */
class ScreenTimeStore(private val context: Context) {

    private object Keys {
        val DAILY_LIMIT_MINUTES = intPreferencesKey("daily_limit_minutes")
        val NOTIFIED_DAY = intPreferencesKey("notified_day")
        val EXTENSION_DAY = intPreferencesKey("extension_day")
        val EXTENSION_MILLIS = longPreferencesKey("extension_millis_today")
        val EXTENSIONS_USED = intPreferencesKey("extensions_used_today")
        val STRICTNESS = stringPreferencesKey("strictness")
        val LOOSENING_DAY = intPreferencesKey("loosening_day")
        val LOOSENINGS_TODAY = intPreferencesKey("loosenings_today")

        // Older builds scheduled a looser strictness for the next day; read once as the choice
        val LEGACY_PENDING_STRICTNESS = stringPreferencesKey("pending_strictness")
        val LEGACY_PENDING_STRICTNESS_FROM_DAY = intPreferencesKey("pending_strictness_from_day")

        // Before usage_sessions, today's total lived here; read once by takeLegacyTodayTotal()
        val LEGACY_TODAY_TOTAL_MILLIS = longPreferencesKey("today_total_millis")
        val LEGACY_TODAY_EPOCH_DAY = intPreferencesKey("today_epoch_day")
        val LEGACY_NOTIFIED_TODAY = booleanPreferencesKey("notified_today")
    }

    private companion object {
        const val DEFAULT_DAILY_LIMIT_MINUTES = 30
    }

    private val dataStore get() = context.screenTimeDataStore

    val dailyLimitFlow: Flow<Int> = dataStore.data.map { prefs ->
        prefs[Keys.DAILY_LIMIT_MINUTES] ?: DEFAULT_DAILY_LIMIT_MINUTES
    }

    /** Extra watch time granted today on top of the daily limit, in milliseconds. */
    val extensionTodayFlow: Flow<Long> = dataStore.data.map { prefs ->
        if (prefs[Keys.EXTENSION_DAY] == currentEpochDay()) prefs[Keys.EXTENSION_MILLIS] ?: 0L else 0L
    }

    /** How many times the user extended their limit today. */
    val extensionsUsedTodayFlow: Flow<Int> = dataStore.data.map { prefs ->
        if (prefs[Keys.EXTENSION_DAY] == currentEpochDay()) prefs[Keys.EXTENSIONS_USED] ?: 0 else 0
    }

    val strictnessFlow: Flow<Strictness> = dataStore.data.map(::strictnessOf)

    /**
     * How many times today the user loosened a setting (raised the limit or
     * allowed more extra time). The confirmation gets gentler-sadder with each.
     */
    val looseningsTodayFlow: Flow<Int> = dataStore.data.map { prefs ->
        if (prefs[Keys.LOOSENING_DAY] == currentEpochDay()) prefs[Keys.LOOSENINGS_TODAY] ?: 0 else 0
    }

    suspend fun getDailyLimit(): Int = dailyLimitFlow.first()

    suspend fun getExtensionToday(): Long = extensionTodayFlow.first()

    suspend fun getExtensionsUsedToday(): Int = extensionsUsedTodayFlow.first()

    suspend fun getStrictness(): Strictness = strictnessFlow.first()

    /** Whether the once-per-day limit notification has already fired today. */
    suspend fun isNotifiedToday(): Boolean = dataStore.data.first()[Keys.NOTIFIED_DAY] == currentEpochDay()

    suspend fun markNotifiedToday() {
        dataStore.edit { prefs -> prefs[Keys.NOTIFIED_DAY] = currentEpochDay() }
    }

    /** Adds [millis] of extra time for today, without touching the daily limit itself. */
    suspend fun grantExtension(millis: Long) {
        val today = currentEpochDay()
        dataStore.edit { prefs ->
            val sameDay = prefs[Keys.EXTENSION_DAY] == today
            val millisSoFar = if (sameDay) prefs[Keys.EXTENSION_MILLIS] ?: 0L else 0L
            val usedSoFar = if (sameDay) prefs[Keys.EXTENSIONS_USED] ?: 0 else 0
            prefs[Keys.EXTENSION_DAY] = today
            prefs[Keys.EXTENSION_MILLIS] = millisSoFar + millis
            prefs[Keys.EXTENSIONS_USED] = usedSoFar + 1
        }
    }

    /**
     * Applies immediately. Changing the limit re-arms today's notification —
     * "notified today" was about the old limit, so without this, raising it
     * mid-day would mean crossing the new one silently.
     */
    suspend fun setDailyLimit(minutes: Int) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.DAILY_LIMIT_MINUTES] ?: DEFAULT_DAILY_LIMIT_MINUTES
            prefs[Keys.DAILY_LIMIT_MINUTES] = minutes
            if (minutes != current) prefs.remove(Keys.NOTIFIED_DAY)
            if (minutes > current) prefs.countLoosening()
        }
    }

    /** Applies immediately; the UI confirms looser choices first. */
    suspend fun setStrictness(requested: Strictness) {
        dataStore.edit { prefs ->
            if (requested.isLooserThan(strictnessOf(prefs))) prefs.countLoosening()
            prefs[Keys.STRICTNESS] = requested.name
            prefs.remove(Keys.LEGACY_PENDING_STRICTNESS)
            prefs.remove(Keys.LEGACY_PENDING_STRICTNESS_FROM_DAY)
        }
    }

    /**
     * One-time hand-off from the old single "today's total" value: returns it
     * if it belongs to today (0 otherwise) and deletes the legacy keys, so a
     * second call returns 0. Also carries over today's notified flag.
     */
    suspend fun takeLegacyTodayTotal(): Long {
        var total = 0L
        val today = currentEpochDay()
        dataStore.edit { prefs ->
            if (prefs[Keys.LEGACY_TODAY_EPOCH_DAY] == today) {
                total = prefs[Keys.LEGACY_TODAY_TOTAL_MILLIS] ?: 0L
                if (prefs[Keys.LEGACY_NOTIFIED_TODAY] == true) prefs[Keys.NOTIFIED_DAY] = today
            }
            prefs.remove(Keys.LEGACY_TODAY_TOTAL_MILLIS)
            prefs.remove(Keys.LEGACY_TODAY_EPOCH_DAY)
            prefs.remove(Keys.LEGACY_NOTIFIED_TODAY)
        }
        return total
    }

    /** A looser choice an older build scheduled for "tomorrow" counts as chosen now. */
    private fun strictnessOf(prefs: Preferences): Strictness =
        Strictness.fromNameOrNull(prefs[Keys.LEGACY_PENDING_STRICTNESS])
            ?: Strictness.fromNameOrNull(prefs[Keys.STRICTNESS])
            ?: Strictness.DEFAULT

    private fun MutablePreferences.countLoosening() {
        val today = currentEpochDay()
        val soFar = if (this[Keys.LOOSENING_DAY] == today) this[Keys.LOOSENINGS_TODAY] ?: 0 else 0
        this[Keys.LOOSENING_DAY] = today
        this[Keys.LOOSENINGS_TODAY] = soFar + 1
    }

    private fun currentEpochDay(): Int = LocalDate.now().toEpochDay().toInt()
}
