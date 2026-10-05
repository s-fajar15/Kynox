package com.kynox.gaming.data.cleaner

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import com.kynox.gaming.core.root.RootExecutor
import com.kynox.gaming.data.logs.LogRepository
import com.kynox.gaming.domain.model.LogEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

/** SAFE: hanya proses yang aman dihentikan. AGGRESSIVE: paksa henti semua aplikasi pihak ketiga yang berjalan (root). */
enum class CleanMode { SAFE, AGGRESSIVE }

/** Hasil pembersihan. Nilai null berarti tidak bisa diukur / tidak dikerjakan di perangkat ini. */
data class CleanResult(
    val ramFreedBytes: Long?,
    val cacheFreedBytes: Long?,
    val usedRoot: Boolean,
    val note: String?,
    val appsStopped: Int? = null,
    val availableBefore: Long? = null,
    val availableAfter: Long? = null
)

/** Keadaan memori saat ini untuk ditampilkan sebelum membersihkan. */
data class MemorySnapshot(val totalBytes: Long, val availableBytes: Long) {
    val usedBytes: Long get() = (totalBytes - availableBytes).coerceAtLeast(0L)
    val usedPercent: Float get() = if (totalBytes <= 0L) 0f else usedBytes * 100f / totalBytes
}

private const val ROOT_TIMEOUT_MS = 20000L
private val PACKAGE_NAME = Regex("^[A-Za-z0-9._]+$")

/**
 * Membersihkan RAM dan cache.
 *
 * RAM: Android menghitung cache halaman sebagai memori "tersedia", jadi satu-satunya cara nyata menambah
 * memori tersedia adalah menghentikan proses aplikasi. Dengan root, Kynox mencari aplikasi pihak ketiga yang
 * sedang berjalan lalu menghentikannya (kecuali launcher, keyboard, layanan aksesibilitas/notifikasi, dan Kynox).
 * Tanpa root hanya `killBackgroundProcesses` yang diizinkan Android, dan efeknya terbatas.
 *
 * Hasil diukur dari memori tersedia sebelum dan sesudah, jadi angkanya apa adanya, bukan perkiraan.
 */
