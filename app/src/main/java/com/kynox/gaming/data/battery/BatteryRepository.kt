package com.kynox.gaming.data.battery

import android.content.Context
import com.kynox.gaming.core.root.RootExecutor
import com.kynox.gaming.core.sysfs.CapabilityEngine
import com.kynox.gaming.core.sysfs.SysfsAccess
import com.kynox.gaming.core.utils.AppResult
import com.kynox.gaming.data.backup.BackupStore
import com.kynox.gaming.data.logs.LogRepository
import com.kynox.gaming.data.root.RootRepository
import com.kynox.gaming.domain.model.BatteryInfo
import com.kynox.gaming.domain.model.ChargeLimitState
import com.kynox.gaming.domain.model.ChargerInfo
import com.kynox.gaming.domain.model.FastChargingState
import com.kynox.gaming.domain.model.LogEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val BACKUP_KEY = "fast_charging"
private const val CHARGE_LIMIT_BACKUP_KEY = "charge_limit_pause"
private const val SCRIPT_NAME = "kynox_fast_charge_loop.sh"
private const val DAEMON_MATCH = "kynox_fast_charge_loop.sh"

const val FAST_CHARGE_DEFAULT_MA = 5050
const val FAST_CHARGE_MIN_MA = 2000
const val FAST_CHARGE_MAX_MA = 6000

/**
 * Battery/charging monitoring (PRD section 9) plus the "fast charging"
 * control (PRD section 20), rebuilt from the reference root scripts. Unlike
 * a one-shot write, this launches a detached root daemon (nohup ... &)
 * running the same style of "echo value > node; sleep 1" loop as the
 * reference Magisk modules, so it keeps re-asserting the values the same
 * way -- and survives even if Kynox itself is closed.
 */
