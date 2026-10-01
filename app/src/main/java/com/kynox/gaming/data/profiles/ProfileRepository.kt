package com.kynox.gaming.data.profiles

import com.kynox.gaming.core.utils.gpuFreqMhz
import com.kynox.gaming.core.utils.AppResult
import com.kynox.gaming.data.backup.BackupStore
import com.kynox.gaming.data.cpu.CpuRepository
import com.kynox.gaming.data.gpu.GpuRepository
import com.kynox.gaming.data.logs.LogRepository
import com.kynox.gaming.data.thermal.ThermalRepository
import com.kynox.gaming.domain.model.CpuSnapshot
import com.kynox.gaming.domain.model.GpuState
import com.kynox.gaming.domain.model.LogEntry
import com.kynox.gaming.domain.model.ProfileApplySummary
import com.kynox.gaming.domain.model.ProfileConfig
import com.kynox.gaming.domain.model.ProfileParamResult
import com.kynox.gaming.domain.model.ProfileType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val ACTIVE_KEY = "profile_active"
private const val CUSTOM_KEY = "profile_custom"
private const val ORIGINAL_KEY = "profile_original_state"

/**
 * Performance Profiles (PRD section 10). Touches CPU/GPU governor and max
 * frequency, plus -- only for the Custom profile, never a preset, and never
 * the boot receiver -- the thermal governor (`policy`), which is a choice of
 * kernel-provided throttling strategies rather than a threshold override.
 * Fast Charging and fully disabling the thermal engine stay separate,
 * manual, confirmation-gated actions on their own screens.
 */
