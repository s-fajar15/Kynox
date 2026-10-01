package com.kynox.gaming.ui.battery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kynox.gaming.data.battery.BatteryRepository
import com.kynox.gaming.data.battery.FAST_CHARGE_DEFAULT_MA
import com.kynox.gaming.data.settings.SettingsRepository
import com.kynox.gaming.domain.model.BatteryInfo
import com.kynox.gaming.domain.model.CHARGE_LIMIT_DEFAULT_PERCENT
import com.kynox.gaming.domain.model.ChargeLimitState
import com.kynox.gaming.domain.model.ChargerInfo
import com.kynox.gaming.domain.model.FastChargingState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class BatteryUiState(
    val info: BatteryInfo? = null,
    val charger: ChargerInfo? = null,
    val fastCharging: FastChargingState? = null,
    val targetMa: Int = FAST_CHARGE_DEFAULT_MA,
    val showCustomInput: Boolean = false,
    val chargeLimit: ChargeLimitState? = null,
    val chargeLimitEnabled: Boolean = false,
    val chargeLimitPercent: Int = CHARGE_LIMIT_DEFAULT_PERCENT,
    val busy: Boolean = false,
    val message: String? = null
)

class BatteryViewModel(
    private val repository: BatteryRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(BatteryUiState())
    val uiState: StateFlow<BatteryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val savedSettings = settingsRepository.settingsFlow.first()
            _uiState.value = _uiState.value.copy(
                chargeLimitEnabled = savedSettings.chargeLimitEnabled,
                chargeLimitPercent = savedSettings.chargeLimitPercent
            )
            while (true) {
                val fc = repository.fastChargingState()
                _uiState.value = _uiState.value.copy(
                    info = repository.readInfo(),
                    charger = repository.readChargerInfo(),
                    fastCharging = fc,
                    targetMa = if (fc.active) fc.targetCurrentMa else _uiState.value.targetMa,
                    chargeLimit = repository.chargeLimitState()
                )
                delay(2000)
            }
        }
    }

    fun setTargetMa(value: Int) {
        _uiState.value = _uiState.value.copy(targetMa = value)
    }

    fun toggleCustomInput() {
        _uiState.value = _uiState.value.copy(showCustomInput = !_uiState.value.showCustomInput)
    }

    fun setFastChargingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(busy = true)
            val result = if (enabled) {
                repository.applyFastCharging(_uiState.value.targetMa, includeAdvanced = false)
            } else {
                repository.restoreFastCharging()
            }
            result.onFailure { _uiState.value = _uiState.value.copy(message = it) }
            _uiState.value = _uiState.value.copy(busy = false, fastCharging = repository.fastChargingState())
        }
    }

    fun applyCustomValue() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(busy = true)
            val advanced = _uiState.value.fastCharging?.advancedActive ?: false
            val result = repository.applyFastCharging(_uiState.value.targetMa, includeAdvanced = advanced)
            result.onFailure { _uiState.value = _uiState.value.copy(message = it) }
            _uiState.value = _uiState.value.copy(busy = false, showCustomInput = false, fastCharging = repository.fastChargingState())
        }
    }

    fun setAdvancedEnabled(enabled: Boolean) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(busy = true)
            val result = repository.applyFastCharging(_uiState.value.targetMa, includeAdvanced = enabled)
            result.onFailure { _uiState.value = _uiState.value.copy(message = it) }
            _uiState.value = _uiState.value.copy(busy = false, fastCharging = repository.fastChargingState())
        }
    }

    fun setChargeLimitEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(chargeLimitEnabled = enabled)
        viewModelScope.launch { settingsRepository.setChargeLimit(enabled, _uiState.value.chargeLimitPercent) }
    }

    fun setChargeLimitPercent(percent: Int) {
        _uiState.value = _uiState.value.copy(chargeLimitPercent = percent)
        viewModelScope.launch { settingsRepository.setChargeLimit(_uiState.value.chargeLimitEnabled, percent) }
    }

    fun consumeMessage() { _uiState.value = _uiState.value.copy(message = null) }
}
