package com.clobrano.irlhero.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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

/** What the user has already been shown, so celebrations appear once, on app open. */
data class Celebrated(
    val longestSession: Long = 0,
    val bestDay: Long = 0,
    val bestWeek: Long = 0,
    val bestMonth: Long = 0,
    val lastGoalDate: String = "",
)

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
        val celLongest = longPreferencesKey("cel_longest")
        val celDay = longPreferencesKey("cel_day")
        val celWeek = longPreferencesKey("cel_week")
        val celMonth = longPreferencesKey("cel_month")
        val celGoal = stringPreferencesKey("cel_goal_date")
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

    suspend fun celebrated(): Celebrated = context.dataStore.data.first().let {
        Celebrated(
            longestSession = it[Keys.celLongest] ?: 0,
            bestDay = it[Keys.celDay] ?: 0,
            bestWeek = it[Keys.celWeek] ?: 0,
            bestMonth = it[Keys.celMonth] ?: 0,
            lastGoalDate = it[Keys.celGoal] ?: "",
        )
    }

    suspend fun setCelebrated(c: Celebrated) = context.dataStore.edit {
        it[Keys.celLongest] = c.longestSession
        it[Keys.celDay] = c.bestDay
        it[Keys.celWeek] = c.bestWeek
        it[Keys.celMonth] = c.bestMonth
        it[Keys.celGoal] = c.lastGoalDate
    }

    suspend fun clearProgress() = context.dataStore.edit {
        it.remove(Keys.lastSync)
        it.remove(Keys.celLongest); it.remove(Keys.celDay); it.remove(Keys.celWeek); it.remove(Keys.celMonth)
        it.remove(Keys.celGoal)
    }
}
