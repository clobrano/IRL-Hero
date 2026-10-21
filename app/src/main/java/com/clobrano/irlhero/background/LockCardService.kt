package com.clobrano.irlhero.background

import android.app.KeyguardManager
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
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.clobrano.irlhero.MainActivity
import com.clobrano.irlhero.R
import com.clobrano.irlhero.data.Repository
import com.clobrano.irlhero.domain.Format
import com.clobrano.irlhero.domain.GoalOutlook
import com.clobrano.irlhero.domain.LockTimer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

    private val timer = LockTimer()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val now = System.currentTimeMillis()
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> timer.onScreenOff(now)
                Intent.ACTION_SCREEN_ON -> timer.onScreenOn(keyguardLocked(), now)
                Intent.ACTION_USER_PRESENT -> timer.onUnlock(now)
                else -> return
            }
            refresh()
            // Check again shortly after: the log can lag a moment, and the unlock broadcast may
            // never come at all; a later look at the screen and lock state catches both.
            lifecycleScope.launch {
                delay(LATE_EVENTS_DELAY_MILLIS)
                refresh()
            }
        }
    }

    private var lastNotification: Notification? = null

    override fun onCreate() {
        super.onCreate()
        createChannel(this)
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    /** Every startForegroundService call must be answered with startForeground, even when already running. */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        val notification = lastNotification ?: build(getString(R.string.card_title_idle), "")
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
        refresh()
        return START_STICKY
    }

    override fun onDestroy() {
        unregisterReceiver(receiver)
        super.onDestroy()
    }

    private fun refresh() {
        lifecycleScope.launch {
            val repo = Repository.get(this@LockCardService)
            repo.sync()
            // Count the running session so far: while locked, today's total includes it;
            // right after an unlock, it is the session that just ended.
            val d = repo.dashboard(countOpenSessionUntilNow = true)
            // Line the timer up with the event log, the same source the app's stats use.
            val sessionRunning = !getSystemService(PowerManager::class.java).isInteractive || keyguardLocked()
            timer.reconcile(sessionRunning, d.openSessionStart, System.currentTimeMillis())
            val t = d.today
            val outlook = GoalOutlook.of(t)
            // Phone use can make the outlook worse without any screen event: look again when it would.
            scheduleOutlookCheck(outlook.worsensInMillis)
            // Every line names what its numbers are (IRL, Phone, day left) and fits on one line.
            val irl = getString(R.string.card_irl, Format.short(t.irlMillis), Format.short(t.goalMillis), Format.percent(t.goalProgress))
            val (warningTitle, warningText) = when (outlook.status) {
                GoalOutlook.Status.AT_RISK -> getString(R.string.card_title_at_risk) to
                    getString(R.string.card_at_risk, Format.short(outlook.neededMillis), Format.short(outlook.phoneLeftMillis))
                GoalOutlook.Status.OUT_OF_REACH -> getString(R.string.card_title_out_of_reach) to
                    getString(R.string.card_out_of_reach, Format.short(outlook.neededMillis), Format.short(outlook.remainingMillis))
                GoalOutlook.Status.MET -> getString(R.string.card_title_met) to null
                else -> null to null
            }
            val locked = timer.lockedAt
            // While locked, "last session" is the one before the session running now.
            val lastSession = if (locked != null) d.lastClosedSession else d.lastSession?.session
            val phone = if (locked != null || lastSession == null) {
                getString(R.string.card_phone, Format.short(t.phoneMillis), Format.short(t.phoneAllowanceMillis))
            } else {
                getString(R.string.card_last_phone, Format.short(d.calc.irlOf(lastSession)), Format.short(t.phoneMillis), Format.short(t.phoneAllowanceMillis))
            }
            val details = getString(
                R.string.card_details, Format.short(t.longestMillis),
                resources.getQuantityString(R.plurals.streak_days, d.hero.streakDays, d.hero.streakDays), d.hero.level.title,
            )
            val title = warningTitle ?: getString(if (locked != null) R.string.card_title_locked else R.string.card_title_idle)
            val expanded = listOfNotNull(warningText?.let { irl }, phone, details).joinToString("\n")
            val notification = build(title, warningText ?: irl, expanded, chronometerBase = locked)
            lastNotification = notification
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
        }
    }

    private var outlookCheck: Job? = null

    private fun scheduleOutlookCheck(inMillis: Long?) {
        outlookCheck?.cancel()
        if (inMillis == null) return
        outlookCheck = lifecycleScope.launch {
            delay(inMillis + OUTLOOK_CHECK_SLACK_MILLIS)
            refresh()
        }
    }

    private fun keyguardLocked() = getSystemService(KeyguardManager::class.java).isKeyguardLocked

    private fun build(title: String, text: String, details: String = "", chronometerBase: Long? = null): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_hero)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setStyle(NotificationCompat.BigTextStyle().bigText(if (details.isEmpty()) text else "$text\n$details"))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
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
        /**
         * Default importance, but no sound or vibration. A low-importance channel counts as
         * "silent", and many phones (Pixel since Android 12) hide silent notifications on the
         * lock screen, which is the one place this card must show. A channel's importance cannot
         * change once created, hence the new id; the old low-importance channel is removed.
         */
        const val CHANNEL_ID = "lock_card_v2"
        private const val LATE_EVENTS_DELAY_MILLIS = 5_000L
        private const val OUTLOOK_CHECK_SLACK_MILLIS = 30_000L
        private const val OLD_CHANNEL_ID = "lock_card"
        private const val NOTIFICATION_ID = 1

        fun createChannel(context: Context) {
            val channel = NotificationChannel(
                CHANNEL_ID, context.getString(R.string.card_channel), NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.card_channel_description)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(null, null)
                enableVibration(false)
                enableLights(false)
                setShowBadge(false)
            }
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.deleteNotificationChannel(OLD_CHANNEL_ID)
            nm.createNotificationChannel(channel)
        }

        /** Whether the card can actually be shown: app notifications on and the channel not blocked. */
        fun canPost(context: Context): Boolean {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (!nm.areNotificationsEnabled()) return false
            val channel = nm.getNotificationChannel(CHANNEL_ID) ?: return true
            return channel.importance != NotificationManager.IMPORTANCE_NONE
        }

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, LockCardService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LockCardService::class.java))
        }
    }
}
