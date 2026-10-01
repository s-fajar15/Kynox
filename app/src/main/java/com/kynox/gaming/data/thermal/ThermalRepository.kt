package com.kynox.gaming.data.thermal

import com.kynox.gaming.core.root.RootExecutor
import com.kynox.gaming.core.sysfs.CapabilityEngine
import com.kynox.gaming.core.sysfs.SysfsAccess
import com.kynox.gaming.core.utils.AppResult
import com.kynox.gaming.core.utils.normalizeSensorTemperature
import com.kynox.gaming.data.backup.BackupStore
import com.kynox.gaming.data.logs.LogRepository
import com.kynox.gaming.data.root.RootRepository
import com.kynox.gaming.domain.model.LogEntry
import com.kynox.gaming.domain.model.ThermalDisableState
import com.kynox.gaming.domain.model.ThermalEngineMode
import com.kynox.gaming.domain.model.ThermalPolicySummary
import com.kynox.gaming.domain.model.ThermalTripPoint
import com.kynox.gaming.domain.model.ThermalZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val BACKUP_KEY = "thermal_disable"
private const val POLICY_BACKUP_KEY = "thermal_policy_backup"
private val servicePropPattern = Regex("""\[init\.svc\.([^\]]+)\]:\s*\[(\w*)\]""")

/**
 * Dynamic thermal zone monitoring (PRD section 8) plus the "disable thermal"
 * control (PRD section 20), rebuilt from the reference Magisk module the
 * user supplied so it runs live through RootExecutor instead of a flashed
 * module. Every write goes through Check -> Backup -> Write -> Verify -> Log.
 */