class CleanerRepository(
    private val context: Context,
    private val rootExecutor: RootExecutor,
    private val logRepository: LogRepository
) {

    fun memory(): MemorySnapshot {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        return MemorySnapshot(info.totalMem, info.availMem)
    }

    private fun freeDataBytes(): Long = try {
        StatFs(Environment.getDataDirectory().path).availableBytes
    } catch (_: Throwable) {
        0L
    }

    suspend fun isRootAvailable(): Boolean = try { rootExecutor.status().isAvailable } catch (_: Throwable) { false }

    suspend fun cleanRam(mode: CleanMode = CleanMode.SAFE): CleanResult = withContext(Dispatchers.IO) {
        val before = memory().availableBytes
        val root = isRootAvailable()
        var stopped: Int? = null
        var note: String? = null
        var ok = true
        var error: String? = null

        if (root) {
            val protectedSet = protectedPackages(root = true)
            val targets = runningThirdPartyPackages().filter { it !in protectedSet && PACKAGE_NAME.matches(it) }
            val verb = if (mode == CleanMode.AGGRESSIVE) "force-stop" else "kill"
            val loop = if (targets.isEmpty()) "true" else "for p in ${targets.joinToString(" ")}; do am $verb \$p; done"
            val result = rootExecutor.execute("am kill-all; $loop", ROOT_TIMEOUT_MS)
            if (!result.isSuccess) {
                ok = false
                error = result.stderr.take(160).ifBlank { "perintah root gagal" }
            }
            if (mode == CleanMode.AGGRESSIVE) {
                rootExecutor.execute(
                    "sync; echo 3 > /proc/sys/vm/drop_caches; " +
                        "[ -w /proc/sys/vm/compact_memory ] && echo 1 > /proc/sys/vm/compact_memory; true",
                    ROOT_TIMEOUT_MS
                )
            }
            stopped = targets.size
        } else {
            val sent = killBackgroundApps()
            note = "Tanpa root, Android hanya mengizinkan penghentian proses latar (permintaan dikirim ke $sent aplikasi), jadi hasilnya terbatas."
        }

        // Beri waktu sistem melepas memori sebelum diukur.
        delay(1800)
        val after = memory().availableBytes
        val delta = after - before
        log("CLEAN_RAM", "${if (root) "root" else "non-root"}/${mode.name.lowercase()}", "${delta / 1048576} MB", ok, error)
        CleanResult(
            ramFreedBytes = delta.coerceAtLeast(0L),
            cacheFreedBytes = null,
            usedRoot = root,
            note = note,
            appsStopped = stopped,
            availableBefore = before,
            availableAfter = after
        )
    }

    suspend fun cleanCache(): CleanResult = withContext(Dispatchers.IO) {
        val root = isRootAvailable()
        var note: String? = null
        val before = freeDataBytes()
        var ok = true
        var error: String? = null
        val freed: Long
        if (root) {
            // trim-caches meminta sistem membuang cache semua aplikasi hingga ukuran yang diminta.
            val trim = rootExecutor.execute("pm trim-caches 999999999999", ROOT_TIMEOUT_MS)
            if (!trim.isSuccess) {
                // Cadangan untuk ROM yang tidak punya trim-caches: hapus isi folder cache langsung.
                val rm = rootExecutor.execute(
                    "rm -rf /data/data/*/cache/* /data/user/*/*/cache/* /data/user_de/*/*/cache/* " +
                        "/sdcard/Android/data/*/cache/* 2>/dev/null; true",
                    ROOT_TIMEOUT_MS
                )
                ok = rm.isSuccess
                if (!ok) error = rm.stderr.take(160)
            }
            delay(600)
            freed = (freeDataBytes() - before).coerceAtLeast(0L)
        } else {
            freed = clearOwnCache()
            note = "Tanpa root, hanya cache Kynox yang bisa dihapus. Cache aplikasi lain dibersihkan lewat Pengaturan > Penyimpanan."
        }
        log("CLEAN_CACHE", if (root) "root" else "non-root", "${freed / 1048576} MB", ok, error)
        CleanResult(ramFreedBytes = null, cacheFreedBytes = freed, usedRoot = root, note = note)
    }

    /** Aplikasi pihak ketiga yang punya proses berjalan saat ini (butuh root untuk membaca daftar proses). */
    private suspend fun runningThirdPartyPackages(): Set<String> {
        val thirdParty = rootExecutor.execute("pm list packages -3", ROOT_TIMEOUT_MS).stdout
            .lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("package:") }
            .map { it.removePrefix("package:").trim() }
            .filter { it.isNotEmpty() }
            .toSet()
        if (thirdParty.isEmpty()) return emptySet()
        val ps = rootExecutor.execute("ps -A -o NAME 2>/dev/null || ps", ROOT_TIMEOUT_MS).stdout
        return ps.lineSequence()
            .map { it.trim().substringAfterLast(' ').substringBefore(':') }
            .filter { it in thirdParty }
            .toSet()
    }

    /** Paket yang tidak boleh dihentikan: Kynox, launcher, keyboard, layanan aksesibilitas dan pendengar notifikasi. */
    private suspend fun protectedPackages(root: Boolean): Set<String> {
        val result = mutableSetOf(context.packageName, "com.android.systemui", "android")
        try {
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            context.packageManager.resolveActivity(home, 0)?.activityInfo?.packageName?.let { result.add(it) }
        } catch (_: Throwable) {
        }
        if (root) {
            try {
                val out = rootExecutor.execute(
                    "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME",
                    ROOT_TIMEOUT_MS
                ).stdout
                out.lineSequence().map { it.trim() }.lastOrNull { it.contains('/') }
                    ?.substringBefore('/')?.takeIf { it.isNotBlank() }?.let { result.add(it) }
            } catch (_: Throwable) {
            }
        }
        val resolver = context.contentResolver
        try {
            Settings.Secure.getString(resolver, Settings.Secure.DEFAULT_INPUT_METHOD)
                ?.substringBefore('/')?.takeIf { it.isNotBlank() }?.let { result.add(it) }
        } catch (_: Throwable) {
        }
        for (key in listOf("enabled_accessibility_services", "enabled_notification_listeners")) {
            try {
                Settings.Secure.getString(resolver, key)?.split(':')?.forEach { component ->
                    component.substringBefore('/').takeIf { it.isNotBlank() }?.let { result.add(it) }
                }
            } catch (_: Throwable) {
            }
        }
        return result
    }

    /** Tanpa root: minta Android menghentikan proses latar tiap aplikasi yang punya ikon peluncur. */
    private suspend fun killBackgroundApps(): Int {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val protectedSet = protectedPackages(root = false)
        val packages = context.packageManager.queryIntentActivities(launcher, 0)
            .map { it.activityInfo.packageName }
            .filter { it !in protectedSet }
            .toSet()
        packages.forEach { pkg ->
            try {
                am.killBackgroundProcesses(pkg)
            } catch (_: Throwable) {
            }
        }
        return packages.size
    }

    private fun clearOwnCache(): Long {
        var freed = 0L
        fun wipe(dir: File?) {
            if (dir == null || !dir.exists()) return
            dir.listFiles()?.forEach { child ->
                freed += sizeOf(child)
                child.deleteRecursively()
            }
        }
        wipe(context.cacheDir)
        wipe(context.externalCacheDir)
        return freed
    }

    private fun sizeOf(file: File): Long =
        if (file.isFile) file.length() else file.listFiles()?.sumOf { sizeOf(it) } ?: 0L

    private suspend fun log(action: String, target: String, newValue: String?, ok: Boolean, error: String?) {
        try {
            logRepository.append(
                LogEntry(
                    timestamp = System.currentTimeMillis(),
                    action = action,
                    target = target,
                    previousValue = null,
                    newValue = newValue,
                    result = if (ok) "SUCCESS" else "FAILED",
                    error = error
                )
            )
        } catch (_: Throwable) {
        }
    }
}
