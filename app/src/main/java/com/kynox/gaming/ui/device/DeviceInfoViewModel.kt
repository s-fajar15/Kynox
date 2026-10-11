package com.kynox.gaming.ui.device

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kynox.gaming.data.device.DeviceInfoRepository
import com.kynox.gaming.domain.model.DeviceInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DeviceInfoViewModel(private val repository: DeviceInfoRepository) : ViewModel() {
    private val _info = MutableStateFlow<DeviceInfo?>(null)
    val info: StateFlow<DeviceInfo?> = _info.asStateFlow()

    init {
        // Tampilkan hasil sebelumnya seketika (bila ada), lalu segarkan di latar belakang.
        _info.value = repository.cached() ?: runCatching { repository.basic() }.getOrNull()
        viewModelScope.launch {
            runCatching { repository.load() }.getOrNull()?.let { _info.value = it }
        }
    }
}
