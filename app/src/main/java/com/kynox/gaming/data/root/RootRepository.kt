package com.kynox.gaming.data.root

import com.kynox.gaming.core.root.RootExecutor
import com.kynox.gaming.core.root.RootStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class RootRepository(private val rootExecutor: RootExecutor) {

    private val _status = MutableStateFlow<RootStatus?>(null)
    val status: StateFlow<RootStatus?> = _status.asStateFlow()

    suspend fun refresh(): RootStatus {
        val result = rootExecutor.refreshStatus()
        _status.value = result
        return result
    }

    suspend fun current(): RootStatus {
        return _status.value ?: refresh()
    }
}
