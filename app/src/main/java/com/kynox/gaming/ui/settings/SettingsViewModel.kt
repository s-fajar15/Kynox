package com.kynox.gaming.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kynox.gaming.data.settings.AppSettings
import com.kynox.gaming.data.settings.SettingsRepository
import com.kynox.gaming.data.settings.TemperatureUnit
import com.kynox.gaming.data.settings.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {
    val settings: StateFlow<AppSettings> = repository.settingsFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings()
    )

    fun setThemeMode(mode: ThemeMode) { viewModelScope.launch { repository.setThemeMode(mode) } }
    fun setRefreshInterval(ms: Long) { viewModelScope.launch { repository.setRefreshInterval(ms) } }
    fun setTemperatureUnit(unit: TemperatureUnit) { viewModelScope.launch { repository.setTemperatureUnit(unit) } }
    fun setApplyOnBoot(enabled: Boolean) { viewModelScope.launch { repository.setApplyOnBoot(enabled) } }
    fun setRequireConfirmation(enabled: Boolean) { viewModelScope.launch { repository.setRequireConfirmation(enabled) } }
    fun setLoggingEnabled(enabled: Boolean) { viewModelScope.launch { repository.setLoggingEnabled(enabled) } }
    fun setChargingNotification(enabled: Boolean) { viewModelScope.launch { repository.setChargingNotification(enabled) } }
    fun setOverlayMetric(showFps: Boolean, showCpu: Boolean, showGpu: Boolean, showBatteryTemp: Boolean) {
        viewModelScope.launch { repository.setOverlayMetric(showFps, showCpu, showGpu, showBatteryTemp) }
    }
    fun setOverlayStyle(scalePercent: Int, opacityPercent: Int) {
        viewModelScope.launch { repository.setOverlayStyle(scalePercent, opacityPercent) }
    }
    fun setMonitorEnabled(enabled: Boolean) { viewModelScope.launch { repository.setMonitorEnabled(enabled) } }
    fun setNotifications(thermal: Boolean, thresholdC: Int, cooldownMin: Int, profileApplied: Boolean) {
        viewModelScope.launch { repository.setNotifications(thermal, thresholdC, cooldownMin, profileApplied) }
    }
    fun setHistory(enabled: Boolean, intervalSec: Int, retentionHours: Int) {
        viewModelScope.launch { repository.setHistory(enabled, intervalSec, retentionHours) }
    }
    fun exitSafeMode() { viewModelScope.launch { repository.exitSafeMode() } }
    fun resetAll() { viewModelScope.launch { repository.resetAll() } }
}
