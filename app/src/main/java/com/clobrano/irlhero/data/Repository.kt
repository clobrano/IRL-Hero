package com.clobrano.irlhero.data

import android.content.Context
import com.clobrano.irlhero.domain.Celebration
import com.clobrano.irlhero.domain.Celebrations
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

    /**
     * [countOpenSessionUntilNow]: count a session still open in the log as ending now. True for
     * the app on screen (the phone is unlocked) and the lock-screen card (the running session).
     */
    suspend fun dashboard(now: Long = System.currentTimeMillis(), countOpenSessionUntilNow: Boolean = false): Dashboard {
        var settings = settingsStore.current()
        if (settings.onboarded && settings.pointsSinceMillis == 0L) {
            // Installs from before points had a start date begin earning from today.
            settingsStore.update { it.copy(pointsSinceMillis = now) }
            settings = settingsStore.current()
        }
        val events = events()
        val zone = ZoneId.systemDefault()
        val pointsFrom = Instant.ofEpochMilli(settings.pointsSinceMillis.takeIf { it > 0 } ?: now).atZone(zone).toLocalDate()
        return withContext(Dispatchers.Default) {
            DashboardBuilder.build(events, settings.rules, zone, now, pointsFrom, countOpenSessionUntilNow)
        }
    }

    /**
     * Celebrations for the latest finished day, week and month, each judged once (see
     * [Celebrations]): nothing pops up for a period still running, nor twice for the same one.
     */
    suspend fun pendingCelebrations(d: Dashboard): List<Celebration> {
        val settings = settingsStore.current()
        val pointsFrom = Instant.ofEpochMilli(settings.pointsSinceMillis.takeIf { it > 0 } ?: d.nowMillis)
            .atZone(ZoneId.systemDefault()).toLocalDate()
        val check = Celebrations.check(d, settingsStore.judged(), pointsFrom)
        settingsStore.setJudged(check.judged)
        return check.celebrations
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
        settingsStore.update { it.copy(pointsSinceMillis = System.currentTimeMillis()) }
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
