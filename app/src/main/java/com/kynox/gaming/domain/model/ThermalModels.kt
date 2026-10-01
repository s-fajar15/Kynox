package com.kynox.gaming.domain.model

data class ThermalZone(
    val zoneName: String,
    val sysfsPath: String,
    val sensorType: String,
    val temperatureCelsius: Float?,
    val tripPoints: List<ThermalTripPoint>,
    val policy: String? = null,
    val availablePolicies: List<String> = emptyList()
)

data class ThermalTripPoint(
    val index: Int,
    val type: String?,
    val temperatureCelsius: Float?
)

/** What the thermal engine is doing right now, read back from the system. */
enum class ThermalEngineMode { ACTIVE, DISABLED, PARTIAL }

data class ThermalDisableState(
    val supported: Boolean,
    /** True whenever anything is switched off (fully or partly). */
    val active: Boolean,
    val reason: String,
    val mode: ThermalEngineMode = ThermalEngineMode.ACTIVE,
    /** Kernel switches found (zone modes + module parameters) and how many read as disabled. */
    val controlsTotal: Int = 0,
    val controlsDisabled: Int = 0,
    /** Vendor thermal daemons this app can stop, and their current state. */
    val daemonsRunning: Int = 0,
    val daemonsStopped: Int = 0
)

/**
 * The kernel's thermal governor (e.g. `step_wise`, `user_space`), summarised
 * across every zone that exposes one -- almost all zones on a device report
 * the same policy and the same available choices, so the profile screen
 * shows and changes this once rather than per zone.
 */
data class ThermalPolicySummary(
    val supported: Boolean,
    val currentPolicy: String?,
    val availablePolicies: List<String>,
    val supportedZoneCount: Int,
    val totalZoneCount: Int
)
