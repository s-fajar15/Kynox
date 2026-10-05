package com.kynox.gaming.ui.gaming

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kynox.gaming.data.gaming.GameLibraryRepository
import com.kynox.gaming.data.gaming.GameSessionRepository
import com.kynox.gaming.data.gaming.OverlayPermissionRepository
import com.kynox.gaming.domain.model.InstalledGame
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SessionUiState(
    val loading: Boolean = true,
    val managedGames: List<InstalledGame> = emptyList(),
    val selected: InstalledGame? = null,
    val isRecording: Boolean = false,
    val hasReports: Boolean = false,
    val overlayGranted: Boolean = true,
    val startedAtMs: Long = 0L,
    val live: com.kynox.gaming.domain.model.LiveMetrics = com.kynox.gaming.domain.model.LiveMetrics()
)

class SessionViewModel(
    private val gameLibraryRepository: GameLibraryRepository,
    private val sessionRepository: GameSessionRepository,
    private val overlayPermissionRepository: OverlayPermissionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SessionUiState())
    val uiState: StateFlow<SessionUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val apps = gameLibraryRepository.listInstalledApps().filter { it.isManaged }
            _uiState.value = _uiState.value.copy(loading = false, managedGames = apps, hasReports = sessionRepository.reportCount() > 0)
        }
        viewModelScope.launch {
            sessionRepository.isRecording.collect { recording ->
                _uiState.value = _uiState.value.copy(
                    isRecording = recording,
                    startedAtMs = if (recording) sessionRepository.recordingStartedAtMs() else 0L
                )
                if (!recording) {
                    _uiState.value = _uiState.value.copy(hasReports = sessionRepository.reportCount() > 0)
                }
            }
        }
    }

    init {
        viewModelScope.launch {
            overlayPermissionRepository.ensureGranted()
            while (true) {
                com.kynox.gaming.core.utils.AppVisibility.awaitForeground()
                _uiState.value = _uiState.value.copy(overlayGranted = overlayPermissionRepository.isGranted())
                delay(2000)
            }
        }
    }

    init {
        viewModelScope.launch {
            sessionRepository.live.collect { live -> _uiState.value = _uiState.value.copy(live = live) }
        }
    }

    fun prepareOverlay(onReady: () -> Unit) {
        viewModelScope.launch {
            overlayPermissionRepository.ensureGranted()
            _uiState.value = _uiState.value.copy(overlayGranted = overlayPermissionRepository.isGranted())
            onReady()
        }
    }

    fun select(game: InstalledGame) {
        _uiState.value = _uiState.value.copy(selected = game)
    }

    fun refreshReports() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(hasReports = sessionRepository.reportCount() > 0)
        }
    }
}
