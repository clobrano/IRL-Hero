package com.clobrano.irlhero.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.clobrano.irlhero.data.Settings as AppSettings

/** Onboarding (SE-1): welcome, how it works, sleep and goal, usage access, battery. */
@Composable
fun Onboarding(
    state: UiState,
    onSettings: ((AppSettings) -> AppSettings) -> Unit,
    onDone: () -> Unit,
) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    val context = LocalContext.current

    @Composable
    fun page(title: String, body: String, primary: String, onPrimary: () -> Unit, secondary: String? = null, onSecondary: () -> Unit = {}, extra: @Composable () -> Unit = {}) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(48.dp))
            Text("IN REAL LIFE HERO", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(body, style = MaterialTheme.typography.bodyLarge)
            extra()
            Spacer(Modifier.height(16.dp))
            Button(onClick = onPrimary, modifier = Modifier.fillMaxWidth()) { Text(primary) }
            if (secondary != null) TextButton(onClick = onSecondary, modifier = Modifier.fillMaxWidth()) { Text(secondary) }
        }
    }

    when (step) {
        0 -> page(
            "Every minute your phone stays locked is a minute in real life.",
            "Track the time you spend away from your screen, beat your records and share your wins.",
            "Get started", { step = 1 },
        )
        1 -> page(
            "How it works",
            "1. Locking your phone starts the clock.\n\n2. Peeking at the lock screen is fine: checking the time or a notification does not stop it.\n\n3. Unlocking stops it. Sessions under ${state.settings.minSessionMinutes} minutes and your sleep hours do not count.",
            "Next", { step = 2 },
        )
        2 -> page(
            "Your day",
            "Set your sleep hours and a daily goal. You can change them any time in Settings.",
            "Next", { step = 3 },
        ) {
            SleepAndGoalEditor(state.settings, onSettings)
        }
        3 -> page(
            "Allow usage access",
            "In Real Life Hero reads only when your phone is locked, unlocked and when the screen turns on or off. It never reads which apps you use, and your data never leaves your phone.",
            if (state.hasUsageAccess) "Next" else "Open settings",
            {
                if (state.hasUsageAccess) step = 4
                else context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            },
        ) {
            Text(
                if (state.hasUsageAccess) "Access granted." else "Find In Real Life Hero in the list and turn access on, then come back.",
                style = MaterialTheme.typography.bodyMedium,
                color = if (state.hasUsageAccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        else -> page(
            "Keep tracking reliable",
            "Some phones stop apps in the background. Allowing In Real Life Hero to ignore battery optimisation keeps the once-a-day backup of your history reliable. It has no visible effect on battery.",
            if (state.ignoresBatteryOptimizations) "Finish" else "Allow",
            {
                if (state.ignoresBatteryOptimizations) onDone() else requestIgnoreBattery(context)
            },
            secondary = if (state.ignoresBatteryOptimizations) null else "Skip",
            onSecondary = onDone,
        )
    }
}

@SuppressLint("BatteryLife")
fun requestIgnoreBattery(context: android.content.Context) {
    context.startActivity(
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, "package:${context.packageName}".toUri())
    )
}

@Composable
fun SleepAndGoalEditor(s: AppSettings, onSettings: ((AppSettings) -> AppSettings) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Stepper("Sleep starts", hhmm(s.sleepStartMinute),
            onMinus = { onSettings { it.copy(sleepStartMinute = (it.sleepStartMinute - 30).mod(1440)) } },
            onPlus = { onSettings { it.copy(sleepStartMinute = (it.sleepStartMinute + 30).mod(1440)) } })
        Stepper("Sleep ends", hhmm(s.sleepEndMinute),
            onMinus = { onSettings { it.copy(sleepEndMinute = (it.sleepEndMinute - 30).mod(1440)) } },
            onPlus = { onSettings { it.copy(sleepEndMinute = (it.sleepEndMinute + 30).mod(1440)) } })
        Stepper("Daily goal", "${s.goalPercent}% of waking hours",
            onMinus = { onSettings { it.copy(goalPercent = (it.goalPercent - 5).coerceAtLeast(5)) } },
            onPlus = { onSettings { it.copy(goalPercent = (it.goalPercent + 5).coerceAtMost(95)) } })
    }
}

fun hhmm(minute: Int) = "%02d:%02d".format(minute / 60, minute % 60)

@Composable
fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
        TextButton(onClick = onMinus) { Text("−", style = MaterialTheme.typography.titleLarge) }
        TextButton(onClick = onPlus) { Text("+", style = MaterialTheme.typography.titleLarge) }
    }
}