class ThermalRepository(
    private val capabilityEngine: CapabilityEngine,
    private val rootRepository: RootRepository,
    private val rootExecutor: RootExecutor,
    private val backupStore: BackupStore,
    private val logRepository: LogRepository
) {

    // Candidate module parameters observed across MSM/Qualcomm platforms
    // (including the Poco X3 Pro's Snapdragon 860). Each is verified to
    // exist on the running device before it is ever touched.
    private val moduleParamCandidates = listOf(
        "/sys/module/msm_thermal/parameters/enabled",
        "/sys/module/msm_thermal/core_control/enabled",
        "/sys/kernel/msm_thermal/enabled"
    )

    // Vendor thermal daemons/HALs that the reference module stops. Stopping
    // a service that does not exist on this device/ROM is a harmless no-op.
    private val vendorServices = listOf(
        "vendor.thermal-engine", "vendor.thermal_manager", "vendor.thermal-manager",
        "vendor.thermal-hal-2-0", "vendor.thermal-hal-1-0", "vendor.thermal-symlinks",
        "thermal_mnt_hal_service", "thermal", "mi_thermald", "thermald",
        "thermalloadalgod", "thermalservice", "thermal-engine"
    )

    suspend fun readZones(): List<ThermalZone> = withContext(Dispatchers.IO) {
        capabilityEngine.thermalZonePaths().map { zonePath ->
            val zoneName = zonePath.substringAfterLast('/')
            val type = SysfsAccess.readDirect("$zonePath/type") ?: "Unknown"
            val tempRaw = SysfsAccess.readDirect("$zonePath/temp")?.toFloatOrNull()
            val tempCelsius = tempRaw?.let { normalizeTemperature(it) }
            ThermalZone(
                zoneName = zoneName,
                sysfsPath = zonePath,
                sensorType = type,
                temperatureCelsius = tempCelsius,
                tripPoints = readTripPoints(zonePath),
                policy = SysfsAccess.readDirect("$zonePath/policy")?.trim()?.takeIf { it.isNotEmpty() },
                availablePolicies = SysfsAccess.readDirect("$zonePath/available_policies")
                    ?.trim()?.split(Regex("\\s+"))?.filter { it.isNotBlank() } ?: emptyList()
            )
        }
    }

    /**
     * One policy summary for the whole device instead of per zone, since
     * every zone that exposes a policy at all almost always offers the same
     * choices -- the profile screen shows and changes this once, the same
     * way the reference app's "Ubah Thermal Policy (semua zone)" dialog does.
     */
    suspend fun policySummary(): ThermalPolicySummary = withContext(Dispatchers.IO) {
        val zones = readZones()
        val withPolicy = zones.filter { it.policy != null }
        val availablePolicies = withPolicy.flatMap { it.availablePolicies }.distinct().sorted()
        ThermalPolicySummary(
            supported = withPolicy.isNotEmpty(),
            currentPolicy = withPolicy.firstOrNull()?.policy,
            availablePolicies = availablePolicies,
            supportedZoneCount = withPolicy.count { it.availablePolicies.isNotEmpty() },
            totalZoneCount = zones.size
        )
    }

    /**
     * Applied to every zone that both exposes `policy` and actually lists
     * [policy] in its own `available_policies` -- writing a policy a zone
     * never advertised is either silently ignored or rejected depending on
     * the kernel, so it is filtered out here instead of attempted blindly.
     * The very first policy set is backed up (per zone) so [ProfileRepository]'s
     * reset can put each zone back the way it found it.
     */
    suspend fun setPolicyForAllZones(policy: String): AppResult<Unit> = withContext(Dispatchers.IO) {
        val zones = readZones().filter { it.policy != null && it.availablePolicies.any { p -> p.equals(policy, ignoreCase = true) } }
        if (zones.isEmpty()) {
            return@withContext AppResult.Failure("Tidak ada zone yang mendukung policy \"$policy\"")
        }
        if (!backupStore.hasBackup(POLICY_BACKUP_KEY)) {
            backupStore.save(POLICY_BACKUP_KEY, zones.associate { it.sysfsPath to it.policy })
        }

        var succeeded = 0
        zones.forEach { zone ->
            val result = rootExecutor.writeFile("${zone.sysfsPath}/policy", policy)
            logRepository.append(
                LogEntry(System.currentTimeMillis(), "SET_THERMAL_POLICY", zone.sysfsPath, zone.policy, policy,
                    if (result.isSuccess) "SUCCESS" else "FAILED", if (result.isSuccess) null else result.stderr)
            )
            if (result.isSuccess) succeeded++
        }

        if (succeeded == 0) AppResult.Failure("Kernel menolak perubahan policy di semua zone")
        else AppResult.Success(Unit)
    }

    /** Restores each zone's own backed-up policy, not one value applied everywhere -- some devices genuinely run mixed policies across zones out of the box. */
    suspend fun restorePolicy(): AppResult<Unit> = withContext(Dispatchers.IO) {
        val backup = backupStore.load(POLICY_BACKUP_KEY)
            ?: return@withContext AppResult.Failure("Tidak ada policy tersimpan untuk dipulihkan")
        var succeeded = 0
        backup.forEach { (path, previous) ->
            if (previous == null) return@forEach
            val result = rootExecutor.writeFile("$path/policy", previous)
            logRepository.append(
                LogEntry(System.currentTimeMillis(), "RESTORE_THERMAL_POLICY", path, "changed", previous,
                    if (result.isSuccess) "SUCCESS" else "FAILED", if (result.isSuccess) null else result.stderr)
            )
            if (result.isSuccess) succeeded++
        }
        backupStore.clear(POLICY_BACKUP_KEY)
        if (succeeded == 0 && backup.isNotEmpty()) AppResult.Failure("Sebagian policy belum pulih")
        else AppResult.Success(Unit)
    }

    /**
     * Same zone/type/temperature data as [readZones] but skips the trip-point
     * scan (which does several extra file reads per zone). The dashboard only
     * ever needs a temperature match by name/type, not trip points, and it
     * polls every couple of seconds -- skipping that scan there noticeably
     * cuts the I/O this repository does per refresh.
     */
    private suspend fun readZoneTemperaturesOnly(): List<Triple<String, String, Float?>> = withContext(Dispatchers.IO) {
        capabilityEngine.thermalZonePaths().map { zonePath ->
            val zoneName = zonePath.substringAfterLast('/')
            val type = SysfsAccess.readDirect("$zonePath/type") ?: "Unknown"
            val tempRaw = SysfsAccess.readDirect("$zonePath/temp")?.toFloatOrNull()
            Triple(zoneName, type, tempRaw?.let { normalizeTemperature(it) })
        }
    }

    private fun readTripPoints(zonePath: String): List<ThermalTripPoint> {
        val points = mutableListOf<ThermalTripPoint>()
        var index = 0
        while (true) {
            val tempPath = "$zonePath/trip_point_${index}_temp"
            if (!SysfsAccess.existsDirect(tempPath)) break
            val temp = SysfsAccess.readDirect(tempPath)?.toFloatOrNull()?.let { normalizeTemperature(it) }
            val type = SysfsAccess.readDirect("$zonePath/trip_point_${index}_type")
            points.add(ThermalTripPoint(index, type, temp))
            index++
        }
        return points
    }

    // Real device temperatures never fall outside a plausible band, and
    // different kernels scale `temp` differently -- see
    // [normalizeSensorTemperature] for the full rationale.
    private fun normalizeTemperature(raw: Float): Float? = normalizeSensorTemperature(raw)

    /**
     * Best-effort match of a thermal zone to a component, for dashboard summaries.
     * Several zones can match the same keywords (e.g. more than one "cpu*"
     * zone); the first with an actually plausible reading wins, instead of
     * settling for whichever zone matched first even if its reading was
     * filtered out as invalid.
     */
    suspend fun findTemperatureFor(keywords: List<String>): Float? {
        val zones = readZoneTemperaturesOnly()
        return zones.asSequence()
            .filter { (name, type, _) -> keywords.any { keyword -> name.contains(keyword, true) || type.contains(keyword, true) } }
            .mapNotNull { it.third }
            .firstOrNull()
    }

    /**
     * Reads every zone's temperature once and matches both keyword sets
     * against that single result, instead of the dashboard triggering two
     * independent full zone scans (once per component) every refresh tick.
     * Like [findTemperatureFor], each keyword set tries every matching zone
     * in order and keeps the first plausible reading rather than the first match.
     */
    suspend fun findTemperaturesFor(primaryKeywords: List<String>, secondaryKeywords: List<String>): Pair<Float?, Float?> {
        val zones = readZoneTemperaturesOnly()
        fun match(keywords: List<String>) = zones.asSequence()
            .filter { (name, type, _) -> keywords.any { keyword -> name.contains(keyword, true) || type.contains(keyword, true) } }
            .mapNotNull { it.third }
            .firstOrNull()
        return match(primaryKeywords) to match(secondaryKeywords)
    }

    /**
     * Kernel-level switches this feature touches: each zone's non-critical
     * ("passive"/"active") trip thresholds -- plus the module parameters.
     * A zone's `mode` file is deliberately left untouched: writing
     * "disabled" there is the standard way to silence a zone's throttling
     * governor, but on this kernel family it also freezes `temp` reporting
     * to a sentinel value instead of leaving the sensor readable. Raising
     * the trip thresholds instead achieves the same practical goal --
     * throttling never reacts -- without ever touching `mode`, so `temp`
     * keeps reporting. The zone's "critical" trip (its last-resort hardware
     * shutdown protection) is never touched, in line with PRD section 13.
     */
    private suspend fun controlPaths(): Pair<List<String>, List<String>> {
        val tripPaths = capabilityEngine.thermalZonePaths().flatMap { zonePath ->
            val paths = mutableListOf<String>()
            var index = 0
            while (true) {
                val tempPath = "$zonePath/trip_point_${index}_temp"
                if (!SysfsAccess.existsDirect(tempPath)) break
                val type = SysfsAccess.readDirect("$zonePath/trip_point_${index}_type")?.trim()?.lowercase().orEmpty()
                if (type == "passive" || type == "active") paths.add(tempPath)
                index++
            }
            paths
        }
        val params = moduleParamCandidates.filter { SysfsAccess.existsDirect(it) }
        return tripPaths to params
    }

    /**
     * A relaxed trip is written in whatever scale ([normalizeSensorTemperature])
     * the zone's own existing value already uses, well above any realistic
     * operating temperature, so the kernel's reactive governor never fires.
     */
    private fun relaxedTripValue(currentRaw: Float?): String {
        val raw = currentRaw ?: return "150000"
        return when {
            kotlin.math.abs(raw) > 1000f -> "150000"
            kotlin.math.abs(raw) > 150f -> "1500"
            else -> "150"
        }
    }

    /** (running, stopped) among the vendor thermal daemons this repository knows how to stop. */
    private suspend fun readDaemonStates(): Pair<Int, Int> {
        val result = rootExecutor.execute("getprop | grep -i 'init.svc.*therm'", 4000L)
        var running = 0
        var stopped = 0
        servicePropPattern.findAll(result.stdout).forEach { match ->
            if (match.groupValues[1] !in vendorServices) return@forEach
            when (match.groupValues[2]) {
                "running" -> running++
                "stopped" -> stopped++
            }
        }
        return running to stopped
    }

    /**
     * Reports what the system is actually doing right now, not just whether
     * this app once ran "disable": the kernel switches are read back, and the
     * vendor daemons are checked. A saved backup on its own proves nothing,
     * because everything comes back by itself after a reboot.
     */
    suspend fun disableState(): ThermalDisableState = withContext(Dispatchers.IO) {
        val rootAvailable = rootRepository.current().isAvailable
        if (!rootAvailable) {
            return@withContext ThermalDisableState(supported = false, active = false, reason = "Requires root access")
        }
        val (tripPaths, params) = controlPaths()
        if (tripPaths.isEmpty() && params.isEmpty()) {
            return@withContext ThermalDisableState(supported = false, active = false, reason = "No compatible thermal control interface found on this kernel")
        }

        val values = SysfsAccess.readMultipleWithRoot(tripPaths + params, rootAvailable, rootExecutor)
        val backup = backupStore.load(BACKUP_KEY) ?: emptyMap()
        var total = 0
        var disabled = 0
        tripPaths.forEach { path ->
            val current = values[path]?.trim()
            if (!current.isNullOrEmpty()) {
                total++
                // A trip has no fixed "disabled" word to look for; a value
                // that no longer matches what was backed up before this
                // feature touched it means it has been relaxed.
                val original = backup[path]
                if (original != null && current != original) disabled++
            }
        }
        params.forEach { path ->
            val value = values[path]?.trim()?.lowercase()
            if (!value.isNullOrEmpty()) {
                total++
                if (value == "n" || value == "0" || value == "false") disabled++
            }
        }

        val (running, stopped) = readDaemonStates()
        val hasBackup = backupStore.hasBackup(BACKUP_KEY)
        val stoppedByUs = hasBackup && stopped > 0 && running == 0
        val mode = when {
            total > 0 && disabled == total -> if (running > 0) ThermalEngineMode.PARTIAL else ThermalEngineMode.DISABLED
            total > 0 && disabled > 0 -> ThermalEngineMode.PARTIAL
            stoppedByUs -> if (total > 0) ThermalEngineMode.PARTIAL else ThermalEngineMode.DISABLED
            else -> ThermalEngineMode.ACTIVE
        }

        // Everything is back to normal (typically after a reboot), so a
        // leftover backup is stale and must not be mistaken for a live one.
        if (hasBackup && mode == ThermalEngineMode.ACTIVE) backupStore.clear(BACKUP_KEY)

        ThermalDisableState(
            supported = true,
            active = mode != ThermalEngineMode.ACTIVE,
            reason = when (mode) {
                ThermalEngineMode.ACTIVE -> "Thermal engine active"
                ThermalEngineMode.DISABLED -> "Thermal engine disabled"
                ThermalEngineMode.PARTIAL -> "Thermal engine partly disabled"
            },
            mode = mode,
            controlsTotal = total,
            controlsDisabled = disabled,
            daemonsRunning = running,
            daemonsStopped = stopped
        )
    }

    suspend fun disable(): AppResult<Unit> = withContext(Dispatchers.IO) {
        val state = disableState()
        if (!state.supported) return@withContext AppResult.Failure(state.reason)

        val (tripPaths, paramPaths) = controlPaths()
        val allPaths = tripPaths + paramPaths

        // Keep the very first backup: disabling again from a partly disabled
        // state must not overwrite the original values with the relaxed ones.
        if (!backupStore.hasBackup(BACKUP_KEY)) {
            backupStore.save(BACKUP_KEY, allPaths.associateWith { SysfsAccess.readDirect(it) })
        }
        val backup = backupStore.load(BACKUP_KEY) ?: emptyMap()

        tripPaths.forEach { path ->
            val relaxed = relaxedTripValue(backup[path]?.toFloatOrNull())
            val result = rootExecutor.writeFile(path, relaxed)
            logRepository.append(
                LogEntry(System.currentTimeMillis(), "RELAX_THERMAL_TRIP", path, backup[path], relaxed,
                    if (result.isSuccess) "SUCCESS" else "FAILED", if (result.isSuccess) null else result.stderr)
            )
        }
        paramPaths.forEach { path ->
            val value = if (path.endsWith("enabled") && path.contains("msm_thermal/parameters")) "N" else "0"
            val result = rootExecutor.writeFile(path, value)
            logRepository.append(
                LogEntry(System.currentTimeMillis(), "DISABLE_THERMAL_PARAM", path, backup[path], value,
                    if (result.isSuccess) "SUCCESS" else "FAILED", if (result.isSuccess) null else result.stderr)
            )
        }
        vendorServices.forEach { service ->
            val result = rootExecutor.execute("stop $service")
            logRepository.append(
                LogEntry(System.currentTimeMillis(), "STOP_THERMAL_SERVICE", service, "running", "stopped",
                    if (result.isSuccess) "SUCCESS" else "SKIPPED", if (result.isSuccess) null else result.stderr)
            )
        }

        // Verify: read the system back instead of trusting the write results.
        val after = disableState()
        if (after.mode == ThermalEngineMode.ACTIVE) {
            backupStore.clear(BACKUP_KEY)
            AppResult.Failure("Tidak ada perubahan yang diterapkan. Kernel atau ROM ini menolak penulisan.")
        } else {
            AppResult.Success(Unit)
        }
    }

    suspend fun restore(): AppResult<Unit> = withContext(Dispatchers.IO) {
        backupStore.load(BACKUP_KEY)?.forEach { (path, previousValue) ->
            if (previousValue != null) {
                val result = rootExecutor.writeFile(path, previousValue)
                logRepository.append(
                    LogEntry(System.currentTimeMillis(), "RESTORE_THERMAL", path, "relaxed", previousValue,
                        if (result.isSuccess) "SUCCESS" else "FAILED", if (result.isSuccess) null else result.stderr)
                )
            }
        }

        // Also switch back on any module parameter that still reads
        // disabled -- this covers a lost backup and states set up outside
        // this app. A trip threshold has no such fixed word to detect on
        // its own (only the backup above knows its original value), which
        // is the trade-off of relaxing thresholds instead of a flag.
        val rootAvailable = rootRepository.current().isAvailable
        val (_, paramPaths) = controlPaths()
        val values = SysfsAccess.readMultipleWithRoot(paramPaths, rootAvailable, rootExecutor)
        paramPaths.filter { values[it]?.trim()?.lowercase() in listOf("n", "0", "false") }.forEach { path ->
            val value = if (path.contains("msm_thermal/parameters")) "Y" else "1"
            val result = rootExecutor.writeFile(path, value)
            logRepository.append(
                LogEntry(System.currentTimeMillis(), "RESTORE_THERMAL_PARAM", path, "disabled", value,
                    if (result.isSuccess) "SUCCESS" else "FAILED", if (result.isSuccess) null else result.stderr)
            )
        }
        vendorServices.forEach { service ->
            rootExecutor.execute("start $service")
        }
        backupStore.clear(BACKUP_KEY)

        val after = disableState()
        if (after.mode == ThermalEngineMode.ACTIVE) AppResult.Success(Unit)
        else AppResult.Failure("Sebagian pengaturan belum pulih. Restart perangkat akan memulihkannya sepenuhnya.")
    }
}
