package com.clobrano.irlhero.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import com.clobrano.irlhero.share.RecordShare
import com.clobrano.irlhero.share.ShareContent
import com.clobrano.irlhero.share.ShareItem

private data class Tab(val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("Today", Icons.Outlined.WbSunny),
    Tab("Stats", Icons.Outlined.BarChart),
    Tab("Records", Icons.Outlined.EmojiEvents),
    Tab("Hero", Icons.Outlined.Shield),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IrlHeroApp(vm: MainViewModel, state: UiState) {
    if (!state.settings.onboarded) {
        Onboarding(state, vm::updateSettings, vm::completeOnboarding)
        return
    }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var share by remember { mutableStateOf<ShareItem?>(null) }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (showSettings) "Settings" else tabs[tab].label) },
                navigationIcon = {
                    if (showSettings) IconButton(onClick = { showSettings = false }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!showSettings) IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        bottomBar = {
            if (!showSettings) NavigationBar {
                tabs.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i, onClick = { tab = i },
                        icon = { Icon(t.icon, contentDescription = null) }, label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val d = state.dashboard
            when {
                showSettings -> SettingsScreen(
                    state, vm::updateSettings, vm::setLockCard,
                    onExport = { vm.exportCsv(context) }, onDeleteAll = vm::deleteAll,
                )
                !state.hasUsageAccess -> AccessMissing()
                d == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                else -> when (tab) {
                    0 -> TodayScreen(d) { share = ShareContent.build(d, it) }
                    1 -> StatsScreen(d) { share = ShareContent.build(d, it) }
                    2 -> RecordsScreen(d) { r -> share = ShareContent.record(d, r) }
                    else -> HeroScreen(d)
                }
            }
        }
    }

    val d = state.dashboard
    val celebration = state.celebrations.firstOrNull()
    if (celebration != null && d != null) {
        CelebrationScreen(
            celebration,
            onShare = {
                share = ShareContent.record(d, RecordShare(celebration.shareTitle, celebration.value, celebration.detail))
                vm.dismissCelebration()
            },
            onDismiss = vm::dismissCelebration,
        )
    }
    share?.let { ShareSheet(it) { share = null } }
}

@Composable
private fun AccessMissing() {
    val context = LocalContext.current
    androidx.compose.foundation.layout.Column(
        Modifier.fillMaxSize().padding(androidx.compose.ui.unit.Dp(24f)),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Text("Usage access is off", style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
        Text("In Real Life Hero needs it to see when your phone is locked and unlocked. Nothing else is read.")
        androidx.compose.material3.Button(onClick = {
            context.startActivity(android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }) { Text("Open settings") }
    }
}
