package com.kynox.gaming.data.cpu

import com.kynox.gaming.core.root.RootExecutor
import com.kynox.gaming.core.sysfs.CapabilityEngine
import com.kynox.gaming.core.sysfs.SysfsAccess
import com.kynox.gaming.core.utils.AppResult
import com.kynox.gaming.data.logs.LogRepository
import com.kynox.gaming.data.root.RootRepository
import com.kynox.gaming.domain.model.CpuCoreState
import com.kynox.gaming.domain.model.CpuSnapshot
import com.kynox.gaming.domain.model.LogEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class CpuRepository(
    private val capabilityEngine: CapabilityEngine,
    private val rootRepository: RootRepository,
    private val rootExecutor: RootExecutor,
    private val logRepository: LogRepository
) {

    @Volatile private var lastTotalJiffies: Long = -1
    @Volatile private var lastIdleJiffies: Long = -1

    suspend fun readSnapshot(): CpuSnapshot = withContext(Dispatchers.IO) {
        val rootAvailable = rootRepository.current().isAvailable
        val coreCount = capabilityEngine.cpuCoreCount()
        val cores = (0 until coreCount).map { core -> readCore(core, rootAvailable) }
        CpuSnapshot(cores = cores, overallUsagePercent = sampleOverallUsage(rootAvailable))
    }

    private suspend fun readCore(core: Int, rootAvailable: Boolean): CpuCoreState {
        val paths = capabilityEngine.cpuFrequencyPaths(core)

        val online = paths.onlinePath?.let { SysfsAccess.readDirect(it) == "1" } ?: true
        val curFreq = paths.curFreqPath?.let { SysfsAccess.readDirect(it)?.toIntOrNull() }
        val minFreq = paths.minFreqPath?.let { SysfsAccess.readDirect(it)?.toIntOrNull() }
        val maxFreq = paths.maxFreqPath?.let { SysfsAccess.readDirect(it)?.toIntOrNull() }
        val availableFreqs = paths.availableFreqPath
            ?.let { SysfsAccess.readDirect(it) }
            ?.split(Regex("\\s+"))
            ?.mapNotNull { it.toIntOrNull() }
            ?.sorted()
            ?: emptyList()
        val governor = paths.governorPath?.let { SysfsAccess.readDirect(it) }
        val availableGovernors = paths.availableGovernorsPath
            ?.let { SysfsAccess.readDirect(it) }
            ?.split(Regex("\\s+"))
            ?.filter { it.isNotBlank() }
            ?: emptyList()

        val canWriteGovernor = paths.governorPath?.let { isWritable(it, rootAvailable) } ?: false
        val canWriteFreq = paths.minFreqPath?.let { isWritable(it, rootAvailable) } ?: false

        return CpuCoreState(
            core = core,
            online = paths.onlinePath?.let { online },
            currentFreqKhz = curFreq,
            minFreqKhz = minFreq,
            maxFreqKhz = maxFreq,
            availableFreqsKhz = availableFreqs,
            governor = governor,
            availableGovernors = availableGovernors,
            canWriteGovernor = canWriteGovernor,
            canWriteFreq = canWriteFreq,
            canToggleOnline = core != 0 && paths.onlinePath != null && isWritable(paths.onlinePath, rootAvailable)
        )
    }

    private fun isWritable(path: String, rootAvailable: Boolean): Boolean {
        val file = File(path)
        if (!file.exists()) return false
        return if (rootAvailable) true else file.canWrite()
    }

    /**
     * VALIDATE -> APPLY -> VERIFY. [governor] is checked against this core's
     * own `available_governors` before anything is written -- a name that
     * isn't actually offered by this kernel is refused rather than sent to
     * `su` to find out the hard way. After the write, the node is read back
     * and the change confirmed, not assumed from the shell's exit code alone
     * (a write can "succeed" and still be silently reverted by a vendor
     * daemon).
     */
    suspend fun setGovernor(core: Int, governor: String): AppResult<Unit> = withContext(Dispatchers.IO) {
        val paths = capabilityEngine.cpuFrequencyPaths(core)
        val path = paths.governorPath ?: return@withContext AppResult.Failure("Governor control not available on core $core")
        val available = paths.availableGovernorsPath?.let { SysfsAccess.readDirect(it) }
            ?.split(Regex("\\s+"))?.filter { it.isNotBlank() } ?: emptyList()
        if (available.isNotEmpty() && available.none { it.equals(governor, ignoreCase = true) }) {
            return@withContext AppResult.Failure("\"$governor\" is not in this core's available governors: $available")
        }

        val previous = SysfsAccess.readDirect(path)
        val result = rootExecutor.writeFile(path, governor)
        val verified = result.isSuccess && SysfsAccess.readDirect(path)?.trim()?.equals(governor, ignoreCase = true) == true
        val failure = when {
            !result.isSuccess -> result.stderr.ifBlank { "unknown error" }
            !verified -> "write reported success but readback did not match (kernel or vendor daemon reverted it)"
            else -> null
        }
        logRepository.append(
            LogEntry(
                timestamp = System.currentTimeMillis(),
                action = "SET_GOVERNOR",
                target = path,
                previousValue = previous,
                newValue = governor,
                result = if (failure == null) "SUCCESS" else "FAILED",
                error = failure
            )
        )
        if (failure == null) AppResult.Success(Unit) else AppResult.Failure(failure)
    }

    /**
     * VALIDATE -> APPLY -> VERIFY, with rollback of whichever half already
     * succeeded if the other fails: this device's `available_frequencies`
     * (not "the largest number the kernel will accept") is the source of
     * truth for what's safe, and min<=max is checked before either write is
     * attempted so a bad pair can never be sent half-applied.
     */
    suspend fun setMinMaxFreq(core: Int, minKhz: Int?, maxKhz: Int?): AppResult<Unit> = withContext(Dispatchers.IO) {
        val paths = capabilityEngine.cpuFrequencyPaths(core)
        val available = paths.availableFreqPath?.let { SysfsAccess.readDirect(it) }
            ?.split(Regex("\\s+"))?.mapNotNull { it.toIntOrNull() } ?: emptyList()

        if (available.isNotEmpty()) {
            if (minKhz != null && minKhz !in available) {
                return@withContext AppResult.Failure("$minKhz kHz is not one of this core's available frequencies")
            }
            if (maxKhz != null && maxKhz !in available) {
                return@withContext AppResult.Failure("$maxKhz kHz is not one of this core's available frequencies")
            }
        }
        if (minKhz != null && maxKhz != null && minKhz > maxKhz) {
            return@withContext AppResult.Failure("min frequency ($minKhz kHz) cannot be greater than max ($maxKhz kHz)")
        }

        var minPrevious: String? = null
        var minWritten = false
        var lastError: String? = null

        if (minKhz != null && paths.minFreqPath != null) {
            minPrevious = SysfsAccess.readDirect(paths.minFreqPath)
            val result = rootExecutor.writeFile(paths.minFreqPath, minKhz.toString())
            val verified = result.isSuccess && SysfsAccess.readDirect(paths.minFreqPath)?.trim()?.toIntOrNull() == minKhz
            minWritten = verified
            val failure = if (!result.isSuccess) result.stderr.ifBlank { "Write failed" }
                else if (!verified) "readback did not match after write" else null
            logRepository.append(
                LogEntry(System.currentTimeMillis(), "SET_MIN_FREQ", paths.minFreqPath, minPrevious, minKhz.toString(),
                    if (failure == null) "SUCCESS" else "FAILED", failure)
            )
            lastError = failure
        }

        if (maxKhz != null && paths.maxFreqPath != null && lastError == null) {
            val maxPrevious = SysfsAccess.readDirect(paths.maxFreqPath)
            val result = rootExecutor.writeFile(paths.maxFreqPath, maxKhz.toString())
            val verified = result.isSuccess && SysfsAccess.readDirect(paths.maxFreqPath)?.trim()?.toIntOrNull() == maxKhz
            val failure = if (!result.isSuccess) result.stderr.ifBlank { "Write failed" }
                else if (!verified) "readback did not match after write" else null
            logRepository.append(
                LogEntry(System.currentTimeMillis(), "SET_MAX_FREQ", paths.maxFreqPath, maxPrevious, maxKhz.toString(),
                    if (failure == null) "SUCCESS" else "FAILED", failure)
            )
            if (failure != null) {
                // GPU-style partial failure, but within one core: max failed after min
                // already changed -- put min back rather than leave a half-applied pair.
                if (minWritten && minPrevious != null) {
                    val rollback = rootExecutor.writeFile(paths.minFreqPath!!, minPrevious)
                    logRepository.append(
                        LogEntry(System.currentTimeMillis(), "ROLLBACK_MIN_FREQ", paths.minFreqPath, minKhz.toString(), minPrevious,
                            if (rollback.isSuccess) "SUCCESS" else "FAILED", if (rollback.isSuccess) null else rollback.stderr)
                    )
                }
                lastError = failure
            }
        }

        if (lastError == null) AppResult.Success(Unit) else AppResult.Failure(lastError)
    }

    /**
     * Core 0 is refused unconditionally, before even touching sysfs: on
     * most SoCs it is the one guaranteed to still be handling interrupts
     * and the boot path, and taking it offline is a common cause of a
     * rooted device hanging until reboot.
     */
    suspend fun setOnline(core: Int, online: Boolean): AppResult<Unit> = withContext(Dispatchers.IO) {
        if (core == 0) return@withContext AppResult.Failure("Core 0 cannot be taken offline")
        val paths = capabilityEngine.cpuFrequencyPaths(core)
        val path = paths.onlinePath ?: return@withContext AppResult.Failure("Online control not available on core $core")
        if (path.contains("'")) return@withContext AppResult.Failure("Invalid path for core $core")
        val previous = SysfsAccess.readDirect(path)
        val value = if (online) "1" else "0"

        // One command that writes, reports the shell's exit code, and reads the file back as root, so a refusal
        // can be told apart from a write that "worked" but that the kernel or a vendor daemon then undid.
        val result = rootExecutor.execute(
            "echo $value > '$path'; rc=\$?; echo \"rc=\$rc\"; cat '$path' 2>&1; exit \$rc",
            timeoutMs = 5000L
        )
        val lines = result.stdout.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        val exitCode = lines.firstOrNull { it.startsWith("rc=") }?.removePrefix("rc=")?.toIntOrNull()
        val readBack = lines.lastOrNull { it != "rc=$exitCode" && !it.startsWith("rc=") && it.length <= 3 }
        val shellMessage = lines.filter { !it.startsWith("rc=") && it != readBack }.joinToString(" ").take(120)

        val failure: String? = when {
            result.timedOut -> "Timed out writing $path"
            !result.isSuccess -> {
                val detail = shellMessage.ifBlank { "the kernel refused the write and gave no message" }
                "cpu$core online -> $value refused (rc=${exitCode ?: result.exitCode}): $detail"
            }
            readBack != null && readBack != value ->
                "cpu$core online -> $value was ignored (still $readBack); a kernel or vendor service keeps the core in that state"
            else -> null
        }
        logRepository.append(
            LogEntry(System.currentTimeMillis(), "SET_CORE_ONLINE", path, previous, value,
                if (failure == null) "SUCCESS" else "FAILED", failure)
        )
        if (failure == null) AppResult.Success(Unit) else AppResult.Failure(failure)
    }

    private suspend fun sampleOverallUsage(rootAvailable: Boolean): Float? {
        // Android has blocked non-root apps from reading global /proc/stat
        // since N-era hardening; fall back to root so usage isn't just "Unknown".
        val statLine = SysfsAccess.readWithRoot("/proc/stat", rootAvailable, rootExecutor)
            ?.lineSequence()?.firstOrNull { it.startsWith("cpu ") }
            ?: return null
        val fields = statLine.trim().split(Regex("\\s+")).drop(1).mapNotNull { it.toLongOrNull() }
        if (fields.size < 4) return null

        val idle = fields[3] + (fields.getOrNull(4) ?: 0L)
        val total = fields.take(8.coerceAtMost(fields.size)).sum()

        val result = if (lastTotalJiffies >= 0 && total > lastTotalJiffies) {
            val totalDelta = total - lastTotalJiffies
            val idleDelta = idle - lastIdleJiffies
            if (totalDelta > 0) (((totalDelta - idleDelta).toFloat() / totalDelta.toFloat()) * 100f).coerceIn(0f, 100f) else null
        } else null

        lastTotalJiffies = total
        lastIdleJiffies = idle
        return result
    }
}
