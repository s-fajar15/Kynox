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
    val killingPid: Int? = null,
    /** Hasil penghentian terakhir, diverifikasi dengan membaca ulang daftar proses. */
    val killResult: KillResult? = null
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

data class KillResult(val name: String, val pid: Int, val success: Boolean)

class ProcessViewModel(private val repository: ProcessRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(ProcessUiState())
    val uiState: StateFlow<ProcessUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            while (true) {
                com.kynox.gaming.core.utils.AppVisibility.awaitForeground()
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
        if (_uiState.value.killingPid != null) return
        val name = _uiState.value.processes.firstOrNull { it.pid == pid }?.name.orEmpty()
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(killingPid = pid, killResult = null)
            repository.kill(pid)
            val after = repository.list()
            // Berhasil hanya jika proses benar-benar sudah hilang dari daftar.
            val gone = after.none { it.pid == pid }
            _uiState.value = _uiState.value.copy(
                processes = after,
                killingPid = null,
                killResult = KillResult(name, pid, gone)
            )
        }
    }

    fun dismissKillResult() {
        _uiState.value = _uiState.value.copy(killResult = null)
    }
}
