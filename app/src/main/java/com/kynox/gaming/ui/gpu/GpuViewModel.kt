package com.kynox.gaming.ui.gpu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kynox.gaming.data.gpu.GpuRepository
import com.kynox.gaming.domain.model.GpuState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

class GpuViewModel(private val repository: GpuRepository) : ViewModel() {
    private val _state = MutableStateFlow<GpuState?>(null)
    val state: StateFlow<GpuState?> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            while (true) {
                com.kynox.gaming.core.utils.AppVisibility.awaitForeground()
                _state.value = repository.readState()
                delay(2000)
            }
        }
    }

    fun setGovernor(governor: String) {
        viewModelScope.launch {
            repository.setGovernor(governor)
            _state.value = repository.readState()
        }
    }
}
