package com.kynox.gaming.ui.debloat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kynox.gaming.core.utils.AppResult
import com.kynox.gaming.data.debloat.DebloatApp
import com.kynox.gaming.data.debloat.DebloatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DebloatUiState(
    val loading: Boolean = true,
    val query: String = "",
    val apps: List<DebloatApp> = emptyList(),
    /** Package currently mid disable/enable, so its row can show a spinner and every other row stays tappable. */
    val busyPackage: String? = null,
    val lastError: String? = null
)

class DebloatViewModel(private val repository: DebloatRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(DebloatUiState())
    val uiState: StateFlow<DebloatUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true)
            val apps = repository.listCandidates()
            _uiState.value = _uiState.value.copy(apps = apps, loading = false)
        }
    }

    fun setQuery(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
    }

    fun setDisabled(packageName: String, disabled: Boolean) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(busyPackage = packageName, lastError = null)
            val result = if (disabled) repository.disable(packageName) else repository.enable(packageName)
            when (result) {
                is AppResult.Success -> refresh()
                is AppResult.Failure -> _uiState.value = _uiState.value.copy(lastError = result.message)
            }
            _uiState.value = _uiState.value.copy(busyPackage = null)
        }
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(lastError = null)
    }
}
