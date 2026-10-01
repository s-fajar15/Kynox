package com.kynox.gaming.domain.model

/** One reading of the always-on monitor. [tMs] is wall-clock time, so history stays meaningful across restarts. */
data class MonitorSample(
    val tMs: Long,
    val cpuUsage: Float?,
    val cpuFreqMhz: Float?,
    val cpuTemp: Float?,
    val gpuUsage: Float?,
    val gpuFreqMhz: Float?,
    val ramUsage: Float?,
    val currentMa: Float?,
    val powerWatts: Float?,
    val batteryTemp: Float?
)
