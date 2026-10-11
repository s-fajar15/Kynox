package com.kynox.gaming.core.root

import com.kynox.gaming.core.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val TAG = "RootExecutor"

class RootExecutorImpl : RootExecutor {

    private val lock = Mutex()
    private val shellLock = Mutex()
    private val shell = RootShell()
    @Volatile private var cachedStatus: RootStatus? = null
    /** Setelah su gagal dibuka, jangan mencoba lagi sebentar agar tidak membanjiri permintaan izin. */
    @Volatile private var openFailedAt = 0L

    override suspend fun status(): RootStatus = cachedStatus ?: refreshStatus()

    override suspend fun refreshStatus(): RootStatus = lock.withLock {
        // Mulai dari shell baru supaya izin yang baru diberikan/dicabut langsung terbaca.
        shellLock.withLock { shell.close(); openFailedAt = 0L }
        val detected = RootDetector.detect { command, timeoutMs ->
            val r = runInShell(command, timeoutMs)
            (r.exitCode == 0 && !r.timedOut) to r.stdout
        }
        cachedStatus = detected
        Logger.i(TAG, "Root status: available=${detected.isAvailable} provider=${detected.provider}")
        detected
    }

    /**
     * Semua perintah root lewat SATU proses `su` yang tetap hidup (lihat [RootShell]), jadi Magisk/KernelSU
     * hanya menampilkan notifikasi "hak superuser" sekali, bukan tiap perintah. Perintah dijalankan satu
     * per satu. Batas waktu tetap berlaku per perintah: bila macet, shell ditutup dan dibuka ulang.
     */
    private suspend fun runInShell(command: String, timeoutMs: Long): RootCommandResult = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        shellLock.withLock {
            var out: RootShell.Output? = null
            for (attempt in 0..1) {
                if (!shell.isOpen) {
                    if (System.currentTimeMillis() - openFailedAt < OPEN_RETRY_DELAY_MS) break
                    if (!shell.open()) { openFailedAt = System.currentTimeMillis(); break }
                }
                out = shell.run(command, timeoutMs)
                if (!out.shellFailed) break
                // Shell mati di tengah jalan: coba sekali lagi dengan shell baru.
                out = null
            }
            val duration = System.currentTimeMillis() - start
            if (out == null) {
                openFailedAt = if (openFailedAt == 0L) System.currentTimeMillis() else openFailedAt
                RootCommandResult(command, -1, "", "root shell unavailable", false, duration)
            } else if (out.timedOut) {
                Logger.e(TAG, "Timeout after ${timeoutMs}ms: $command")
                RootCommandResult(command, -1, "", "timeout after ${timeoutMs}ms", true, duration)
            } else {
                openFailedAt = 0L
                RootCommandResult(command, out.exitCode, out.output, if (out.exitCode != 0) out.output else "", false, duration)
            }
        }
    }

    override suspend fun execute(command: String, timeoutMs: Long): RootCommandResult {
        val trimmed = command.trim()
        if (trimmed.isEmpty()) {
            Logger.w(TAG, "Rejected empty root command")
            return RootCommandResult("", -1, "", "validation: empty command", false, 0)
        }
        if (trimmed.contains("\n")) {
            Logger.w(TAG, "Rejected multi-line root command")
            return RootCommandResult(trimmed, -1, "", "validation: multi-line command not allowed", false, 0)
        }
        val result = runInShell(trimmed, timeoutMs)
        if (result.isSuccess) {
            Logger.d(TAG, "OK (${result.durationMs}ms): $trimmed")
        } else {
            Logger.w(TAG, "FAIL exit=${result.exitCode} (${result.durationMs}ms): $trimmed :: ${result.stderr}")
        }
        return result
    }

    override suspend fun readFile(path: String): RootCommandResult {
        if (path.isBlank() || !isSafePath(path)) {
            return RootCommandResult(path, -1, "", "validation: invalid path", false, 0)
        }
        return execute("cat '$path' 2>/dev/null")
    }

    override suspend fun writeFile(path: String, value: String, timeoutMs: Long): RootCommandResult {
        if (path.isBlank() || !isSafePath(path)) {
            return RootCommandResult(path, -1, "", "validation: invalid or unsafe path", false, 0)
        }
        if (!isSafeSysfsValue(value)) {
            // Every real caller writes a governor name, a frequency in kHz, a
            // 0/1/Y/N flag, or a policy name -- all pure ASCII, no shell
            // metacharacters. Anything else is refused outright instead of
            // being silently rewritten: a sysfs write is not the place to
            // "helpfully" mutate the caller's intent.
            Logger.w(TAG, "Rejected unsafe sysfs value for $path: $value")
            return RootCommandResult(path, -1, "", "validation: value contains characters not allowed in a sysfs write", false, 0)
        }
        // No chmod: root already bypasses the file's permission bits, and
        // widening a kernel node's permissions is an unrelated, unnecessary
        // side effect this app has no reason to cause.
        val command = "echo '$value' > '$path'"
        val first = execute(command, timeoutMs)
        // Kegagalan tanpa pesan apa pun (exit != 0, keluaran kosong) pernah terjadi pada governor GPU,
        // padahal perintah yang sama berhasil dari Termux. Coba cara tulis lain sebelum menyerah:
        // printf tanpa baris baru (satu kali write), lalu dd yang mencetak galat sebenarnya dari kernel.
        if (first.isSuccess || first.timedOut || first.stderr.isNotBlank()) return first

        val second = execute("printf '%s' '$value' > '$path'", timeoutMs)
        if (second.isSuccess) {
            Logger.w(TAG, "echo write failed silently but printf succeeded for $path")
            return second
        }
        val diag = execute("echo '$value' | dd of='$path' 2>&1", timeoutMs)
        if (diag.isSuccess) {
            Logger.w(TAG, "echo/printf failed but dd succeeded for $path")
            return diag
        }
        val reason = "echo exit ${first.exitCode}, printf exit ${second.exitCode}" +
            diag.stdout.trim().takeIf { it.isNotEmpty() }?.let { "; dd: " + it.takeLast(140) }.orEmpty()
        return RootCommandResult(command, first.exitCode, "", reason, false, first.durationMs + second.durationMs + diag.durationMs)
    }

    /** No shell metacharacters, no path traversal, no quotes -- this is placed straight into a single-quoted shell argument. */
    private fun isSafePath(path: String): Boolean =
        path.startsWith("/") && SAFE_PATH_REGEX.matches(path)

    /**
     * Sysfs values written by this app are always short, plain tokens:
     * governor names, frequencies, flags, or thermal policy names. None of
     * these ever need a shell metacharacter -- no quotes, no `;`, `|`, `&`,
     * backtick, `$`, backslash, or newline, so the class list stays
     * intentionally narrow rather than trying to blocklist dangerous
     * characters one by one.
     */
    private fun isSafeSysfsValue(value: String): Boolean =
        value.isNotEmpty() && value.length <= 64 && SAFE_VALUE_REGEX.matches(value)

    private companion object {
        const val OPEN_RETRY_DELAY_MS = 3000L
        val SAFE_PATH_REGEX = Regex("""^/[A-Za-z0-9_./\-]+$""")
        val SAFE_VALUE_REGEX = Regex("""^[A-Za-z0-9_ ,.:+\-]+$""")
    }
}
