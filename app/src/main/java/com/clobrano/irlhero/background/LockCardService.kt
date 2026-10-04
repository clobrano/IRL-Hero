package com.clobrano.irlhero.background

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.clobrano.irlhero.MainActivity
import com.clobrano.irlhero.R
import com.clobrano.irlhero.data.Repository
import com.clobrano.irlhero.domain.Format
import kotlinx.coroutines.launch

/**
 * Optional lock-screen stats card (off by default). Runs only while the user keeps the card
 * turned on. The card is the foreground-service notification itself:
 * - on screen off it starts a system chronometer counting up from the lock time, so the app
 *   does no work while the screen is off;
 * - on screen on it refreshes today's numbers so a glance shows current stats;
 * - on unlock it collapses to a quiet summary.
 */
class LockCardService : LifecycleService() {

    private var lockedAt: Long? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    if (lockedAt == null) lockedAt = System.currentTimeMillis()
                    refresh()
                }
                Intent.ACTION_SCREEN_ON -> refresh()
                Intent.ACTION_USER_PRESENT -> {
                    lockedAt = null
                    refresh()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel(this)
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, build(getString(R.string.card_title_idle), ""), type)
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        refresh()
    }

    override fun onDestroy() {
        unregisterReceiver(receiver)
        super.onDestroy()
    }

    private fun refresh() {
        lifecycleScope.launch {
            val repo = Repository.get(this@LockCardService)
            repo.sync()
            val d = repo.dashboard()
            val stats = getString(
                R.string.card_stats,
                Format.duration(d.today.irlMillis),
                Format.percent(d.today.goalProgress),
                resources.getQuantityString(R.plurals.streak_days, d.hero.streakDays, d.hero.streakDays),
            )
            val locked = lockedAt
            val notification = if (locked != null) {
                build(getString(R.string.card_title_locked), stats, chronometerBase = locked)
            } else {
                val last = d.lastSession?.irlMillis?.let { Format.duration(it) } ?: "–"
                build(getString(R.string.card_title_idle), getString(R.string.card_summary, last, Format.duration(d.today.irlMillis)))
            }
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
        }
    }

    private fun build(title: String, text: String, chronometerBase: Long? = null): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_hero)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .apply {
                if (chronometerBase != null) {
                    setWhen(chronometerBase)
                    setShowWhen(true)
                    setUsesChronometer(true)
                } else {
                    setShowWhen(false)
                }
            }
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "lock_card"
        private const val NOTIFICATION_ID = 1

        fun createChannel(context: Context) {
            val channel = NotificationChannel(
                CHANNEL_ID, context.getString(R.string.card_channel), NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.card_channel_description)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setShowBadge(false)
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, LockCardService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LockCardService::class.java))
        }
    }
}
