package com.kynox.gaming.domain.model

/** Which metrics the user wants shown in the standalone performance overlay (Settings). */
data class OverlaySettings(
    val showFps: Boolean = true,
    val showCpu: Boolean = true,
    val showGpu: Boolean = true,
    val showBatteryTemp: Boolean = true
) {
    val anyEnabled: Boolean get() = showFps || showCpu || showGpu || showBatteryTemp
}

/** One tick of live data for the standalone overlay -- independent from a recorded [SessionReport]. */
data class QuickOverlayMetrics(
    val fps: Float?,
    val cpuUsagePercent: Float?,
    val gpuUsagePercent: Float?,
    val gpuFreqRaw: Int?,
    val batteryTempCelsius: Float?
)
