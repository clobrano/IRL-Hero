package com.clobrano.irlhero.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import com.clobrano.irlhero.domain.EventType
import com.clobrano.irlhero.domain.ScreenEvent

/**
 * Reads lock, unlock and screen events from the system usage log. Only event types and
 * timestamps are kept: no app names are ever read or stored.
 */
class UsageEventSource(private val context: Context) {

    fun hasAccess(): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java)
        @Suppress("DEPRECATION")
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun read(fromMillis: Long, toMillis: Long): List<ScreenEvent> {
        if (!hasAccess()) return emptyList()
        val usm = context.getSystemService(UsageStatsManager::class.java)
        val events = usm.queryEvents(fromMillis, toMillis) ?: return emptyList()
        val out = mutableListOf<ScreenEvent>()
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val type = map(e.eventType) ?: continue
            out += ScreenEvent(type, e.timeStamp)
        }
        return out
    }

    private fun map(code: Int): EventType? = when (code) {
        UsageEvents.Event.SCREEN_NON_INTERACTIVE -> EventType.SCREEN_OFF
        UsageEvents.Event.SCREEN_INTERACTIVE -> EventType.SCREEN_ON
        UsageEvents.Event.KEYGUARD_SHOWN -> EventType.KEYGUARD_SHOWN
        UsageEvents.Event.KEYGUARD_HIDDEN -> EventType.KEYGUARD_HIDDEN
        DEVICE_SHUTDOWN -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) EventType.SHUTDOWN else null
        DEVICE_STARTUP -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) EventType.STARTUP else null
        else -> null
    }

    private companion object {
        // UsageEvents.Event.DEVICE_SHUTDOWN / DEVICE_STARTUP (API 29); values inlined for minSdk 28.
        const val DEVICE_SHUTDOWN = 26
        const val DEVICE_STARTUP = 27
    }
}
