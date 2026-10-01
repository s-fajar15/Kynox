package com.kynox.gaming.domain.model

data class CpuCoreState(
    val core: Int,
    val online: Boolean?,
    val currentFreqKhz: Int?,
    val minFreqKhz: Int?,
    val maxFreqKhz: Int?,
    val availableFreqsKhz: List<Int>,
    val governor: String?,
    val availableGovernors: List<String>,
    val canWriteGovernor: Boolean,
    val canWriteFreq: Boolean,
    /** Core 0 can never be taken offline here -- on most SoCs it is the boot/interrupt-routing core and disabling it can hang the device. */
    val canToggleOnline: Boolean
)

data class CpuSnapshot(
    val cores: List<CpuCoreState>,
    val overallUsagePercent: Float?
)
