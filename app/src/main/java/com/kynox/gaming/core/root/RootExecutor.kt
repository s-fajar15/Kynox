package com.kynox.gaming.core.root

/**
 * Sole abstraction layer for privileged operations. UI and ViewModels never
 * invoke shell commands directly; every write to a protected system path goes
 * through here so it is validated, time-bounded, and logged in one place.
 */
interface RootExecutor {
    suspend fun status(): RootStatus
    suspend fun refreshStatus(): RootStatus
    suspend fun execute(command: String, timeoutMs: Long = 5000L): RootCommandResult
    suspend fun readFile(path: String): RootCommandResult
    suspend fun writeFile(path: String, value: String, timeoutMs: Long = 5000L): RootCommandResult
}
