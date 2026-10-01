package com.kynox.gaming.core.root

enum class RootProvider {
    MAGISK, KERNELSU, APATCH, UNKNOWN, NONE
}

data class RootStatus(
    val isAvailable: Boolean,
    val provider: RootProvider,
    val suVersion: String?,
    val detail: String
)

data class RootCommandResult(
    val command: String,
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val timedOut: Boolean,
    val durationMs: Long
) {
    val isSuccess: Boolean get() = exitCode == 0 && !timedOut
}
