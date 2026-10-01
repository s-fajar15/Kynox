package com.kynox.gaming.ui.gaming

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kynox.gaming.data.gaming.GameLibraryRepository
import com.kynox.gaming.domain.model.InstalledGame
import com.kynox.gaming.domain.model.ProfileType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class GameLibraryUiState(
    val loading: Boolean = true,
    val apps: List<InstalledGame> = emptyList(),
    val query: String = ""
)

class GameLibraryViewModel(private val repository: GameLibraryRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(GameLibraryUiState())
    val uiState: StateFlow<GameLibraryUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true)
            val apps = repository.listInstalledApps()
            _uiState.value = _uiState.value.copy(loading = false, apps = apps)
        }
    }

    fun setQuery(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
    }

    fun setManaged(packageName: String, managed: Boolean) {
        viewModelScope.launch {
            repository.setManaged(packageName, managed)
            _uiState.value = _uiState.value.copy(
                apps = _uiState.value.apps.map {
                    if (it.packageName == packageName) it.copy(isManaged = managed, assignedProfile = if (managed) it.assignedProfile else null) else it
                }
            )
        }
    }

    fun setProfile(packageName: String, profile: ProfileType?) {
        viewModelScope.launch {
            repository.setProfileFor(packageName, profile)
            _uiState.value = _uiState.value.copy(
                apps = _uiState.value.apps.map {
                    if (it.packageName == packageName) it.copy(assignedProfile = profile) else it
                }
            )
        }
    }
}
