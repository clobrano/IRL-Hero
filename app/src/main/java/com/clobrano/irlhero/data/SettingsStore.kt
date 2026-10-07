package com.clobrano.irlhero.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.clobrano.irlhero.domain.JudgedPeriods
import com.clobrano.irlhero.domain.MINUTE
import com.clobrano.irlhero.domain.SessionRules
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class Settings(
    val onboarded: Boolean = false,
    val goalPercent: Int = 50,
    val minSessionMinutes: Int = 2,
    val sleepStartMinute: Int = 23 * 60,
    val sleepEndMinute: Int = 7 * 60,
    val lockCardEnabled: Boolean = false,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    /** When the user started using the app; only days from then on earn Hero Points (0 = not set). */
    val pointsSinceMillis: Long = 0,
) {
    val rules: SessionRules
        get() = SessionRules(
            minSessionMillis = minSessionMinutes * MINUTE,
            sleepStartMinute = sleepStartMinute,
            sleepEndMinute = sleepEndMinute,
            goalPercent = goalPercent,
        )
}

class SettingsStore(private val context: Context) {
    private object Keys {
        val onboarded = booleanPreferencesKey("onboarded")
        val goal = intPreferencesKey("goal_percent")
        val minSession = intPreferencesKey("min_session_minutes")
        val sleepStart = intPreferencesKey("sleep_start")
        val sleepEnd = intPreferencesKey("sleep_end")
        val lockCard = booleanPreferencesKey("lock_card")
        val theme = stringPreferencesKey("theme")
        val pointsSince = longPreferencesKey("points_since")
        val lastSync = longPreferencesKey("last_sync")
        val judgedDay = stringPreferencesKey("judged_day")
        val judgedWeek = stringPreferencesKey("judged_week")
        val judgedMonth = stringPreferencesKey("judged_month")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { it.toSettings() }

    suspend fun current(): Settings = settings.first()

    private fun Preferences.toSettings() = Settings(
        onboarded = this[Keys.onboarded] ?: false,
        goalPercent = this[Keys.goal] ?: 50,
        minSessionMinutes = this[Keys.minSession] ?: 2,
        sleepStartMinute = this[Keys.sleepStart] ?: (23 * 60),
        sleepEndMinute = this[Keys.sleepEnd] ?: (7 * 60),
        lockCardEnabled = this[Keys.lockCard] ?: false,
        theme = this[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
        pointsSinceMillis = this[Keys.pointsSince] ?: 0L,
    )

    suspend fun update(transform: (Settings) -> Settings) {
        context.dataStore.edit { prefs ->
            val s = transform(prefs.toSettings())
            prefs[Keys.onboarded] = s.onboarded
            prefs[Keys.goal] = s.goalPercent
            prefs[Keys.minSession] = s.minSessionMinutes
            prefs[Keys.sleepStart] = s.sleepStartMinute
            prefs[Keys.sleepEnd] = s.sleepEndMinute
            prefs[Keys.lockCard] = s.lockCardEnabled
            prefs[Keys.theme] = s.theme.name
            prefs[Keys.pointsSince] = s.pointsSinceMillis
        }
    }

    suspend fun lastSync(): Long = context.dataStore.data.first()[Keys.lastSync] ?: 0L
    suspend fun setLastSync(value: Long) = context.dataStore.edit { it[Keys.lastSync] = value }

    /** The latest periods already judged for celebrations, so each is judged once. */
    suspend fun judged(): JudgedPeriods = context.dataStore.data.first().let {
        JudgedPeriods(day = it[Keys.judgedDay] ?: "", week = it[Keys.judgedWeek] ?: "", month = it[Keys.judgedMonth] ?: "")
    }

    suspend fun setJudged(j: JudgedPeriods) = context.dataStore.edit {
        it[Keys.judgedDay] = j.day
        it[Keys.judgedWeek] = j.week
        it[Keys.judgedMonth] = j.month
    }

    suspend fun clearProgress() = context.dataStore.edit {
        it.remove(Keys.lastSync)
        it.remove(Keys.judgedDay); it.remove(Keys.judgedWeek); it.remove(Keys.judgedMonth)
    }
}
