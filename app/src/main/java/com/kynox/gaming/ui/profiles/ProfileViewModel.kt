package com.kynox.gaming.ui.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kynox.gaming.data.profiles.ProfileRepository
import com.kynox.gaming.data.settings.SettingsRepository
import com.kynox.gaming.data.thermal.ThermalRepository
import com.kynox.gaming.domain.model.ProfileApplySummary
import com.kynox.gaming.domain.model.ProfileParamResult
import com.kynox.gaming.domain.model.ProfileType
import com.kynox.gaming.domain.model.ThermalPolicySummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class ProfileUiState(
    val activeProfile: ProfileType? = null,
    val hasCustomSaved: Boolean = false,
    val applyOnBoot: Boolean = false,
    val busy: Boolean = false,
    val lastResults: List<ProfileParamResult>? = null,
    val message: String? = null,
    val thermalPolicy: ThermalPolicySummary = ThermalPolicySummary(false, null, emptyList(), 0, 0),
    val thermalPolicyBusy: Boolean = false
)

class ProfileViewModel(
    private val repository: ProfileRepository,
    private val settingsRepository: SettingsRepository,
    private val thermalRepository: ThermalRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        refresh()
        refreshThermalPolicy()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                activeProfile = repository.activeProfile(),
                hasCustomSaved = repository.customProfile() != null,
                applyOnBoot = settingsRepository.settingsFlow.first().applyOnBoot
            )
        }
    }

    fun refreshThermalPolicy() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(thermalPolicy = thermalRepository.policySummary())
        }
    }

    /** Global, immediate -- like Disable Thermal, this is its own action rather than something bundled into a preset profile switch. */
    fun setThermalPolicy(policy: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(thermalPolicyBusy = true)
            val result = thermalRepository.setPolicyForAllZones(policy)
            result.onFailure { _uiState.value = _uiState.value.copy(message = it) }
            _uiState.value = _uiState.value.copy(thermalPolicyBusy = false, thermalPolicy = thermalRepository.policySummary())
        }
    }

    fun apply(type: ProfileType) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(busy = true, lastResults = null)
            val result = repository.apply(type)
            var summary: ProfileApplySummary? = null
            result.onSuccess { summary = it }
            result.onFailure { _uiState.value = _uiState.value.copy(message = it) }
            _uiState.value = _uiState.value.copy(
                busy = false,
                lastResults = summary?.results,
                activeProfile = repository.activeProfile()
            )
            refreshThermalPolicy()
        }
    }

    fun saveCurrentAsCustom() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(busy = true)
            repository.saveCurrentAsCustom()
            _uiState.value = _uiState.value.copy(busy = false, hasCustomSaved = true, message = "Current CPU/GPU state saved as Custom profile")
        }
    }

    fun reset() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(busy = true, lastResults = null)
            val result = repository.reset()
            var results: List<ProfileParamResult>? = null
            result.onSuccess { results = it }
            result.onFailure { _uiState.value = _uiState.value.copy(message = it) }
            _uiState.value = _uiState.value.copy(
                busy = false,
                lastResults = results,
                activeProfile = repository.activeProfile()
            )
            refreshThermalPolicy()
        }
    }

    fun setApplyOnBoot(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setApplyOnBoot(enabled)
            _uiState.value = _uiState.value.copy(applyOnBoot = enabled)
        }
    }

    fun consumeMessage() { _uiState.value = _uiState.value.copy(message = null) }
}
