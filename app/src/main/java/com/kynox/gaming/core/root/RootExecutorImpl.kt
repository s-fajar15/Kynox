package com.kynox.gaming.core.root

import com.kynox.gaming.core.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

private const val TAG = "RootExecutor"

class RootExecutorImpl : RootExecutor {

    private val lock = Mutex()
    @Volatile private var cachedStatus: RootStatus? = null

    override suspend fun status(): RootStatus = cachedStatus ?: refreshStatus()

    override suspend fun refreshStatus(): RootStatus = lock.withLock {
        val detected = RootDetector.detect()
        cachedStatus = detected
        Logger.i(TAG, "Root status: available=${detected.isAvailable} provider=${detected.provider}")
        detected
    }

    /**
     * IMPORTANT: this does not wrap the process I/O in withTimeoutOrNull.
     * Coroutine cancellation from a timeout does NOT interrupt a raw blocking
     * java.io stream read -- if a stuck sysfs node or a wedged su process
     * never produces EOF, that read blocks forever regardless of any
     * coroutine-level timeout wrapped around it, which is exactly what was
     * freezing the app (ANR) on screens that read several nodes. The fix is
     * to bound the *process* itself with Process.waitFor(timeout, unit) --
     * which Java guarantees returns on time -- and only read its output
     * (or force-kill it) once we know it has actually exited.
     */
    override suspend fun execute(command: String, timeoutMs: Long): RootCommandResult = withContext(Dispatchers.IO) {
        val trimmed = command.trim()
        if (trimmed.isEmpty()) {
            Logger.w(TAG, "Rejected empty root command")
            return@withContext RootCommandResult("", -1, "", "validation: empty command", false, 0)
        }
        if (trimmed.contains("\n")) {
            Logger.w(TAG, "Rejected multi-line root command")
            return@withContext RootCommandResult(trimmed, -1, "", "validation: multi-line command not allowed", false, 0)
        }

        val start = System.currentTimeMillis()
        var process: Process? = null
        val result = try {
            // Merge stderr into stdout: reading two separate pipes
            // sequentially (stdout fully, then stderr) can deadlock if the
            // unread pipe fills up while the process is still writing to it.
            // One merged stream removes that failure mode entirely.
            process = ProcessBuilder("su", "-c", trimmed)
                .redirectErrorStream(true)
                .start()

            val exited = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (!exited) {
                Logger.e(TAG, "Timeout after ${timeoutMs}ms, killing process: $trimmed")
                process.destroyForcibly()
                RootCommandResult(trimmed, -1, "", "timeout after ${timeoutMs}ms", true, System.currentTimeMillis() - start)
            } else {
                // Process has exited, so its output is fully buffered and
                // this read cannot block.
                val stdout = readStream(process.inputStream)
                val exitCode = process.exitValue()
                RootCommandResult(trimmed, exitCode, stdout, if (exitCode != 0) stdout else "", false, System.currentTimeMillis() - start)
            }
        } catch (t: Throwable) {
            Logger.e(TAG, "su exec failed for: $trimmed", t)
            RootCommandResult(trimmed, -1, "", t.message ?: "unknown error", false, System.currentTimeMillis() - start)
        } finally {
            process?.let { if (it.isAlive) it.destroyForcibly() }
        }

        if (result.isSuccess) {
            Logger.d(TAG, "OK (${result.durationMs}ms): $trimmed")
        } else {
            Logger.w(TAG, "FAIL exit=${result.exitCode} (${result.durationMs}ms): $trimmed :: ${result.stderr}")
        }
        result
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
        return execute(command, timeoutMs)
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
        val SAFE_PATH_REGEX = Regex("""^/[A-Za-z0-9_./\-]+$""")
        val SAFE_VALUE_REGEX = Regex("""^[A-Za-z0-9_ ,.:+\-]+$""")
    }

    private fun readStream(stream: java.io.InputStream): String {
        return BufferedReader(InputStreamReader(stream)).use { it.readText() }.trim()
    }
}
