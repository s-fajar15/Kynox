package com.kynox.gaming.data.device

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.StatFs
import android.os.Environment
import android.util.DisplayMetrics
import android.view.WindowManager
import com.kynox.gaming.core.shell.ShellExecutor
import com.kynox.gaming.core.sysfs.CapabilityEngine
import com.kynox.gaming.core.sysfs.SysfsAccess
import com.kynox.gaming.core.utils.GpuRendererProbe
import com.kynox.gaming.data.root.RootRepository
import com.kynox.gaming.domain.model.DeviceInfo
import com.kynox.gaming.domain.model.DisplayInfo
import com.kynox.gaming.domain.model.NOT_AVAILABLE_VALUE
import com.kynox.gaming.domain.model.RamUsage
import com.kynox.gaming.domain.model.StorageInfo
import com.kynox.gaming.domain.model.UNKNOWN_VALUE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

class DeviceInfoRepository(
    private val context: Context,
    private val capabilityEngine: CapabilityEngine,
    private val rootRepository: RootRepository
) {

    suspend fun load(): DeviceInfo = withContext(Dispatchers.Default) {
        val root = rootRepository.current()

        val socManufacturer = if (Build.VERSION.SDK_INT >= 31) {
            runCatching { Build.SOC_MANUFACTURER }.getOrNull()?.takeIf { it.isNotBlank() } ?: UNKNOWN_VALUE
        } else UNKNOWN_VALUE

        val socModel = if (Build.VERSION.SDK_INT >= 31) {
            runCatching { Build.SOC_MODEL }.getOrNull()?.takeIf { it.isNotBlank() } ?: UNKNOWN_VALUE
        } else UNKNOWN_VALUE

        val kernelVersion = SysfsAccess.readDirect("/proc/version")
            ?: ShellExecutor.run("cat /proc/version").takeIf { it.isSuccess }?.stdout
            ?: NOT_AVAILABLE_VALUE

        val selinux = ShellExecutor.run("getenforce").let { result ->
            if (result.isSuccess && result.stdout.isNotBlank()) result.stdout
            else SysfsAccess.readDirect("/sys/fs/selinux/enforce")?.let {
                if (it.trim() == "1") "Enforcing" else "Permissive"
            } ?: UNKNOWN_VALUE
        }

        val gpuProbe = withContext(Dispatchers.Default) {
            GpuRendererProbe.probeAll()
        }

        val securityPatch = if (Build.VERSION.SDK_INT >= 23) {
            runCatching { Build.VERSION.SECURITY_PATCH }.getOrNull()?.takeIf { it.isNotBlank() } ?: UNKNOWN_VALUE
        } else UNKNOWN_VALUE

        val fingerprint = runCatching { Build.FINGERPRINT }.getOrNull()?.takeIf { it.isNotBlank() } ?: UNKNOWN_VALUE

        val memInfo = ActivityManager.MemoryInfo()
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        am?.getMemoryInfo(memInfo)

        val swapTotalKb = SysfsAccess.readDirect("/proc/meminfo")
            ?.lineSequence()
            ?.firstOrNull { it.startsWith("SwapTotal:") }
            ?.filter { it.isDigit() }
            ?.toLongOrNull() ?: 0L
        val swapFreeKb = SysfsAccess.readDirect("/proc/meminfo")
            ?.lineSequence()
            ?.firstOrNull { it.startsWith("SwapFree:") }
            ?.filter { it.isDigit() }
            ?.toLongOrNull() ?: 0L

        DeviceInfo(
            manufacturer = Build.MANUFACTURER ?: UNKNOWN_VALUE,
            brand = Build.BRAND ?: UNKNOWN_VALUE,
            model = Build.MODEL ?: UNKNOWN_VALUE,
            device = Build.DEVICE ?: UNKNOWN_VALUE,
            androidVersion = Build.VERSION.RELEASE ?: UNKNOWN_VALUE,
            androidSdk = Build.VERSION.SDK_INT,
            socManufacturer = socManufacturer,
            socModel = socModel,
            cpuArchitecture = Build.SUPPORTED_ABIS.firstOrNull() ?: UNKNOWN_VALUE,
            cpuCoreCount = capabilityEngine.cpuCoreCount(),
            supportedAbis = Build.SUPPORTED_ABIS.joinToString(", ").ifBlank { UNKNOWN_VALUE },
            gpuRenderer = gpuProbe.renderer ?: UNKNOWN_VALUE,
            gpuVendor = gpuProbe.vendor ?: UNKNOWN_VALUE,
            glEsVersion = gpuProbe.version ?: UNKNOWN_VALUE,
            totalRamBytes = memInfo.totalMem,
            swapTotalBytes = swapTotalKb * 1024L,
            swapFreeBytes = swapFreeKb * 1024L,
            kernelVersion = kernelVersion,
            securityPatch = securityPatch,
            fingerprint = fingerprint,
            selinuxStatus = selinux,
            isRooted = root.isAvailable,
            rootImplementation = if (root.isAvailable) root.provider.name else "None",
            display = readDisplayInfo(),
            storage = readStorageInfo()
        )
    }

    private fun readDisplayInfo(): DisplayInfo {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        val metrics = DisplayMetrics()
        return try {
            @Suppress("DEPRECATION")
            val display = wm?.defaultDisplay
            @Suppress("DEPRECATION")
            display?.getRealMetrics(metrics)
            val refreshRate = display?.refreshRate ?: 60f

            val widthInches = metrics.widthPixels / metrics.xdpi
            val heightInches = metrics.heightPixels / metrics.ydpi
            val diagonal = sqrt((widthInches * widthInches + heightInches * heightInches).toDouble()).toFloat()

            val hdrTypes = if (Build.VERSION.SDK_INT >= 24) {
                runCatching {
                    display?.hdrCapabilities?.supportedHdrTypes
                        ?.joinToString(", ") { hdrTypeName(it) }
                        ?.ifBlank { "None" }
                }.getOrNull() ?: UNKNOWN_VALUE
            } else UNKNOWN_VALUE

            DisplayInfo(
                resolution = "${metrics.widthPixels} x ${metrics.heightPixels}",
                refreshRateHz = refreshRate,
                densityDpi = metrics.densityDpi,
                sizeInches = diagonal,
                hdrTypes = hdrTypes
            )
        } catch (t: Throwable) {
            DisplayInfo(UNKNOWN_VALUE, 0f, 0, 0f, UNKNOWN_VALUE)
        }
    }

    private fun hdrTypeName(type: Int): String = when (type) {
        1 -> "Dolby Vision"
        2 -> "HDR10"
        3 -> "HLG"
        4 -> "HDR10+"
        else -> "Type $type"
    }

    private fun readStorageInfo(): StorageInfo {
        return try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val total = stat.blockCountLong * stat.blockSizeLong
            val free = stat.availableBlocksLong * stat.blockSizeLong
            StorageInfo(totalBytes = total, usedBytes = (total - free).coerceAtLeast(0), freeBytes = free)
        } catch (t: Throwable) {
            StorageInfo(0, 0, 0)
        }
    }

    fun readRamUsage(): RamUsage {
        val memInfo = ActivityManager.MemoryInfo()
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        am?.getMemoryInfo(memInfo)
        val total = memInfo.totalMem
        val used = (total - memInfo.availMem).coerceAtLeast(0)
        val percent = if (total > 0) (used.toFloat() / total.toFloat()) * 100f else 0f
        return RamUsage(usedBytes = used, totalBytes = total, usedPercent = percent)
    }
}
