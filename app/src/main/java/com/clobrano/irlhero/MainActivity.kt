package com.clobrano.irlhero

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.clobrano.irlhero.ui.IrlHeroApp
import com.clobrano.irlhero.ui.MainViewModel
import com.clobrano.irlhero.ui.theme.IrlHeroTheme
import com.clobrano.irlhero.ui.theme.LocalDarkTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Reports are computed only while the app is open: on resume, again shortly after
        // (the system may log the unlock a moment late), then every minute while visible.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                vm.onResume()
                delay(LATE_EVENTS_DELAY_MILLIS)
                while (true) {
                    vm.refresh()
                    delay(REFRESH_INTERVAL_MILLIS)
                }
            }
        }
        setContent {
            val state by vm.state.collectAsStateWithLifecycle()
            if (state.settingsLoaded) {
                IrlHeroTheme(state.settings.theme) {
                    val dark = LocalDarkTheme.current
                    DisposableEffect(dark) {
                        val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                        onDispose {}
                    }
                    IrlHeroApp(vm, state)
                }
            }
        }
    }

    private companion object {
        const val LATE_EVENTS_DELAY_MILLIS = 5_000L
        const val REFRESH_INTERVAL_MILLIS = 60_000L
    }
}
