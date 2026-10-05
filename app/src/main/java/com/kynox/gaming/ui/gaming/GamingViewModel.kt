package com.kynox.gaming.ui.gaming

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kynox.gaming.data.gaming.GameDetectionRepository
import com.kynox.gaming.data.gaming.GamingModeRepository
import com.kynox.gaming.data.gaming.OverlayPermissionRepository
import com.kynox.gaming.service.QuickOverlayService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val ACTIVE_REFRESH_MS = 2000L

data class GamingUiState(
    val active: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
    val autoDetect: Boolean = false,
    val rootAvailable: Boolean = false,
    val managedCount: Int = 0,
    val overlayActive: Boolean = false,
    val overlayGranted: Boolean = true
)

class GamingViewModel(
    private val repository: GamingModeRepository,
    private val detectionRepository: GameDetectionRepository,
    private val overlayPermissionRepository: OverlayPermissionRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(GamingUiState())
    val uiState: StateFlow<GamingUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                active = repository.isActive(),
                autoDetect = detectionRepository.isEnabled(),
                rootAvailable = detectionRepository.isRootAvailable(),
                managedCount = detectionRepository.managedCount(),
                overlayGranted = overlayPermissionRepository.isGranted()
            )
            // Auto Game Mode can flip the state while this screen is open.
            while (true) {
                com.kynox.gaming.core.utils.AppVisibility.awaitForeground()
                delay(ACTIVE_REFRESH_MS)
                if (!_uiState.value.busy) {
                    _uiState.value = _uiState.value.copy(
                        active = repository.isActive(),
                        managedCount = detectionRepository.managedCount(),
                        overlayActive = QuickOverlayService.isActive.value,
                        overlayGranted = overlayPermissionRepository.isGranted()
                    )
                }
            }
        }
    }

    fun setGameMode(enabled: Boolean) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(busy = true)
            val result = if (enabled) repository.enable() else repository.disable()
            result.onFailure { _uiState.value = _uiState.value.copy(message = it) }
            _uiState.value = _uiState.value.copy(busy = false, active = repository.isActive())
        }
    }

    fun setAutoDetect(enabled: Boolean) {
        viewModelScope.launch {
            detectionRepository.setEnabled(enabled)
            _uiState.value = _uiState.value.copy(autoDetect = enabled)
        }
    }

    fun prepareOverlayPermission(onReady: (granted: Boolean) -> Unit) {
        viewModelScope.launch {
            overlayPermissionRepository.ensureGranted()
            val granted = overlayPermissionRepository.isGranted()
            _uiState.value = _uiState.value.copy(overlayGranted = granted)
            onReady(granted)
        }
    }

    fun setOverlayActive(active: Boolean) {
        _uiState.value = _uiState.value.copy(overlayActive = active)
    }

    fun consumeMessage() { _uiState.value = _uiState.value.copy(message = null) }
}
