package com.kynox.gaming.ui.monitor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kynox.gaming.data.monitor.MonitorRecorder
import com.kynox.gaming.domain.model.MonitorSample
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class MonitorUiState(
    val samples: List<MonitorSample> = emptyList(),
    val windowSeconds: Int = 60,
    val paused: Boolean = false,
    val gpuSupported: Boolean = true,
    /** Whether the background monitor service is currently sampling. */
    val recording: Boolean = false
)

/**
 * The Monitor tab only *reads* what [MonitorRecorder] has been collecting in
 * the background. Pausing freezes the charts on screen (so a spike can be
 * looked at) but never stops the recording itself.
 */
class MonitorViewModel(private val recorder: MonitorRecorder) : ViewModel() {

    private val window = MutableStateFlow(60)
    private val frozen = MutableStateFlow<List<MonitorSample>?>(null)

    val uiState: StateFlow<MonitorUiState> = combine(
        recorder.samples, recorder.gpuSupported, recorder.running, window, frozen
    ) { samples, gpuSupported, running, windowSeconds, frozenSamples ->
        MonitorUiState(
            samples = frozenSamples ?: samples,
            windowSeconds = windowSeconds,
            paused = frozenSamples != null,
            gpuSupported = gpuSupported,
            recording = running
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MonitorUiState())

    fun setWindow(seconds: Int) {
        window.value = seconds
    }

    fun togglePause() {
        frozen.value = if (frozen.value == null) recorder.samples.value else null
    }
}
