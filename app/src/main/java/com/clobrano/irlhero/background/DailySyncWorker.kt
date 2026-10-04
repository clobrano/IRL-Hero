package com.clobrano.irlhero.background

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.clobrano.irlhero.data.Repository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Safety net only: copies new events into the app database before Android purges them.
 * It shows nothing to the user; reports appear only when the app is opened.
 */
class DailySyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        Repository.get(applicationContext).sync()
        return Result.success()
    }

    companion object {
        private const val NAME = "daily-sync"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<DailySyncWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}

/** Re-arms the daily sync and, if the user turned it on, the lock-screen card after a reboot. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        DailySyncWorker.schedule(context)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = Repository.get(context)
                repo.sync()
                if (repo.settingsStore.current().lockCardEnabled) LockCardService.start(context)
            } finally {
                pending.finish()
            }
        }
    }
}
