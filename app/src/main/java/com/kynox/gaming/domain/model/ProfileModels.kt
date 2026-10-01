package com.kynox.gaming.domain.model

enum class ProfileType {
    BALANCED, PERFORMANCE, GAMING, POWERSAVE, CUSTOM
}

/**
 * What a profile actually changes. Deliberately limited to CPU/GPU governor
 * and max frequency -- the two highest-risk controls (Fast Charging, Disable
 * Thermal) stay manual, single-purpose actions with their own confirmation
 * and warning, rather than something a profile switch (or a boot receiver)
 * can trigger silently.
 */
data class ProfileConfig(
    val cpuGovernor: String? = null,
    val cpuMaxFreqKhz: Int? = null,
    val gpuGovernor: String? = null,
    val gpuMaxFreqKhz: Int? = null,
    val thermalPolicy: String? = null
)

data class ProfileParamResult(val label: String, val applied: Boolean, val detail: String)

data class ProfileApplySummary(
    val profile: ProfileType,
    val results: List<ProfileParamResult>
) {
    val appliedCount: Int get() = results.count { it.applied }
    val skippedCount: Int get() = results.count { !it.applied }
}

data class InstalledGame(
    val packageName: String,
    val label: String,
    val isAutoDetectedGame: Boolean,
    val isManaged: Boolean,
    val assignedProfile: ProfileType? = null
)
