package com.kynox.gaming.ui.thermal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kynox.gaming.core.utils.AppResult
import com.kynox.gaming.data.thermal.ThermalRepository
import com.kynox.gaming.domain.model.ThermalDisableState
import com.kynox.gaming.domain.model.ThermalZone
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Outcome of the last disable/restore, shown right under the status so it is never ambiguous. */
sealed interface ThermalNotice {
    object Disabled : ThermalNotice
    object Restored : ThermalNotice
    data class Failed(val reason: String) : ThermalNotice
}

data class ThermalUiState(
    val zones: List<ThermalZone> = emptyList(),
    val disableState: ThermalDisableState? = null,
    val busy: Boolean = false,
    val notice: ThermalNotice? = null
)

class ThermalViewModel(private val repository: ThermalRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(ThermalUiState())
    val uiState: StateFlow<ThermalUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            while (true) {
                // Do not overwrite the state while a change is being applied.
                if (!_uiState.value.busy) {
                    val zones = repository.readZones()
                    val disableState = repository.disableState()
                    _uiState.value = _uiState.value.copy(zones = zones, disableState = disableState)
                }
                delay(3000)
            }
        }
    }

    fun disableThermal() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(busy = true, notice = null)
            val result = repository.disable()
            val notice: ThermalNotice = when (result) {
                is AppResult.Success -> ThermalNotice.Disabled
                is AppResult.Failure -> ThermalNotice.Failed(result.message)
            }
            _uiState.value = _uiState.value.copy(busy = false, disableState = repository.disableState(), notice = notice)
        }
    }

    fun restoreThermal() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(busy = true, notice = null)
            val result = repository.restore()
            val notice: ThermalNotice = when (result) {
                is AppResult.Success -> ThermalNotice.Restored
                is AppResult.Failure -> ThermalNotice.Failed(result.message)
            }
            _uiState.value = _uiState.value.copy(busy = false, disableState = repository.disableState(), notice = notice)
        }
    }
}
