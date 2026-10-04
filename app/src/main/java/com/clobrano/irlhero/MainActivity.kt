package com.clobrano.irlhero

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clobrano.irlhero.ui.IrlHeroApp
import com.clobrano.irlhero.ui.MainViewModel
import com.clobrano.irlhero.ui.theme.IrlHeroTheme

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val state by vm.state.collectAsStateWithLifecycle()
            // Reports are computed only when the app is opened.
            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }
            if (state.settingsLoaded) {
                IrlHeroTheme(state.settings.theme) { IrlHeroApp(vm, state) }
            }
        }
    }
}
