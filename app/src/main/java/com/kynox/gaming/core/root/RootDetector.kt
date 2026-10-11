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

    /**
     * [runRoot] menjalankan perintah lewat shell root yang sudah terbuka (satu proses su), jadi
     * deteksi tidak lagi memanggil `su` berkali-kali dan tidak memicu notifikasi superuser berulang.
     */
    suspend fun detect(runRoot: suspend (String, Long) -> Pair<Boolean, String>): RootStatus = withContext(Dispatchers.IO) {
        val suOnPath = COMMON_SU_PATHS.any { File(it).exists() } || probe("which su").isNotBlank()

        val idResult = runRoot("id", 6000L)
        if (!idResult.first || !idResult.second.contains("uid=0")) {
            return@withContext RootStatus(
                isAvailable = false,
                provider = RootProvider.NONE,
                suVersion = null,
                detail = if (suOnPath) "su binary found but access was denied" else "no su binary detected"
            )
        }

        // APatch/KernelSU dicek lebih dulu: sisa folder Magisk di /data/adb bisa tertinggal dan menimbulkan salah deteksi.
        val providerCommand = "if [ -d /data/adb/ap ] || which apd >/dev/null 2>&1; then echo APATCH; " +
            "elif [ -e /data/adb/ksu ] || which ksud >/dev/null 2>&1; then echo KERNELSU; " +
            "elif [ -d /data/adb/magisk ] || [ -n \"\$(getprop ro.build.version.magisk.version)\" ]; then echo MAGISK; else echo UNKNOWN; fi"
        val provider = when (runRoot(providerCommand, 4000L).second.trim()) {
            "MAGISK" -> RootProvider.MAGISK
            "KERNELSU" -> RootProvider.KERNELSU
            "APATCH" -> RootProvider.APATCH
            else -> RootProvider.UNKNOWN
        }

        val version = runRoot("su -v 2>/dev/null", 3000L).second.trim()

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
