package com.kynox.gaming.core.shell

data class ShellResult(
    val command: String,
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val timedOut: Boolean = false
) {
    val isSuccess: Boolean get() = exitCode == 0 && !timedOut
}
