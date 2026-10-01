package com.kynox.gaming.domain.model

/**
 * Frequency fields hold the kernel's native unit (Hz on kgsl/devfreq), so they
 * can be written back unchanged. Use gpuFreqMhz() to display them.
 */
data class GpuState(
    val supported: Boolean,
    val currentFreqKhz: Int?,
    val minFreqKhz: Int?,
    val maxFreqKhz: Int?,
    val availableFreqsKhz: List<Int>,
    val governor: String?,
    val availableGovernors: List<String>,
    val utilizationPercent: Float?,
    val temperatureCelsius: Float?,
    val canWriteGovernor: Boolean,
    val canWriteFreq: Boolean,
    val reason: String
)
