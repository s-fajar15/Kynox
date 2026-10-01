package com.kynox.gaming.core.sysfs

import com.kynox.gaming.core.root.RootExecutor
import com.kynox.gaming.core.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

private const val TAG = "CapabilityEngine"

/**
 * Implements the discovery flow required by the PRD:
 * Scan -> Detect -> Validate -> Check Read/Write -> Build Capabilities.
 * Nothing here assumes a fixed vendor path; every path is confirmed to
 * exist on the running device before it is treated as supported.
 *
 * Results that describe fixed hardware (core count, GPU base path, the set
 * of power_supply nodes) are cached after the first successful lookup --
 * they cannot change while the app is running, so re-probing them (some of
 * which cost a root shell call) on every poll cycle was pure waste.
 */
class CapabilityEngine(private val rootExecutor: RootExecutor) {

    @Volatile private var cpuCoreCountCache: Int? = null
    @Volatile private var thermalZonePathsCache: List<String>? = null
    @Volatile private var powerSupplyNamesCache: List<String>? = null
    @Volatile private var kgslBasePathChecked: Boolean = false
    @Volatile private var kgslBasePathCache: String? = null

    suspend fun cpuCoreCount(): Int = withContext(Dispatchers.IO) {
        cpuCoreCountCache?.let { return@withContext it }
        val fromCpuDir = SysfsAccess.listChildren("/sys/devices/system/cpu")
            .count { it.matches(Regex("cpu[0-9]+")) }
        val result = if (fromCpuDir > 0) fromCpuDir else Runtime.getRuntime().availableProcessors()
        cpuCoreCountCache = result
        result
    }

    suspend fun thermalZonePaths(): List<String> = withContext(Dispatchers.IO) {
        thermalZonePathsCache?.let { return@withContext it }
        val result = SysfsAccess.listChildren("/sys/class/thermal")
            .filter { it.startsWith("thermal_zone") }
            .map { "/sys/class/thermal/$it" }
            .sortedBy { it.removePrefix("/sys/class/thermal/thermal_zone").toIntOrNull() ?: 0 }
        thermalZonePathsCache = result
        result
    }

    suspend fun powerSupplyNames(): List<String> = withContext(Dispatchers.IO) {
        powerSupplyNamesCache?.let { return@withContext it }
        val rootAvailable = rootExecutor.status().isAvailable
        val result = SysfsAccess.listChildrenWithRoot("/sys/class/power_supply", rootAvailable, rootExecutor)
        if (result.isNotEmpty()) powerSupplyNamesCache = result
        result
    }

    suspend fun kgslBasePath(): String? = withContext(Dispatchers.IO) {
        if (kgslBasePathChecked) return@withContext kgslBasePathCache
        val rootAvailable = rootExecutor.status().isAvailable
        val candidates = listOf("/sys/class/kgsl/kgsl-3d0", "/sys/kernel/gpu")
        val found = candidates.firstOrNull { SysfsAccess.existsWithRoot(it, rootAvailable, rootExecutor) }
        kgslBasePathCache = found
        kgslBasePathChecked = true
        found
    }

    /**
     * Probes a batch of candidate sysfs paths in parallel and returns only
     * the ones that actually exist on this device.
     */
    suspend fun discoverExisting(candidates: List<String>): List<SysfsNode> = withContext(Dispatchers.IO) {
        val rootAvailable = rootExecutor.status().isAvailable
        val deferred = candidates.map { path ->
            async { SysfsAccess.probeNode(path, rootAvailable, rootExecutor) }
        }
        val results = deferred.awaitAll().filter { it.exists }
        Logger.d(TAG, "discoverExisting: ${results.size}/${candidates.size} candidates present")
        results
    }

    suspend fun cpuFrequencyPaths(core: Int): CpuCoreSysfsPaths = withContext(Dispatchers.IO) {
        val base = "/sys/devices/system/cpu/cpu$core/cpufreq"
        CpuCoreSysfsPaths(
            core = core,
            curFreqPath = "$base/scaling_cur_freq".takeIf { SysfsAccess.existsDirect(it) },
            minFreqPath = "$base/scaling_min_freq".takeIf { SysfsAccess.existsDirect(it) },
            maxFreqPath = "$base/scaling_max_freq".takeIf { SysfsAccess.existsDirect(it) },
            availableFreqPath = "$base/scaling_available_frequencies".takeIf { SysfsAccess.existsDirect(it) },
            governorPath = "$base/scaling_governor".takeIf { SysfsAccess.existsDirect(it) },
            availableGovernorsPath = "$base/scaling_available_governors".takeIf { SysfsAccess.existsDirect(it) },
            onlinePath = "/sys/devices/system/cpu/cpu$core/online".takeIf { SysfsAccess.existsDirect(it) }
        )
    }
}

data class CpuCoreSysfsPaths(
    val core: Int,
    val curFreqPath: String?,
    val minFreqPath: String?,
    val maxFreqPath: String?,
    val availableFreqPath: String?,
    val governorPath: String?,
    val availableGovernorsPath: String?,
    val onlinePath: String?
)