class BatteryRepository(
    private val context: Context,
    private val capabilityEngine: CapabilityEngine,
    private val rootRepository: RootRepository,
    private val rootExecutor: RootExecutor,
    private val backupStore: BackupStore,
    private val logRepository: LogRepository
) {
    private val infoReader = BatteryInfoReader(context)
    private val scriptFile: File get() = File(context.filesDir, SCRIPT_NAME)

    // Current-limit node names probed under every /sys/class/power_supply/*
    // entry, mirroring the reference modules.
    private val currentNodeNames = listOf(
        "current_max", "hw_current_max", "pd_current_max", "ctm_current_max",
        "sdp_current_max", "constant_charge_current_max", "constant_charge_current",
        "input_current_max"
    )

    // Fixed absolute paths from the reference scripts that aren't discovered
    // per-supply-name (either a unique node, or a known typo'd path kept as-is).
    private val extraCurrentPaths = listOf(
        "/sys/class/power_supply/pc_port/current_max",
        "/sys/class/power_supply/constant_charge_current__max"
    )

    // Simple on/off flags the reference scripts also set to unlock the limit.
    private val toggleNodes = listOf(
        "/sys/kernel/fast_charge/force_fast_charge" to "1",
        "/sys/kernel/fast_charge/failsafe" to "1",
        "/sys/class/power_supply/battery/allow_hvdcp3" to "1",
        "/sys/class/power_supply/usb/pd_allowed" to "1",
        "/sys/class/power_supply/battery/subsystem/usb/pd_allowed" to "1",
        "/sys/class/power_supply/battery/input_current_limited" to "0",
        "/sys/class/power_supply/battery/input_current_settled" to "1",
        "/sys/class/qcom-battery/restricted_charging" to "0",
        "/sys/class/qcom-battery/restrict_chg" to "0"
    )

    // Candidate nodes for pausing/resuming charging while still plugged in,
    // used by the charge-limit feature (PRD "Charge limit"/"Battery
    // protection"). Node names and their on/off polarity both vary by
    // vendor, so each entry carries its own pause/resume value instead of
    // assuming "1 means enabled" everywhere.
    private val chargePauseNodes = listOf(
        Triple("charging_enabled", "0", "1"),
        Triple("battery_charging_enabled", "0", "1"),
        Triple("charge_enabled", "0", "1"),
        Triple("input_suspend", "1", "0"),
        Triple("stop_charging", "1", "0")
    )

    private val chmodTargets = listOf(
        "/sys/class/power_supply/*/*",
        "/sys/module/qpnp_smbcharger/*/*",
        "/sys/module/dwc3_msm/*/*",
        "/sys/module/phy_msm_usb/*/*"
    )

    // "Infinity ROM Boost (Advanced)": additionally forces the charging
    // thermal mitigation thresholds open. Kept as a separate, extra-warned
    // toggle -- this is the riskiest part of the reference scripts.
    private val advancedNodes = listOf(
        "/sys/class/power_supply/battery/system_temp_level" to "1",
        "/sys/class/power_supply/bms/temp_cool" to "300",
        "/sys/class/power_supply/bms/temp_hot" to "470",
        "/sys/class/power_supply/bms/temp_warm" to "470"
    )

    suspend fun readInfo(): BatteryInfo = withContext(Dispatchers.IO) { infoReader.read() }

    // ---- Charger diagnostics (PRD section 9/20) -------------------------

    private val quickChargeTypeNames = mapOf(
        "0" to "None", "1" to "USB", "2" to "HVDCP", "3" to "HVDCP3", "4" to "HVDCP3P5", "5" to "USB PD"
    )

    /** Reads charger-side diagnostics (temp, negotiated voltage/current, protocol) discovered from power_supply nodes, without assuming vendor-specific paths. */
    suspend fun readChargerInfo(): ChargerInfo = withContext(Dispatchers.IO) {
        val rootAvailable = rootRepository.current().isAvailable
        val supplies = capabilityEngine.powerSupplyNames()
        val batterySupply = supplies.firstOrNull { it.equals("battery", ignoreCase = true) }
            ?: supplies.firstOrNull { it.contains("batt", ignoreCase = true) }
        val usbSupply = supplies.firstOrNull { it.equals("usb", ignoreCase = true) }
            ?: supplies.firstOrNull { it.contains("usb", ignoreCase = true) }

        val paths = mutableListOf<String>()
        batterySupply?.let { b ->
            paths += "/sys/class/power_supply/$b/charger_temp"
            paths += "/sys/class/power_supply/$b/charger_temp_max"
        }
        usbSupply?.let { u ->
            paths += "/sys/class/power_supply/$u/real_type"
            paths += "/sys/class/power_supply/$u/type"
            paths += "/sys/class/power_supply/$u/quick_charge_type"
            paths += "/sys/class/power_supply/$u/pd_active"
            paths += "/sys/class/power_supply/$u/voltage_now"
            paths += "/sys/class/power_supply/$u/current_max"
            paths += "/sys/class/power_supply/$u/typec_mode"
            paths += "/sys/class/power_supply/$u/online"
        }

        if (paths.isEmpty()) {
            return@withContext ChargerInfo(false, false, "Unknown", "Unknown", null, null, null, null)
        }

        val values = SysfsAccess.readMultipleWithRoot(paths, rootAvailable, rootExecutor)
        fun v(name: String, supply: String?) = supply?.let { values["/sys/class/power_supply/$it/$name"] }

        val online = v("online", usbSupply) == "1"
        val chargerTemp = v("charger_temp", batterySupply)?.toFloatOrNull()?.div(10f)
        val chargerTempMax = v("charger_temp_max", batterySupply)?.toFloatOrNull()?.div(10f)
        val voltageMv = v("voltage_now", usbSupply)?.toLongOrNull()?.div(1000)?.toInt()
        val currentMa = v("current_max", usbSupply)?.toLongOrNull()?.div(1000)?.toInt()
        val realType = v("real_type", usbSupply)?.takeIf { it.isNotBlank() }
        val type = v("type", usbSupply)?.takeIf { it.isNotBlank() }
        val quickChargeType = v("quick_charge_type", usbSupply)
        val pdActive = v("pd_active", usbSupply) == "1"
        val typeCMode = v("typec_mode", usbSupply)

        val protocol = when {
            pdActive -> "USB PD"
            quickChargeType != null -> quickChargeTypeNames[quickChargeType] ?: "QC ($quickChargeType)"
            else -> realType ?: type ?: "Unknown"
        }

        ChargerInfo(
            supported = batterySupply != null || usbSupply != null,
            online = online,
            chargerType = realType ?: type ?: "Unknown",
            fastChargeProtocol = protocol,
            chargerTemperatureCelsius = chargerTemp ?: chargerTempMax,
            negotiatedVoltageMilliVolts = voltageMv,
            negotiatedCurrentMilliAmps = currentMa,
            typeCMode = typeCMode
        )
    }

    // ---- Charge limit / battery protection -----------------------------

    private suspend fun chargePausePaths(): List<Triple<String, String, String>> {
        val supplies = capabilityEngine.powerSupplyNames()
        return supplies.flatMap { supply ->
            chargePauseNodes.map { (node, pause, resume) -> Triple("/sys/class/power_supply/$supply/$node", pause, resume) }
        }
    }

    /** Whether a writable node exists on this kernel to pause charging while still plugged in. */
    suspend fun chargeLimitState(): ChargeLimitState = withContext(Dispatchers.IO) {
        val rootAvailable = rootRepository.current().isAvailable
        if (!rootAvailable) {
            return@withContext ChargeLimitState(false, null, "Requires root access")
        }
        val candidates = chargePausePaths()
        val values = SysfsAccess.readMultipleWithRoot(candidates.map { it.first }, rootAvailable, rootExecutor)
        val found = candidates.firstOrNull { values[it.first] != null }
        if (found == null) {
            ChargeLimitState(false, null, "No charge-pause interface found on this kernel")
        } else {
            ChargeLimitState(true, found.first, "Ready")
        }
    }

    /** Writes the pause value to the first working node, backing up its original value on first use. */
    suspend fun pauseCharging(): AppResult<Unit> = withContext(Dispatchers.IO) {
        val candidates = chargePausePaths()
        val values = SysfsAccess.readMultipleWithRoot(candidates.map { it.first }, true, rootExecutor)
        val (path, pauseValue, _) = candidates.firstOrNull { values[it.first] != null }
            ?: return@withContext AppResult.Failure("No charge-pause interface found on this kernel")

        if (!backupStore.hasBackup(CHARGE_LIMIT_BACKUP_KEY)) {
            backupStore.save(CHARGE_LIMIT_BACKUP_KEY, mapOf(path to values[path]))
        }
        val result = rootExecutor.writeFile(path, pauseValue)
        logRepository.append(
            LogEntry(System.currentTimeMillis(), "PAUSE_CHARGING", path, values[path], pauseValue,
                if (result.isSuccess) "SUCCESS" else "FAILED", if (result.isSuccess) null else result.stderr)
        )
        if (result.isSuccess) AppResult.Success(Unit) else AppResult.Failure(result.stderr.ifBlank { "Write failed" })
    }

    /** Writes the resume value back. Safe to call even if charging was never paused. */
    suspend fun resumeCharging(): AppResult<Unit> = withContext(Dispatchers.IO) {
        val candidates = chargePausePaths()
        val values = SysfsAccess.readMultipleWithRoot(candidates.map { it.first }, true, rootExecutor)
        val (path, _, resumeValue) = candidates.firstOrNull { values[it.first] != null }
            ?: return@withContext AppResult.Success(Unit)

        val result = rootExecutor.writeFile(path, resumeValue)
        logRepository.append(
            LogEntry(System.currentTimeMillis(), "RESUME_CHARGING", path, values[path], resumeValue,
                if (result.isSuccess) "SUCCESS" else "FAILED", if (result.isSuccess) null else result.stderr)
        )
        backupStore.clear(CHARGE_LIMIT_BACKUP_KEY)
        if (result.isSuccess) AppResult.Success(Unit) else AppResult.Failure(result.stderr.ifBlank { "Write failed" })
    }

    /** All current-limit + toggle candidate paths, built once (cheap, no I/O). */
    private suspend fun allCandidatePaths(): List<String> {
        val supplies = capabilityEngine.powerSupplyNames()
        val currentCandidates = supplies.flatMap { supply ->
            currentNodeNames.map { node -> "/sys/class/power_supply/$supply/$node" }
        } + extraCurrentPaths
        return currentCandidates + toggleNodes.map { it.first }
    }

    private suspend fun isDaemonRunning(): Boolean {
        if (!rootRepository.current().isAvailable) return false
        val result = rootExecutor.execute("pgrep -f $DAEMON_MATCH")
        return result.isSuccess && result.stdout.isNotBlank()
    }

    suspend fun fastChargingState(): FastChargingState = withContext(Dispatchers.IO) {
        val rootAvailable = rootRepository.current().isAvailable
        if (!rootAvailable) {
            return@withContext FastChargingState(false, false, false, FAST_CHARGE_DEFAULT_MA, null, null, 0, "Requires root access")
        }

        // One batched root read covers every candidate path this screen
        // needs, instead of a separate `su` spawn per path (which is what
        // made this screen -- polled every couple of seconds -- noticeably
        // heavy).
        val supplies = capabilityEngine.powerSupplyNames()
        val currentCandidates = supplies.flatMap { supply ->
            currentNodeNames.map { node -> "/sys/class/power_supply/$supply/$node" }
        } + extraCurrentPaths
        val allPaths = currentCandidates + toggleNodes.map { it.first }
        val values = SysfsAccess.readMultipleWithRoot(allPaths, rootAvailable, rootExecutor)

        val nodes = currentCandidates.filter { values[it] != null }
        val toggleFound = toggleNodes.any { values[it.first] != null }
        if (nodes.isEmpty() && !toggleFound) {
            return@withContext FastChargingState(false, false, false, FAST_CHARGE_DEFAULT_MA, null, null, 0, "No writable charge-current interface found on this kernel")
        }

        val running = isDaemonRunning()
        val backup = backupStore.load(BACKUP_KEY)
        val savedTarget = backup?.get("_target_ma")?.toIntOrNull() ?: FAST_CHARGE_DEFAULT_MA
        val advancedActive = running && backup?.get("_advanced") == "1"

        val activeNodePath = toggleNodes.map { it.first }.firstOrNull { values[it] != null } ?: nodes.firstOrNull()
        val activeNodeValue = activeNodePath?.let { values[it] }

        FastChargingState(
            supported = true,
            active = running,
            advancedActive = advancedActive,
            targetCurrentMa = savedTarget,
            activeNodePath = activeNodePath,
            activeNodeValue = activeNodeValue,
            writableNodeCount = nodes.size,
            reason = if (running) "Fast charging daemon running" else "Using stock charge current limits"
        )
    }

    private fun buildScript(targetMa: Int, includeAdvanced: Boolean, supplies: List<String>): String {
        val targetUa = (targetMa * 1000)
        val sb = StringBuilder()
        sb.appendLine("#!/system/bin/sh")
        chmodTargets.forEach { sb.appendLine("chmod 777 $it 2>/dev/null") }
        sb.appendLine("while true; do")
        toggleNodes.forEach { (path, value) -> sb.appendLine("echo '$value' > '$path' 2>/dev/null") }
        supplies.forEach { supply ->
            currentNodeNames.forEach { node ->
                sb.appendLine("echo '$targetUa' > '/sys/class/power_supply/$supply/$node' 2>/dev/null")
            }
        }
        extraCurrentPaths.forEach { sb.appendLine("echo '$targetUa' > '$it' 2>/dev/null") }
        sb.appendLine("echo '10010000' > /sys/class/qcom-battery/restricted_current 2>/dev/null")
        if (includeAdvanced) {
            advancedNodes.forEach { (path, value) -> sb.appendLine("echo '$value' > '$path' 2>/dev/null") }
        }
        sb.appendLine("sleep 1")
        sb.appendLine("done")
        return sb.toString()
    }

    suspend fun applyFastCharging(targetMa: Int, includeAdvanced: Boolean): AppResult<Unit> = withContext(Dispatchers.IO) {
        val clamped = targetMa.coerceIn(FAST_CHARGE_MIN_MA, FAST_CHARGE_MAX_MA)
        val state = fastChargingState()
        if (!state.supported) return@withContext AppResult.Failure(state.reason)

        // Snapshot the values we're about to start overwriting, once, before
        // the very first start -- re-toggling/adjusting later won't stomp it.
        if (!backupStore.hasBackup(BACKUP_KEY)) {
            val supplies = capabilityEngine.powerSupplyNames()
            val currentCandidates = supplies.flatMap { supply ->
                currentNodeNames.map { node -> "/sys/class/power_supply/$supply/$node" }
            } + extraCurrentPaths
            val allPaths = currentCandidates + toggleNodes.map { it.first } + advancedNodes.map { it.first }
            val snapshot = SysfsAccess.readMultipleWithRoot(allPaths, true, rootExecutor)
            backupStore.save(BACKUP_KEY, snapshot)
        }

        // Stop any previous instance before relaunching with new parameters.
        stopDaemon()

        val supplies = capabilityEngine.powerSupplyNames()
        val script = buildScript(clamped, includeAdvanced, supplies)
        try {
            scriptFile.writeText(script)
        } catch (t: Throwable) {
            return@withContext AppResult.Failure("Could not write script: ${t.message}")
        }

        val launch = rootExecutor.execute("nohup sh '${scriptFile.absolutePath}' > /dev/null 2>&1 &", timeoutMs = 4000L)
        logRepository.append(
            LogEntry(System.currentTimeMillis(), "START_FAST_CHARGE_DAEMON", scriptFile.absolutePath, null,
                "target=${clamped}mA advanced=$includeAdvanced",
                if (launch.isSuccess) "SUCCESS" else "FAILED", if (launch.isSuccess) null else launch.stderr)
        )
        if (!launch.isSuccess) return@withContext AppResult.Failure(launch.stderr.ifBlank { "Failed to launch daemon" })

        val marker = backupStore.load(BACKUP_KEY)?.toMutableMap() ?: mutableMapOf()
        marker["_target_ma"] = clamped.toString()
        marker["_advanced"] = if (includeAdvanced) "1" else "0"
        backupStore.save(BACKUP_KEY, marker)
        AppResult.Success(Unit)
    }

    private suspend fun stopDaemon(): AppResult<Unit> = withContext(Dispatchers.IO) {
        rootExecutor.execute("pkill -f $DAEMON_MATCH")
        logRepository.append(
            LogEntry(System.currentTimeMillis(), "STOP_FAST_CHARGE_DAEMON", DAEMON_MATCH, "running", "stopped", "SUCCESS", null)
        )
        AppResult.Success(Unit)
    }

    suspend fun restoreFastCharging(): AppResult<Unit> = withContext(Dispatchers.IO) {
        stopDaemon()
        val backup = backupStore.load(BACKUP_KEY)
            ?: return@withContext AppResult.Failure("No backup found to restore")
        var failures = 0
        backup.forEach { (path, previousValue) ->
            if (!path.startsWith("_") && previousValue != null) {
                val result = rootExecutor.writeFile(path, previousValue)
                if (!result.isSuccess) failures++
                logRepository.append(
                    LogEntry(System.currentTimeMillis(), "RESTORE_CHARGE_CURRENT", path, "modified", previousValue,
                        if (result.isSuccess) "SUCCESS" else "FAILED", if (result.isSuccess) null else result.stderr)
                )
            }
        }
        try { scriptFile.delete() } catch (t: Throwable) { /* best effort */ }
        backupStore.clear(BACKUP_KEY)
        if (failures > 0) AppResult.Failure("$failures value(s) could not be restored, a reboot will fully restore them")
        else AppResult.Success(Unit)
    }
}