class ProfileRepository(
    private val cpuRepository: CpuRepository,
    private val gpuRepository: GpuRepository,
    private val thermalRepository: ThermalRepository,
    private val backupStore: BackupStore,
    private val logRepository: LogRepository
) {

    suspend fun activeProfile(): ProfileType? {
        val map = backupStore.load(ACTIVE_KEY) ?: return null
        return map["type"]?.let { runCatching { ProfileType.valueOf(it) }.getOrNull() }
    }

    suspend fun customProfile(): ProfileConfig? {
        val map = backupStore.load(CUSTOM_KEY) ?: return null
        return ProfileConfig(
            cpuGovernor = map["cpuGovernor"],
            cpuMaxFreqKhz = map["cpuMaxFreqKhz"]?.toIntOrNull(),
            gpuGovernor = map["gpuGovernor"],
            gpuMaxFreqKhz = map["gpuMaxFreqKhz"]?.toIntOrNull(),
            thermalPolicy = map["thermalPolicy"]
        )
    }

    suspend fun hasOriginalBackup(): Boolean = backupStore.hasBackup(ORIGINAL_KEY)

    suspend fun saveCurrentAsCustom(): AppResult<Unit> = withContext(Dispatchers.IO) {
        val cpuSnapshot = cpuRepository.readSnapshot()
        val gpu = gpuRepository.readState()
        val thermalPolicy = thermalRepository.policySummary().currentPolicy
        val representative = cpuSnapshot.cores.firstOrNull()
        backupStore.save(
            CUSTOM_KEY,
            mapOf(
                "cpuGovernor" to representative?.governor,
                "cpuMaxFreqKhz" to representative?.maxFreqKhz?.toString(),
                "gpuGovernor" to gpu.governor.takeIf { gpu.supported },
                "gpuMaxFreqKhz" to gpu.currentFreqKhz?.takeIf { gpu.supported }?.toString(),
                "thermalPolicy" to thermalPolicy
            )
        )
        AppResult.Success(Unit)
    }

    suspend fun apply(type: ProfileType): AppResult<ProfileApplySummary> = withContext(Dispatchers.IO) {
        val cpuSnapshot = cpuRepository.readSnapshot()
        val gpu = gpuRepository.readState()

        if (type == ProfileType.CUSTOM) {
            val custom = customProfile()
                ?: return@withContext AppResult.Failure("No custom profile saved yet. Set CPU/GPU manually first, then use \"Save current as Custom\".")
            applyCustom(custom, cpuSnapshot, gpu)
        } else {
            applyPreset(type, cpuSnapshot, gpu)
        }
    }

    suspend fun reset(): AppResult<List<ProfileParamResult>> = withContext(Dispatchers.IO) {
        val backup = backupStore.load(ORIGINAL_KEY)
            ?: return@withContext AppResult.Failure("Nothing to reset -- no profile has been applied yet.")

        val results = mutableListOf<ProfileParamResult>()
        val cpuSnapshot = cpuRepository.readSnapshot()
        cpuSnapshot.cores.forEach { core ->
            val governor = backup["cpu${core.core}_governor"]
            val maxFreq = backup["cpu${core.core}_maxFreq"]?.toIntOrNull()
            if (governor != null && core.canWriteGovernor) {
                val r = cpuRepository.setGovernor(core.core, governor)
                results += ProfileParamResult("CPU${core.core} governor", r is AppResult.Success, describe(r, "restored to $governor"))
            }
            if (maxFreq != null && core.canWriteFreq) {
                val r = cpuRepository.setMinMaxFreq(core.core, null, maxFreq)
                results += ProfileParamResult("CPU${core.core} max freq", r is AppResult.Success, describe(r, "restored to ${maxFreq / 1000} MHz"))
            }
        }

        val gpu = gpuRepository.readState()
        backup["gpu_governor"]?.let { governor ->
            if (gpu.supported && gpu.canWriteGovernor) {
                val r = gpuRepository.setGovernor(governor)
                results += ProfileParamResult("GPU governor", r is AppResult.Success, describe(r, "restored to $governor"))
            }
        }
        backup["gpu_maxFreq"]?.toIntOrNull()?.let { maxFreq ->
            if (gpu.supported && gpu.canWriteFreq) {
                val r = gpuRepository.setMaxFreq(maxFreq)
                results += ProfileParamResult("GPU max freq", r is AppResult.Success, describe(r, "restored to ${gpuFreqMhz(maxFreq)} MHz"))
            }
        }

        backup["thermal_policy"]?.let { policy ->
            val r = thermalRepository.setPolicyForAllZones(policy)
            results += ProfileParamResult("Thermal policy", r is AppResult.Success, describe(r, "restored to $policy"))
        }

        backupStore.clear(ORIGINAL_KEY)
        backupStore.clear(ACTIVE_KEY)
        logRepository.append(LogEntry(System.currentTimeMillis(), "RESET_PROFILE", "cpu+gpu", "profile active", "original state", "SUCCESS", null))
        AppResult.Success(results)
    }

    private suspend fun backupOriginalIfNeeded(cpuSnapshot: CpuSnapshot, gpu: GpuState) {
        if (backupStore.hasBackup(ORIGINAL_KEY)) return
        val map = mutableMapOf<String, String?>()
        cpuSnapshot.cores.forEach { core ->
            map["cpu${core.core}_governor"] = core.governor
            map["cpu${core.core}_maxFreq"] = core.maxFreqKhz?.toString()
        }
        if (gpu.supported) {
            map["gpu_governor"] = gpu.governor
            map["gpu_maxFreq"] = gpu.maxFreqKhz?.toString()
        }
        map["thermal_policy"] = thermalRepository.policySummary().currentPolicy
        backupStore.save(ORIGINAL_KEY, map)
    }

    private suspend fun applyPreset(type: ProfileType, cpuSnapshot: CpuSnapshot, gpu: GpuState): AppResult<ProfileApplySummary> {
        backupOriginalIfNeeded(cpuSnapshot, gpu)

        val governorPreference = when (type) {
            ProfileType.BALANCED -> listOf("schedutil", "interactive", "ondemand", "conservative")
            ProfileType.PERFORMANCE, ProfileType.GAMING -> listOf("performance")
            ProfileType.POWERSAVE -> listOf("powersave", "conservative")
            ProfileType.CUSTOM -> emptyList()
        }
        val capFreqToMax = type == ProfileType.PERFORMANCE || type == ProfileType.GAMING

        val txn = ApplyTransaction()
        val representativeGovernors = cpuSnapshot.cores.firstOrNull()?.availableGovernors ?: emptyList()
        val resolvedCpuGovernor = governorPreference.firstNotNullOfOrNull { pref ->
            representativeGovernors.firstOrNull { it.equals(pref, ignoreCase = true) }
        }

        cpuSnapshot.cores.forEach { core ->
            when {
                resolvedCpuGovernor != null && core.canWriteGovernor -> {
                    txn.attempt(
                        label = "CPU${core.core} governor",
                        write = { cpuRepository.setGovernor(core.core, resolvedCpuGovernor) },
                        undo = { core.governor?.let { cpuRepository.setGovernor(core.core, it) } ?: AppResult.Success(Unit) },
                        successDetail = "set to $resolvedCpuGovernor"
                    )
                }
                resolvedCpuGovernor == null -> txn.skip("CPU${core.core} governor", "no matching governor available on this kernel")
                else -> txn.skip("CPU${core.core} governor", "read-only on this kernel without root")
            }

            if (capFreqToMax && core.canWriteFreq) {
                val target = core.availableFreqsKhz.maxOrNull() ?: core.maxFreqKhz
                if (target != null && core.maxFreqKhz == target) {
                    txn.skip("CPU${core.core} max freq", "already at ${target / 1000} MHz", applied = true)
                } else if (target != null) {
                    txn.attempt(
                        label = "CPU${core.core} max freq",
                        write = { cpuRepository.setMinMaxFreq(core.core, null, target) },
                        undo = { cpuRepository.setMinMaxFreq(core.core, null, core.maxFreqKhz ?: target) },
                        successDetail = "capped to ${target / 1000} MHz"
                    )
                }
            }
        }

        if (gpu.supported) {
            val resolvedGpuGovernor = resolvedCpuGovernor?.let { name ->
                gpu.availableGovernors.firstOrNull { it.equals(name, ignoreCase = true) }
            }
            if (resolvedGpuGovernor != null && gpu.canWriteGovernor) {
                txn.attempt(
                    label = "GPU governor",
                    write = { gpuRepository.setGovernor(resolvedGpuGovernor) },
                    undo = { gpu.governor?.let { gpuRepository.setGovernor(it) } ?: AppResult.Success(Unit) },
                    successDetail = "set to $resolvedGpuGovernor"
                )
            }
            if (capFreqToMax && gpu.canWriteFreq) {
                val target = gpu.availableFreqsKhz.maxOrNull() ?: gpu.maxFreqKhz
                if (target != null && gpu.maxFreqKhz == target) {
                    txn.skip("GPU max freq", "already at ${gpuFreqMhz(target)} MHz", applied = true)
                } else if (target != null) {
                    txn.attempt(
                        label = "GPU max freq",
                        write = { gpuRepository.setMaxFreq(target) },
                        undo = { gpu.maxFreqKhz?.let { gpuRepository.setMaxFreq(it) } ?: AppResult.Success(Unit) },
                        successDetail = "capped to ${gpuFreqMhz(target)} MHz"
                    )
                }
            }
        }

        return txn.finish(type)
    }

    private suspend fun applyCustom(config: ProfileConfig, cpuSnapshot: CpuSnapshot, gpu: GpuState): AppResult<ProfileApplySummary> {
        backupOriginalIfNeeded(cpuSnapshot, gpu)
        val txn = ApplyTransaction()

        cpuSnapshot.cores.forEach { core ->
            if (config.cpuGovernor != null && core.canWriteGovernor && core.availableGovernors.any { it.equals(config.cpuGovernor, true) }) {
                txn.attempt(
                    label = "CPU${core.core} governor",
                    write = { cpuRepository.setGovernor(core.core, config.cpuGovernor) },
                    undo = { core.governor?.let { cpuRepository.setGovernor(core.core, it) } ?: AppResult.Success(Unit) },
                    successDetail = "set to ${config.cpuGovernor}"
                )
            }
            if (config.cpuMaxFreqKhz != null && core.canWriteFreq && core.availableFreqsKhz.contains(config.cpuMaxFreqKhz)) {
                txn.attempt(
                    label = "CPU${core.core} max freq",
                    write = { cpuRepository.setMinMaxFreq(core.core, null, config.cpuMaxFreqKhz) },
                    undo = { cpuRepository.setMinMaxFreq(core.core, null, core.maxFreqKhz ?: config.cpuMaxFreqKhz) },
                    successDetail = "set to ${config.cpuMaxFreqKhz / 1000} MHz"
                )
            }
        }

        if (gpu.supported) {
            if (config.gpuGovernor != null && gpu.canWriteGovernor && gpu.availableGovernors.any { it.equals(config.gpuGovernor, true) }) {
                txn.attempt(
                    label = "GPU governor",
                    write = { gpuRepository.setGovernor(config.gpuGovernor) },
                    undo = { gpu.governor?.let { gpuRepository.setGovernor(it) } ?: AppResult.Success(Unit) },
                    successDetail = "set to ${config.gpuGovernor}"
                )
            }
            if (config.gpuMaxFreqKhz != null && gpu.canWriteFreq) {
                txn.attempt(
                    label = "GPU max freq",
                    write = { gpuRepository.setMaxFreq(config.gpuMaxFreqKhz) },
                    undo = { gpu.maxFreqKhz?.let { gpuRepository.setMaxFreq(it) } ?: AppResult.Success(Unit) },
                    successDetail = "set to ${gpuFreqMhz(config.gpuMaxFreqKhz)} MHz"
                )
            }
        }

        if (config.thermalPolicy != null) {
            val previousPolicy = thermalRepository.policySummary().currentPolicy
            txn.attempt(
                label = "Thermal policy",
                write = { thermalRepository.setPolicyForAllZones(config.thermalPolicy) },
                undo = { previousPolicy?.let { thermalRepository.setPolicyForAllZones(it) } ?: AppResult.Success(Unit) },
                successDetail = "set to ${config.thermalPolicy}"
            )
        }

        return txn.finish(ProfileType.CUSTOM)
    }

    private fun describe(result: AppResult<Unit>, successDetail: String): String =
        if (result is AppResult.Success) successDetail else (result as AppResult.Failure).message.ifBlank { "write failed" }

    /**
     * VALIDATE -> APPLY -> VERIFY -> SUCCESS, or ROLLBACK on any genuine
     * write failure (never for a parameter that was skipped because the
     * capability doesn't exist -- there is nothing to undo for those).
     * Every successful write records its own undo action as it happens; if
     * anything later in the same apply fails, every undo so far runs in
     * reverse order before this returns, so the device is never left with
     * only half of a profile applied.
     */
    private inner class ApplyTransaction {
        private val results = mutableListOf<ProfileParamResult>()
        private val undoStack = mutableListOf<Pair<String, suspend () -> AppResult<Unit>>>()
        private var firstFailureDetail: String? = null

        suspend fun attempt(
            label: String,
            write: suspend () -> AppResult<Unit>,
            undo: suspend () -> AppResult<Unit>,
            successDetail: String
        ) {
            val r = write()
            if (r is AppResult.Success) {
                undoStack += label to undo
                results += ProfileParamResult(label, true, successDetail)
            } else {
                val message = (r as AppResult.Failure).message.ifBlank { "write failed" }
                results += ProfileParamResult(label, false, message)
                if (firstFailureDetail == null) firstFailureDetail = "$label: $message"
            }
        }

        fun skip(label: String, detail: String, applied: Boolean = false) {
            results += ProfileParamResult(label, applied, detail)
        }

        suspend fun finish(type: ProfileType): AppResult<ProfileApplySummary> {
            val failure = firstFailureDetail
            if (failure != null && undoStack.isNotEmpty()) {
                undoStack.asReversed().forEach { (label, undo) ->
                    val r = runCatching { undo() }.getOrElse { AppResult.Failure(it.message ?: "rollback threw") }
                    logRepository.append(
                        LogEntry(System.currentTimeMillis(), "ROLLBACK_PROFILE_PARAM", label, null, null,
                            if (r is AppResult.Success) "SUCCESS" else "FAILED",
                            if (r is AppResult.Success) null else (r as AppResult.Failure).message)
                    )
                }
                logRepository.append(LogEntry(System.currentTimeMillis(), "APPLY_PROFILE", type.name, null, null, "ROLLED_BACK", failure))
                return AppResult.Failure("Profile apply failed ($failure) -- rolled back ${undoStack.size} change(s) already made.")
            }
            if (failure != null) {
                // Nothing succeeded yet, so nothing to roll back -- just report it.
                logRepository.append(LogEntry(System.currentTimeMillis(), "APPLY_PROFILE", type.name, null, null, "FAILED", failure))
                return AppResult.Failure(failure)
            }
            backupStore.save(ACTIVE_KEY, mapOf("type" to type.name))
            logRepository.append(LogEntry(System.currentTimeMillis(), "APPLY_PROFILE", type.name, null, null, "SUCCESS", null))
            return AppResult.Success(ProfileApplySummary(type, results))
        }
    }
}
