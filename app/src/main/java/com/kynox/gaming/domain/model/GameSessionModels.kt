package com.kynox.gaming.domain.model

/** A sample below [MINOR_DROP_RATIO] of the session average counts as a minor drop. */
const val MINOR_DROP_RATIO = 0.75f

/** A sample below [SEVERE_DROP_RATIO] of the session average counts as a severe drop. */
const val SEVERE_DROP_RATIO = 0.4f

data class SessionSample(
    val elapsedMs: Long,
    val fps: Float?,
    val avgCpuFreqKhz: Int?,
    val gpuFreqKhz: Int?,
    val cpuTempCelsius: Float?,
    val batteryTempCelsius: Float?,
    val powerWatts: Float?,
    val batteryPercent: Int? = null,
    val chargeCounterMicroAh: Int? = null
)

data class SessionReport(
    val packageName: String,
    val gameLabel: String,
    val startedAtMs: Long,
    val durationMs: Long,
    val samples: List<SessionSample>,
    val maxFps: Float?,
    val minFps: Float?,
    val avgFps: Float?,
    val low5PercentFps: Float?,
    val minorDropCount: Int,
    val severeDropCount: Int,
    val avgCpuTemp: Float?,
    val maxCpuTemp: Float?,
    val minCpuTemp: Float?,
    val avgBatteryTemp: Float?,
    val maxBatteryTemp: Float?,
    val minBatteryTemp: Float?,
    val avgPowerWatts: Float?,
    val avgCpuFreqMhz: Float?,
    val maxCpuFreqMhz: Float?,
    val avgGpuFreqMhz: Float?,
    val maxGpuFreqMhz: Float?,
    val fpsSupported: Boolean,
    val resolution: String? = null,
    val startBatteryPercent: Int? = null,
    val endBatteryPercent: Int? = null,
    /** Percentage points lost per hour of play; null when the session was too short, or the phone was charging. */
    val drainPercentPerHour: Float? = null,
    /** Same, but from the fuel gauge's charge counter (finer than whole percent); null when unsupported. */
    val drainMahPerHour: Float? = null,
    val peakCpuTempAtMs: Long? = null,
    val peakBatteryTempAtMs: Long? = null,
    val lowestFpsAtMs: Long? = null
)

/** Latest values shown live on the FPS overlay while a session is recording. */
data class LiveMetrics(
    val fps: Float? = null,
    val cpuTempCelsius: Float? = null,
    val gpuFreqRaw: Int? = null,
    val powerWatts: Float? = null
)
