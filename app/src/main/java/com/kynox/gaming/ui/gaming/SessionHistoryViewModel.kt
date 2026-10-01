package com.kynox.gaming.ui.gaming

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kynox.gaming.data.gaming.GameSessionRepository
import com.kynox.gaming.domain.model.SessionReport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SessionHistoryUiState(
    val loading: Boolean = true,
    val reports: List<SessionReport> = emptyList()
)

class SessionHistoryViewModel(private val repository: GameSessionRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(SessionHistoryUiState())
    val uiState: StateFlow<SessionHistoryUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = SessionHistoryUiState(loading = false, reports = repository.listReports())
        }
    }

    fun delete(report: SessionReport) {
        viewModelScope.launch {
            repository.deleteReport(report.startedAtMs)
            _uiState.value = SessionHistoryUiState(loading = false, reports = repository.listReports())
        }
    }
}
