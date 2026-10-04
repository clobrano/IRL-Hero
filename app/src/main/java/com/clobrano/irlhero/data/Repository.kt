package com.clobrano.irlhero.data

import android.content.Context
import com.clobrano.irlhero.domain.DAY
import com.clobrano.irlhero.domain.Dashboard
import com.clobrano.irlhero.domain.DashboardBuilder
import com.clobrano.irlhero.domain.EventType
import com.clobrano.irlhero.domain.Format
import com.clobrano.irlhero.domain.MINUTE
import com.clobrano.irlhero.domain.ScreenEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneId

/** A reason to celebrate, shown once when the app is opened. */
data class Celebration(val title: String, val value: String, val detail: String, val shareTitle: String)

class Repository(context: Context) {
    private val appContext = context.applicationContext
    private val dao = IrlDatabase.get(appContext).rawEvents()
    val usage = UsageEventSource(appContext)
    val settingsStore = SettingsStore(appContext)
    private val mutex = Mutex()

    /** Copies new events from the system usage log into the app's database. */
    suspend fun sync(now: Long = System.currentTimeMillis()) = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!usage.hasAccess()) return@withLock
            val last = settingsStore.lastSync()
            // On first run, backfill whatever the system still keeps (usually several days).
            val from = if (last == 0L) now - BACKFILL_DAYS * DAY else last - OVERLAP_MILLIS
            val events = usage.read(from, now)
            dao.insertAll(events.map { RawEventEntity(type = it.type.name, timestampUtc = it.timeMillis) })
            settingsStore.setLastSync(now)
            dao.deleteBefore(now - (DashboardBuilder.MAX_HISTORY_DAYS + 1) * DAY)
        }
    }

    suspend fun events(): List<ScreenEvent> = withContext(Dispatchers.IO) {
        dao.all().mapNotNull { e ->
            runCatching { EventType.valueOf(e.type) }.getOrNull()?.let { ScreenEvent(it, e.timestampUtc) }
        }
    }

    suspend fun dashboard(now: Long = System.currentTimeMillis()): Dashboard {
        val settings = settingsStore.current()
        val events = events()
        return withContext(Dispatchers.Default) {
            DashboardBuilder.build(events, settings.rules, ZoneId.systemDefault(), now)
        }
    }

    /**
     * New records and goal days since the last visit. The very first computation only sets
     * the baseline, so the history backfilled at install does not fire a burst of confetti.
     */
    suspend fun pendingCelebrations(d: Dashboard): List<Celebration> {
        val seen = settingsStore.celebrated()
        val r = d.records
        val firstRun = seen.longestSession == 0L && seen.bestDay == 0L && seen.bestWeek == 0L && seen.bestMonth == 0L
        val out = mutableListOf<Celebration>()
        if (!firstRun) {
            r.longestSession?.takeIf { it.irlMillis > seen.longestSession }?.let {
                out += Celebration("New record: longest session", Format.duration(it.irlMillis),
                    "Previous best: ${Format.duration(seen.longestSession)}", "Longest session")
            }
            r.bestDay?.takeIf { it.irlMillis > seen.bestDay }?.let {
                out += Celebration("New record: best day", Format.duration(it.irlMillis),
                    "${Format.day(it.date)} · previous best: ${Format.duration(seen.bestDay)}", "Best day")
            }
            r.bestWeek?.takeIf { it.irlMillis > seen.bestWeek }?.let {
                out += Celebration("New record: best week", Format.duration(it.irlMillis),
                    "Week of ${Format.day(it.weekStart)}", "Best week")
            }
            r.bestMonth?.takeIf { it.irlMillis > seen.bestMonth }?.let {
                out += Celebration("New record: best month", Format.duration(it.irlMillis),
                    Format.month(it.month), "Best month")
            }
        }
        val goalDay = d.allDays.lastOrNull { it.goalMet && it.date.toString() > seen.lastGoalDate }
        if (!firstRun && goalDay != null) {
            out += Celebration("Daily goal reached", Format.duration(goalDay.irlMillis),
                "${Format.day(goalDay.date)} · streak ${d.hero.streakDays} days", "Daily goal")
        }
        settingsStore.setCelebrated(
            Celebrated(
                longestSession = maxOf(seen.longestSession, r.longestSession?.irlMillis ?: 0),
                bestDay = maxOf(seen.bestDay, r.bestDay?.irlMillis ?: 0),
                bestWeek = maxOf(seen.bestWeek, r.bestWeek?.irlMillis ?: 0),
                bestMonth = maxOf(seen.bestMonth, r.bestMonth?.irlMillis ?: 0),
                lastGoalDate = maxOf(seen.lastGoalDate, d.allDays.lastOrNull { it.goalMet }?.date?.toString() ?: ""),
            )
        )
        return out
    }

    /** CSV of all scored sessions (SE-2). */
    suspend fun exportCsv(d: Dashboard): File = withContext(Dispatchers.IO) {
        val dir = File(appContext.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "irl-hero-sessions.csv")
        val zone = ZoneId.systemDefault()
        file.bufferedWriter().use { w ->
            w.appendLine("start,end,duration_minutes,irl_minutes,glances")
            for (s in d.sessions) {
                w.appendLine(
                    listOf(
                        Instant.ofEpochMilli(s.startMillis).atZone(zone).toOffsetDateTime(),
                        Instant.ofEpochMilli(s.endMillis).atZone(zone).toOffsetDateTime(),
                        s.durationMillis / MINUTE,
                        d.calc.irlOf(s) / MINUTE,
                        s.glances,
                    ).joinToString(",")
                )
            }
        }
        file
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        dao.deleteAll()
        settingsStore.clearProgress()
        // Start fresh from now instead of re-importing the history the system still keeps.
        settingsStore.setLastSync(System.currentTimeMillis())
    }

    companion object {
        private const val BACKFILL_DAYS = 10L
        private const val OVERLAP_MILLIS = 5 * MINUTE

        @Volatile private var instance: Repository? = null
        fun get(context: Context): Repository = instance ?: synchronized(this) {
            instance ?: Repository(context).also { instance = it }
        }
    }
}
