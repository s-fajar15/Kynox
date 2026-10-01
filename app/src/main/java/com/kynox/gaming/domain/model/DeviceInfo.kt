package com.kynox.gaming.domain.model

data class DeviceInfo(
    val manufacturer: String,
    val brand: String,
    val model: String,
    val device: String,
    val androidVersion: String,
    val androidSdk: Int,
    val socManufacturer: String,
    val socModel: String,
    val cpuArchitecture: String,
    val cpuCoreCount: Int,
    val supportedAbis: String,
    val gpuRenderer: String,
    val gpuVendor: String,
    val glEsVersion: String,
    val totalRamBytes: Long,
    val swapTotalBytes: Long,
    val swapFreeBytes: Long,
    val kernelVersion: String,
    val securityPatch: String,
    val fingerprint: String,
    val selinuxStatus: String,
    val isRooted: Boolean,
    val rootImplementation: String,
    val display: DisplayInfo,
    val storage: StorageInfo
)

data class DisplayInfo(
    val resolution: String,
    val refreshRateHz: Float,
    val densityDpi: Int,
    val sizeInches: Float,
    val hdrTypes: String
)

data class StorageInfo(
    val totalBytes: Long,
    val usedBytes: Long,
    val freeBytes: Long
)

data class RamUsage(
    val usedBytes: Long,
    val totalBytes: Long,
    val usedPercent: Float
)

const val UNKNOWN_VALUE = "Unknown"
const val NOT_AVAILABLE_VALUE = "Not available"
