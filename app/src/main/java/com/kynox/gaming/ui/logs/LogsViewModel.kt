package com.kynox.gaming.ui.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kynox.gaming.data.logs.LogRepository
import com.kynox.gaming.domain.model.LogEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LogsViewModel(private val repository: LogRepository) : ViewModel() {
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch { _logs.value = repository.readAll() }
    }

    fun clear() {
        viewModelScope.launch {
            repository.clear()
            _logs.value = emptyList()
        }
    }
}
