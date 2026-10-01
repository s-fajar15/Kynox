package com.kynox.gaming.ui.display

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kynox.gaming.data.display.RefreshRateRepository
import com.kynox.gaming.data.gaming.GameLibraryRepository
import com.kynox.gaming.domain.model.InstalledGame
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RefreshRateUiState(
    val enabled: Boolean = false,
    val loading: Boolean = true,
    val query: String = "",
    val apps: List<InstalledGame> = emptyList(),
    val overrides: Map<String, Int> = emptyMap(),
    val supportedRates: List<Int> = listOf(60),
    val defaultRate: Int = 60,
    /** Actual active display mode reported by dumpsys display, not merely the peak setting. */
    val appliedHz: Int? = null,
    val checkingApplied: Boolean = false
)

class RefreshRateViewModel(
    private val repository: RefreshRateRepository,
    private val gameLibraryRepository: GameLibraryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RefreshRateUiState())
    val uiState: StateFlow<RefreshRateUiState> = _uiState.asStateFlow()

    init {
        _uiState.value = _uiState.value.copy(
            supportedRates = repository.supportedRefreshRates(),
            defaultRate = repository.defaultRefreshRate()
        )
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val apps = gameLibraryRepository.listInstalledApps()
            val overrides = repository.overrides()
            val enabled = repository.isEnabled()
            _uiState.value = _uiState.value.copy(apps = apps, overrides = overrides, enabled = enabled, loading = false)
        }
    }

    fun setQuery(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
    }

    fun setEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(enabled = enabled, appliedHz = null)
        viewModelScope.launch { repository.setEnabled(enabled) }
    }

    /** Called after the service has had a moment to act (see the screen's LaunchedEffect), to show whether the setting genuinely took on this device. */
    fun checkApplied() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(checkingApplied = true)
            val hz = repository.readActiveDisplayRefreshRate() ?: repository.currentAppliedHz()
            _uiState.value = _uiState.value.copy(appliedHz = hz, checkingApplied = false)
        }
    }

    fun setOverride(packageName: String, hz: Int?) {
        viewModelScope.launch {
            repository.setOverride(packageName, hz)
            _uiState.value = _uiState.value.copy(overrides = repository.overrides())
        }
    }
}
