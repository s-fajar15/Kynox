package com.kynox.gaming.ui.process

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kynox.gaming.data.process.ProcessRepository
import com.kynox.gaming.domain.model.ProcessInfo
import com.kynox.gaming.domain.model.ProcessSort
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val REFRESH_INTERVAL_MS = 3000L

data class ProcessUiState(
    val processes: List<ProcessInfo> = emptyList(),
    val query: String = "",
    val sort: ProcessSort = ProcessSort.CPU,
    val loading: Boolean = true,
    val killingPid: Int? = null
) {
    val visible: List<ProcessInfo> get() {
        val filtered = if (query.isBlank()) processes else processes.filter {
            it.name.contains(query, ignoreCase = true) || it.pid.toString() == query.trim()
        }
        return when (sort) {
            ProcessSort.CPU -> filtered.sortedByDescending { it.cpuPercent ?: -1f }
            ProcessSort.MEMORY -> filtered.sortedByDescending { it.memoryKb ?: -1L }
            ProcessSort.NAME -> filtered.sortedBy { it.name.lowercase() }
        }
    }
}

class ProcessViewModel(private val repository: ProcessRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(ProcessUiState())
    val uiState: StateFlow<ProcessUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            while (true) {
                val processes = repository.list()
                _uiState.value = _uiState.value.copy(processes = processes, loading = false)
                delay(REFRESH_INTERVAL_MS)
            }
        }
    }

    fun setQuery(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
    }

    fun setSort(sort: ProcessSort) {
        _uiState.value = _uiState.value.copy(sort = sort)
    }

    fun kill(pid: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(killingPid = pid)
            repository.kill(pid)
            _uiState.value = _uiState.value.copy(processes = repository.list(), killingPid = null)
        }
    }
}
