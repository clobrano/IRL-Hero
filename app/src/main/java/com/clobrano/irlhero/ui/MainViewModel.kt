package com.clobrano.irlhero.ui

import android.app.Application
import android.content.Context
import android.os.PowerManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.clobrano.irlhero.background.DailySyncWorker
import com.clobrano.irlhero.background.LockCardService
import com.clobrano.irlhero.data.Celebration
import com.clobrano.irlhero.data.Repository
import com.clobrano.irlhero.data.Settings
import com.clobrano.irlhero.domain.Dashboard
import com.clobrano.irlhero.share.Sharing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UiState(
    val settingsLoaded: Boolean = false,
    val settings: Settings = Settings(),
    val hasUsageAccess: Boolean = false,
    val ignoresBatteryOptimizations: Boolean = false,
    val dashboard: Dashboard? = null,
    val celebrations: List<Celebration> = emptyList(),
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repository.get(app)
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repo.settingsStore.settings.collect { s ->
                _state.update { it.copy(settings = s, settingsLoaded = true) }
            }
        }
    }

    /** Called every time the app comes to the foreground: sync, score, celebrate. */
    fun refresh() {
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val access = repo.usage.hasAccess()
            val battery = ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)
            _state.update { it.copy(hasUsageAccess = access, ignoresBatteryOptimizations = battery) }
            if (!access) return@launch
            repo.sync()
            val d = repo.dashboard()
            val settings = repo.settingsStore.current()
            val celebrations = if (settings.onboarded) repo.pendingCelebrations(d) else emptyList()
            _state.update { it.copy(dashboard = d, celebrations = it.celebrations + celebrations) }
        }
    }

    fun updateSettings(transform: (Settings) -> Settings) {
        viewModelScope.launch {
            repo.settingsStore.update(transform)
            refresh()
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            repo.settingsStore.update { it.copy(onboarded = true) }
            DailySyncWorker.schedule(getApplication())
            refresh()
        }
    }

    fun setLockCard(enabled: Boolean) {
        viewModelScope.launch {
            repo.settingsStore.update { it.copy(lockCardEnabled = enabled) }
            val ctx = getApplication<Application>()
            if (enabled) LockCardService.start(ctx) else LockCardService.stop(ctx)
        }
    }

    fun dismissCelebration() = _state.update { it.copy(celebrations = it.celebrations.drop(1)) }

    fun exportCsv(context: Context) {
        val d = _state.value.dashboard ?: return
        viewModelScope.launch {
            Sharing.shareFile(context, repo.exportCsv(d), "text/csv")
        }
    }

    fun deleteAll() {
        viewModelScope.launch {
            repo.deleteAll()
            _state.update { it.copy(dashboard = null, celebrations = emptyList()) }
            refresh()
        }
    }
}
