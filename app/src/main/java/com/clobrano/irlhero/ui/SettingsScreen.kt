package com.clobrano.irlhero.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.clobrano.irlhero.data.Settings
import com.clobrano.irlhero.data.ThemeMode

@Composable
fun SettingsScreen(
    state: UiState,
    onSettings: ((Settings) -> Settings) -> Unit,
    onLockCard: (Boolean) -> Unit,
    onExport: () -> Unit,
    onDeleteAll: () -> Unit,
) {
    val s = state.settings
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) onLockCard(true)
    }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionCard(title = "Your day") {
            SleepAndGoalEditor(s, onSettings)
        }
        SectionCard(title = "Session rules") {
            Text("Minimum session: ${s.minSessionMinutes} min", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = s.minSessionMinutes.toFloat(),
                onValueChange = { v -> onSettings { it.copy(minSessionMinutes = v.toInt()) } },
                valueRange = 1f..10f, steps = 8,
            )
            Text(
                "Shorter breaks are discarded. Looking at the lock screen never ends a session, and phone calls count as real life.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SectionCard(title = "Lock-screen stats") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Show live stats on the lock screen", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "A running IRL timer, today's total, goal and streak while your phone is locked. Off by default.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = s.lockCardEnabled, onCheckedChange = { enable ->
                    val needsPermission = Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                        context, Manifest.permission.POST_NOTIFICATIONS,
                    ) != PackageManager.PERMISSION_GRANTED
                    if (enable && needsPermission) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else onLockCard(enable)
                })
            }
        }
        SectionCard(title = "Theme") {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ThemeMode.entries.forEachIndexed { i, mode ->
                    SegmentedButton(
                        selected = s.theme == mode,
                        onClick = { onSettings { it.copy(theme = mode) } },
                        shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
                    ) { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) }
                }
            }
        }
        SectionCard(title = "Tracking health") {
            LabeledValue("Usage access", if (state.hasUsageAccess) "Granted" else "Missing")
            LabeledValue("Battery optimisation", if (state.ignoresBatteryOptimizations) "Ignored" else "Active")
            if (!state.ignoresBatteryOptimizations) {
                TextButton(onClick = { requestIgnoreBattery(context) }) { Text("Allow background backup") }
            }
        }
        SectionCard(title = "Your data") {
            Text(
                "Everything stays on this phone. No account, no internet access.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onExport, modifier = Modifier.weight(1f)) { Text("Export CSV") }
                OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.weight(1f)) { Text("Delete all") }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete all data?") },
            text = { Text("All sessions, records and points are removed. Tracking starts again from now.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDeleteAll() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
