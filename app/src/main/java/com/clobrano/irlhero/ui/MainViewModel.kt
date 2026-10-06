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
    /** False when app notifications or the lock-screen card channel are turned off. */
    val canPostLockCard: Boolean = true,
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

    /** Called when the app comes to the foreground. */
    fun onResume() {
        viewModelScope.launch {
            // Bring the card back if the system stopped its service (start is idempotent).
            if (repo.settingsStore.current().lockCardEnabled) LockCardService.start(getApplication())
        }
        refresh()
    }

    /** Sync, score, celebrate. Runs on resume and periodically while the app is on screen. */
    fun refresh() {
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val access = repo.usage.hasAccess()
            val battery = ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)
            _state.update {
                it.copy(hasUsageAccess = access, ignoresBatteryOptimizations = battery, canPostLockCard = LockCardService.canPost(ctx))
            }
            if (!access) return@launch
            repo.sync()
            // The app is on screen, so the phone is unlocked right now.
            val d = repo.dashboard(countOpenSessionUntilNow = true)
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
            repo.settingsStore.update {
                it.copy(onboarded = true, pointsSinceMillis = it.pointsSinceMillis.takeIf { t -> t > 0 } ?: System.currentTimeMillis())
            }
            DailySyncWorker.schedule(getApplication())
            refresh()
        }
    }

    fun setLockCard(enabled: Boolean) {
        viewModelScope.launch {
            repo.settingsStore.update { it.copy(lockCardEnabled = enabled) }
            val ctx = getApplication<Application>()
            if (enabled) LockCardService.start(ctx) else LockCardService.stop(ctx)
            _state.update { it.copy(canPostLockCard = LockCardService.canPost(ctx)) }
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
