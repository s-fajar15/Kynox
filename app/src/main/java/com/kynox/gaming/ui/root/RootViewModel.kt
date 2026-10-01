package com.kynox.gaming.ui.root

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kynox.gaming.data.root.RootRepository
import com.kynox.gaming.core.root.RootStatus
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class RootViewModel(private val repository: RootRepository) : ViewModel() {
    val status: StateFlow<RootStatus?> = repository.status

    init {
        viewModelScope.launch { repository.refresh() }
    }

    fun recheck() {
        viewModelScope.launch { repository.refresh() }
    }
}
