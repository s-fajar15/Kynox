package com.kynox.gaming.domain.model

data class ProcessInfo(
    val pid: Int,
    val user: String,
    val name: String,
    val cpuPercent: Float?,
    val memoryKb: Long?,
    val memoryPercent: Float?,
    val uptimeSeconds: Long?
) {
    val isSystemCritical: Boolean get() = pid <= 2 || name == "system_server" || name == "zygote" || name == "zygote64" || name.startsWith("kernel")
}

enum class ProcessSort { CPU, MEMORY, NAME }
