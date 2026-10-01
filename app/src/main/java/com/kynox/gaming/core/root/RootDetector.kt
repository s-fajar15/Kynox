package com.kynox.gaming.core.root

import com.kynox.gaming.core.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * Detects whether root is available on this device and, if so, which
 * provider granted it. Detection never assumes root exists: it always
 * probes and falls back to NONE on any failure.
 */
object RootDetector {

    private const val TAG = "RootDetector"

    private val COMMON_SU_PATHS = listOf(
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/data/adb/magisk/su",
        "/data/adb/ksu/bin/su",
        "/system/bin/.ext/su"
    )

    suspend fun detect(): RootStatus = withContext(Dispatchers.IO) {
        val suOnPath = COMMON_SU_PATHS.any { File(it).exists() } || probe("which su").isNotBlank()

        val idResult = exec("su -c id", 4000L)
        if (!idResult.first || !idResult.second.contains("uid=0")) {
            return@withContext RootStatus(
                isAvailable = false,
                provider = RootProvider.NONE,
                suVersion = null,
                detail = if (suOnPath) "su binary found but access was denied" else "no su binary detected"
            )
        }

        val provider = when {
            exec("su -c '[ -d /data/adb/magisk ]'", 3000L).first -> RootProvider.MAGISK
            probe("su -c getprop ro.build.version.magisk.version").isNotBlank() -> RootProvider.MAGISK
            exec("su -c '[ -e /data/adb/ksu ] || which ksud'", 3000L).first -> RootProvider.KERNELSU
            exec("su -c '[ -d /data/adb/ap ] || which apd'", 3000L).first -> RootProvider.APATCH
            else -> RootProvider.UNKNOWN
        }

        val version = probe("su -v").ifBlank { probe("su -c su -v") }

        Logger.i(TAG, "Detected root provider=$provider version=$version")
        RootStatus(
            isAvailable = true,
            provider = provider,
            suVersion = version.ifBlank { null },
            detail = "root access granted"
        )
    }

    /** Runs a command, returns true if exit code is 0. Bounds the process itself
     * with Process.waitFor(timeout, unit) -- see RootExecutorImpl for why
     * wrapping a blocking read in withTimeoutOrNull does not actually work. */
    private suspend fun exec(command: String, timeoutMs: Long): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        var process: Process? = null
        try {
            process = ProcessBuilder("sh", "-c", command).redirectErrorStream(true).start()
            val exited = process.waitFor(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
            if (!exited) {
                process.destroyForcibly()
                return@withContext false to "timeout"
            }
            val out = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }.trim()
            (process.exitValue() == 0) to out
        } catch (t: Throwable) {
            false to (t.message ?: "")
        } finally {
            process?.let { if (it.isAlive) it.destroyForcibly() }
        }
    }

    private suspend fun probe(command: String): String = exec(command, 3000L).second
}
