package com.kynox.gaming.ui.cpu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kynox.gaming.data.cpu.CpuRepository
import com.kynox.gaming.domain.model.CpuSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

class CpuViewModel(private val repository: CpuRepository) : ViewModel() {
    private val _snapshot = MutableStateFlow<CpuSnapshot?>(null)
    val snapshot: StateFlow<CpuSnapshot?> = _snapshot.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        viewModelScope.launch {
            while (true) {
                com.kynox.gaming.core.utils.AppVisibility.awaitForeground()
                _snapshot.value = repository.readSnapshot()
                delay(2000)
            }
        }
    }

    fun setGovernor(core: Int, governor: String) {
        viewModelScope.launch {
            val result = repository.setGovernor(core, governor)
            result.onFailure { _message.value = it }
            _snapshot.value = repository.readSnapshot()
        }
    }

    fun setOnline(core: Int, online: Boolean) {
        viewModelScope.launch {
            val result = repository.setOnline(core, online)
            result.onFailure { _message.value = it }
            _snapshot.value = repository.readSnapshot()
        }
    }

    fun consumeMessage() { _message.value = null }
}
