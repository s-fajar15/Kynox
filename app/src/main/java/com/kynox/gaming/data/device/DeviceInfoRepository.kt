package com.kynox.gaming.data.device

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.StatFs
import android.os.Environment
import android.util.DisplayMetrics
import android.view.WindowManager
import com.kynox.gaming.core.root.RootExecutor
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
    private val rootRepository: RootRepository,
    private val rootExecutor: RootExecutor? = null
) {

    @Volatile private var cache: DeviceInfo? = null

    /** Hasil pemuatan terakhir (bila ada), supaya layar bisa tampil langsung tanpa menunggu pemeriksaan ulang. */
    fun cached(): DeviceInfo? = cache

    /** Nama pasar dari properti sistem; urutan: yang paling umum dulu. Kosong bila tidak ada satu pun. */
    private suspend fun readMarketName(): String {
        val out = ShellExecutor.run(
            "getprop ro.product.marketname; getprop ro.product.vendor.marketname; " +
                "getprop ro.product.odm.marketname; getprop ro.config.marketing_name; getprop ro.product.system.marketname"
        ).stdout
        return out.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty()
    }

    suspend fun readStorage(): StorageInfo = withContext(Dispatchers.IO) { readStorageInfo() }

    /** Pasangan (terpakai, total) swap/zram dalam byte; (0, 0) bila tidak ada. */
    suspend fun readSwap(): Pair<Long, Long> = withContext(Dispatchers.IO) {
        val text = SysfsAccess.readDirect("/proc/meminfo") ?: return@withContext 0L to 0L
        fun kb(key: String) = text.lineSequence().firstOrNull { it.startsWith(key) }
            ?.filter { it.isDigit() }?.toLongOrNull() ?: 0L
        val total = kb("SwapTotal:") * 1024L
        val free = kb("SwapFree:") * 1024L
        (total - free).coerceAtLeast(0L) to total
    }

    /**
     * Info dasar yang hanya dibaca dari `Build.*`, RAM, layar, dan penyimpanan: tanpa shell, root, atau EGL,
     * sehingga selalu cepat dan tidak bisa menggantung. Dipakai supaya kartu perangkat langsung terisi
     * selagi [load] (yang lebih lengkap) masih berjalan.
     */
    fun basic(): DeviceInfo {
        val memInfo = ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.getMemoryInfo(memInfo)
        val soc = if (Build.VERSION.SDK_INT >= 31) {
            runCatching { Build.SOC_MANUFACTURER to Build.SOC_MODEL }.getOrNull()
        } else null
        return DeviceInfo(
            manufacturer = Build.MANUFACTURER ?: UNKNOWN_VALUE,
            brand = Build.BRAND ?: UNKNOWN_VALUE,
            model = Build.MODEL ?: UNKNOWN_VALUE,
            device = Build.DEVICE ?: UNKNOWN_VALUE,
            androidVersion = Build.VERSION.RELEASE ?: UNKNOWN_VALUE,
            androidSdk = Build.VERSION.SDK_INT,
            socManufacturer = soc?.first?.takeIf { it.isNotBlank() } ?: UNKNOWN_VALUE,
            socModel = soc?.second?.takeIf { it.isNotBlank() } ?: UNKNOWN_VALUE,
            cpuArchitecture = Build.SUPPORTED_ABIS.firstOrNull() ?: UNKNOWN_VALUE,
            cpuCoreCount = Runtime.getRuntime().availableProcessors(),
            supportedAbis = Build.SUPPORTED_ABIS.joinToString(", ").ifBlank { UNKNOWN_VALUE },
            gpuRenderer = UNKNOWN_VALUE,
            gpuVendor = UNKNOWN_VALUE,
            glEsVersion = UNKNOWN_VALUE,
            totalRamBytes = memInfo.totalMem,
            swapTotalBytes = 0L,
            swapFreeBytes = 0L,
            kernelVersion = System.getProperty("os.version") ?: NOT_AVAILABLE_VALUE,
            securityPatch = if (Build.VERSION.SDK_INT >= 23) {
                runCatching { Build.VERSION.SECURITY_PATCH }.getOrNull()?.takeIf { it.isNotBlank() } ?: UNKNOWN_VALUE
            } else UNKNOWN_VALUE,
            fingerprint = Build.FINGERPRINT ?: UNKNOWN_VALUE,
            selinuxStatus = UNKNOWN_VALUE,
            isRooted = false,
            rootImplementation = "None",
            display = readDisplayInfo(),
            storage = readStorageInfo(),
            marketName = "",
            buildDisplay = Build.DISPLAY.orEmpty(),
            board = Build.BOARD.orEmpty(),
            hardware = Build.HARDWARE.orEmpty()
        )
    }

    suspend fun load(): DeviceInfo = withContext(Dispatchers.Default) {
        val root = rootRepository.current()

        val socManufacturer = if (Build.VERSION.SDK_INT >= 31) {
            runCatching { Build.SOC_MANUFACTURER }.getOrNull()?.takeIf { it.isNotBlank() } ?: UNKNOWN_VALUE
        } else UNKNOWN_VALUE

        val socModel = if (Build.VERSION.SDK_INT >= 31) {
            runCatching { Build.SOC_MODEL }.getOrNull()?.takeIf { it.isNotBlank() } ?: UNKNOWN_VALUE
        } else UNKNOWN_VALUE

        val kernelVersion = readKernelVersion(root.isAvailable)
        val selinux = readSelinuxStatus(root.isAvailable)

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

        val built = DeviceInfo(
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
            storage = readStorageInfo(),
            marketName = runCatching { readMarketName() }.getOrDefault(""),
            buildDisplay = Build.DISPLAY.orEmpty(),
            board = Build.BOARD.orEmpty(),
            hardware = Build.HARDWARE.orEmpty()
        )
        cache = built
        built
    }

    /**
     * Versi kernel. Aplikasi biasa dilarang SELinux membaca /proc/version di Android 13+, jadi urutannya:
     * baca langsung -> properti JVM `os.version` (selalu tersedia, tanpa izin) -> `uname -r` -> root.
     */
    private suspend fun readKernelVersion(rootAvailable: Boolean): String {
        SysfsAccess.readDirect("/proc/version")?.takeIf { it.isNotBlank() }?.let { return it }
        System.getProperty("os.version")?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
        ShellExecutor.run("uname -r").takeIf { it.isSuccess && it.stdout.isNotBlank() }?.let { return it.stdout.trim() }
        if (rootAvailable && rootExecutor != null) {
            rootExecutor.execute("uname -r || cat /proc/version", 4000L)
                .takeIf { it.isSuccess && it.stdout.isNotBlank() }?.let { return it.stdout.trim() }
        }
        return NOT_AVAILABLE_VALUE
    }

    /** Status SELinux. `getenforce` ditolak untuk aplikasi biasa, jadi root dipakai bila ada; hasil dinormalkan. */
    private suspend fun readSelinuxStatus(rootAvailable: Boolean): String {
        fun normalize(raw: String?): String? = when (raw?.trim()?.lowercase()) {
            "enforcing", "1" -> "Enforcing"
            "permissive", "0" -> "Permissive"
            "disabled" -> "Disabled"
            else -> null
        }
        ShellExecutor.run("getenforce").takeIf { it.isSuccess }?.let { normalize(it.stdout)?.let { v -> return v } }
        normalize(SysfsAccess.readDirect("/sys/fs/selinux/enforce"))?.let { return it }
        if (rootAvailable && rootExecutor != null) {
            val r = rootExecutor.execute("getenforce 2>/dev/null || cat /sys/fs/selinux/enforce", 4000L)
            if (r.isSuccess) normalize(r.stdout.lineSequence().firstOrNull { it.isNotBlank() })?.let { return it }
        }
        // Cadangan tanpa izin: properti boot (tidak selalu ada).
        val prop = ShellExecutor.run("getprop ro.boot.selinux").stdout
        normalize(prop)?.let { return it }
        return UNKNOWN_VALUE
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
