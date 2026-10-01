package com.kynox.gaming.data.gpu

import com.kynox.gaming.core.root.RootExecutor
import com.kynox.gaming.core.sysfs.CapabilityEngine
import com.kynox.gaming.core.sysfs.SysfsAccess
import com.kynox.gaming.core.utils.AppResult
import com.kynox.gaming.core.utils.normalizeSensorTemperature
import com.kynox.gaming.data.logs.LogRepository
import com.kynox.gaming.data.root.RootRepository
import com.kynox.gaming.domain.model.GpuState
import com.kynox.gaming.domain.model.LogEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * GPU manager (PRD section 7). Only exposes controls if a devfreq interface
 * is actually discovered under the kgsl base path -- otherwise reports
 * "Not supported by this kernel" rather than showing dead controls.
 *
 * Reads go through the root-aware SysfsAccess helpers: some kernels (seen on
 * MIUI) restrict kgsl nodes to root even though the app itself has root, so a
 * plain java.io.File check alone under-reports support.
 */
class GpuRepository(
    private val capabilityEngine: CapabilityEngine,
    private val rootRepository: RootRepository,
    private val rootExecutor: RootExecutor,
    private val logRepository: LogRepository
) {

    suspend fun readState(): GpuState = withContext(Dispatchers.IO) {
        val base = capabilityEngine.kgslBasePath()
            ?: return@withContext unsupported("Not supported by this kernel.")

        val devfreq = "$base/devfreq"
        val rootAvailable = rootRepository.current().isAvailable

        val paths = listOf(
            "$base/gpuclk", "$devfreq/cur_freq", "$devfreq/min_freq", "$devfreq/max_freq",
            "$devfreq/available_frequencies", "$devfreq/governor", "$devfreq/available_governors",
            "$base/gpu_busy_percentage", "$base/gpubusy", "$base/temp"
        )
        val values = SysfsAccess.readMultipleWithRoot(paths, rootAvailable, rootExecutor)

        val curFreq = values["$base/gpuclk"] ?: values["$devfreq/cur_freq"]
        val minFreq = values["$devfreq/min_freq"]
        val maxFreq = values["$devfreq/max_freq"]
        val availableFreqs = values["$devfreq/available_frequencies"]
            ?.split(Regex("\\s+"))?.mapNotNull { it.toIntOrNull() }?.sorted() ?: emptyList()
        val governor = values["$devfreq/governor"]
        val availableGovernors = values["$devfreq/available_governors"]
            ?.split(Regex("\\s+"))?.filter { it.isNotBlank() } ?: emptyList()
        val busy = values["$base/gpu_busy_percentage"]?.replace("%", "")?.trim()?.toFloatOrNull()
            ?: values["$base/gpubusy"]?.split(Regex("\\s+"))?.firstOrNull()?.toFloatOrNull()
        // A flat 0 here usually means the kgsl temp node exists but was
        // never wired to real hardware on this kernel, not a real reading.
        val temp = values["$base/temp"]?.toFloatOrNull()?.let { normalizeSensorTemperature(it, treatZeroAsMissing = true) }

        if (curFreq == null && governor == null) {
            return@withContext unsupported("Not supported by this kernel.")
        }

        val governorPath = "$devfreq/governor"
        val maxFreqPath = "$devfreq/max_freq"
        GpuState(
            supported = true,
            currentFreqKhz = curFreq?.toIntOrNull(),
            minFreqKhz = minFreq?.toIntOrNull(),
            maxFreqKhz = maxFreq?.toIntOrNull(),
            availableFreqsKhz = availableFreqs,
            governor = governor,
            availableGovernors = availableGovernors,
            utilizationPercent = busy,
            temperatureCelsius = temp,
            canWriteGovernor = values[governorPath] != null && (rootAvailable || File(governorPath).canWrite()),
            canWriteFreq = values[maxFreqPath] != null && (rootAvailable || File(maxFreqPath).canWrite()),
            reason = "GPU devfreq interface detected"
        )
    }

    /** VALIDATE (against this GPU's own `available_governors`) -> APPLY -> VERIFY (readback), same discipline as CpuRepository.setGovernor. */
    suspend fun setGovernor(governor: String): AppResult<Unit> = withContext(Dispatchers.IO) {
        val base = capabilityEngine.kgslBasePath() ?: return@withContext AppResult.Failure("Not supported by this kernel.")
        val rootAvailable = rootRepository.current().isAvailable
        val path = "$base/devfreq/governor"
        if (!SysfsAccess.existsWithRoot(path, rootAvailable, rootExecutor)) return@withContext AppResult.Failure("Governor control not available")

        val available = SysfsAccess.readWithRoot("$base/devfreq/available_governors", rootAvailable, rootExecutor)
            ?.split(Regex("\\s+"))?.filter { it.isNotBlank() } ?: emptyList()
        if (available.isNotEmpty() && available.none { it.equals(governor, ignoreCase = true) }) {
            return@withContext AppResult.Failure("\"$governor\" is not in this GPU's available governors: $available")
        }

        val previous = SysfsAccess.readWithRoot(path, rootAvailable, rootExecutor)
        val result = rootExecutor.writeFile(path, governor)
        val verified = result.isSuccess &&
            SysfsAccess.readWithRoot(path, rootAvailable, rootExecutor)?.trim()?.equals(governor, ignoreCase = true) == true
        val failure = when {
            !result.isSuccess -> result.stderr.ifBlank { "Write failed" }
            !verified -> "write reported success but readback did not match"
            else -> null
        }
        logRepository.append(
            LogEntry(System.currentTimeMillis(), "SET_GPU_GOVERNOR", path, previous, governor,
                if (failure == null) "SUCCESS" else "FAILED", failure)
        )
        if (failure == null) AppResult.Success(Unit) else AppResult.Failure(failure)
    }

    /** VALIDATE (against this GPU's own `available_frequencies`) -> APPLY -> VERIFY (readback). The max advertised by the kernel is not assumed safe on its own -- only membership in the device's own table is trusted. */
    suspend fun setMaxFreq(maxKhz: Int): AppResult<Unit> = withContext(Dispatchers.IO) {
        val base = capabilityEngine.kgslBasePath() ?: return@withContext AppResult.Failure("Not supported by this kernel.")
        val rootAvailable = rootRepository.current().isAvailable
        val path = "$base/devfreq/max_freq"
        if (!SysfsAccess.existsWithRoot(path, rootAvailable, rootExecutor)) return@withContext AppResult.Failure("Frequency control not available")

        val available = SysfsAccess.readWithRoot("$base/devfreq/available_frequencies", rootAvailable, rootExecutor)
            ?.split(Regex("\\s+"))?.mapNotNull { it.toIntOrNull() } ?: emptyList()
        if (available.isNotEmpty() && maxKhz !in available) {
            return@withContext AppResult.Failure("$maxKhz kHz is not one of this GPU's available frequencies")
        }
        val minFreq = SysfsAccess.readWithRoot("$base/devfreq/min_freq", rootAvailable, rootExecutor)?.trim()?.toIntOrNull()
        if (minFreq != null && maxKhz < minFreq) {
            return@withContext AppResult.Failure("max frequency ($maxKhz kHz) cannot be less than the current min ($minFreq kHz)")
        }

        val previous = SysfsAccess.readWithRoot(path, rootAvailable, rootExecutor)
        val result = rootExecutor.writeFile(path, maxKhz.toString())
        val verified = result.isSuccess &&
            SysfsAccess.readWithRoot(path, rootAvailable, rootExecutor)?.trim()?.toIntOrNull() == maxKhz
        val failure = when {
            !result.isSuccess -> result.stderr.ifBlank { "Write failed" }
            !verified -> "write reported success but readback did not match"
            else -> null
        }
        logRepository.append(
            LogEntry(System.currentTimeMillis(), "SET_GPU_MAX_FREQ", path, previous, maxKhz.toString(),
                if (failure == null) "SUCCESS" else "FAILED", failure)
        )
        if (failure == null) AppResult.Success(Unit) else AppResult.Failure(failure)
    }

    private fun unsupported(reason: String) = GpuState(
        supported = false, currentFreqKhz = null, minFreqKhz = null, maxFreqKhz = null,
        availableFreqsKhz = emptyList(), governor = null, availableGovernors = emptyList(),
        utilizationPercent = null, temperatureCelsius = null, canWriteGovernor = false, canWriteFreq = false,
        reason = reason
    )
}
