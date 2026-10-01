package com.kynox.gaming.core.shell

import com.kynox.gaming.core.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * Executes commands as the app's own (non-root) process. Used for diagnostics
 * that do not require elevated privileges. UI code never calls this directly;
 * it only ever goes through a Repository.
 *
 * Bounds the *process* itself with Process.waitFor(timeout, unit) rather than
 * wrapping a blocking stream read in withTimeoutOrNull -- coroutine
 * cancellation does not interrupt raw blocking I/O, so that pattern does not
 * actually enforce the timeout if the process never produces EOF.
 */
object ShellExecutor {

    suspend fun run(command: String, timeoutMs: Long = 4000L): ShellResult = withContext(Dispatchers.IO) {
        if (command.isBlank()) {
            return@withContext ShellResult(command, -1, "", "empty command")
        }
        var process: Process? = null
        try {
            process = ProcessBuilder("sh", "-c", command)
                .redirectErrorStream(true)
                .start()
            val exited = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (!exited) {
                process.destroyForcibly()
                return@withContext ShellResult(command, -1, "", "timeout", timedOut = true)
            }
            val stdout = readStream(process.inputStream)
            ShellResult(command, process.exitValue(), stdout, "")
        } catch (t: Throwable) {
            Logger.e("ShellExecutor", "Failed running: $command", t)
            ShellResult(command, -1, "", t.message ?: "unknown error")
        } finally {
            process?.let { if (it.isAlive) it.destroyForcibly() }
        }
    }

    private fun readStream(stream: java.io.InputStream): String {
        return BufferedReader(InputStreamReader(stream)).use { it.readText() }.trim()
    }
}
