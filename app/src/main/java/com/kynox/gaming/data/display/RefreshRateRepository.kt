package com.kynox.gaming.data.display

import android.content.Context
import android.hardware.display.DisplayManager
import android.provider.Settings
import android.view.Display
import android.view.WindowManager
import com.kynox.gaming.core.root.RootExecutor
import com.kynox.gaming.data.backup.BackupStore
import com.kynox.gaming.data.logs.LogRepository
import com.kynox.gaming.domain.model.LogEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

private const val OVERRIDES_KEY = "refresh_rate_overrides"
private const val ENABLED_KEY = "refresh_rate_enabled"
private const val GAME_OVERRIDES_KEY = "refresh_rate_game_overrides"
private const val APPLY_TIMEOUT_MS = 3000L
private const val DEFAULT_REFRESH_RATE_HZ = 60

private const val SETTLE_MS = 300L
private const val PROBE_SETTLE_MS = 450L
private const val PROBE_COOLDOWN_MS = 10 * 60 * 1000L

/**
 * Per-app refresh policy that works on any ROM/device. It does not assume one
 * vendor: it tries several independent strategies in order and keeps the first
 * one whose result is *verified* against the real active display mode.
 *
 *  1. Root: AOSP settings (peak/min_refresh_rate), vendor Hz keys that already
 *     exist on the device, and `cmd display set-user-preferred-display-mode`.
 *  2. Root + auto-detect: vendor enum keys (e.g. Samsung/Oplus style modes) are
 *     discovered from `settings list` and probed; the value that really changes
 *     the panel is cached, anything that does not help is restored.
 *  3. No root: a 1px invisible overlay that requests a preferred display mode
 *     (needs "display over other apps", which Kynox already asks for).
 */
class RefreshRateRepository(
    private val context: Context,
    private val rootExecutor: RootExecutor,
    private val backupStore: BackupStore,
    private val logRepository: LogRepository
) {
    private val overlay = RefreshRateOverlay(context)
    private val vendorWorking = mutableMapOf<Int, Triple<String, String, String>>()
    private val lastProbeAt = mutableMapOf<Int, Long>()


    fun supportedRefreshRates(): List<Int> {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        @Suppress("DEPRECATION")
        val display = windowManager?.defaultDisplay ?: return listOf(60)
        return display.supportedModes
            .map { it.refreshRate.roundToInt() }
            .filter { it > 0 }
            .distinct()
            .sortedDescending()
            .ifEmpty { listOf(60) }
    }

    fun deviceMaxRefreshRate(): Int = supportedRefreshRates().maxOrNull() ?: 60

    fun defaultRefreshRate(): Int = supportedRefreshRates()
        .minByOrNull { kotlin.math.abs(it - DEFAULT_REFRESH_RATE_HZ) }
        ?: DEFAULT_REFRESH_RATE_HZ

    suspend fun overrides(): Map<String, Int> = withContext(Dispatchers.IO) {
        backupStore.load(OVERRIDES_KEY)
            ?.mapNotNull { (pkg, value) -> value?.toIntOrNull()?.let { pkg to it } }
            ?.toMap()
            ?: emptyMap()
    }

    suspend fun setOverride(packageName: String, hz: Int?) = withContext(Dispatchers.IO) {
        val current = overrides().toMutableMap()
        if (hz == null) current.remove(packageName) else current[packageName] = hz
        backupStore.save(OVERRIDES_KEY, current.mapValues { it.value.toString() })
    }

    suspend fun targetForPackage(packageName: String?): Int = withContext(Dispatchers.IO) {
        packageName?.let { overrides()[it] } ?: defaultRefreshRate()
    }

    /** Applies the requested policy and optionally records a user-visible log entry. */
    suspend fun applyForPackage(packageName: String?, recordLog: Boolean = true): Boolean = withContext(Dispatchers.IO) {
        val target = packageName?.let { overrides()[it] } ?: defaultRefreshRate()
        val mode = supportedDisplayModeFor(target)
        val root = hasRoot()
        var method = "none"
        var verified = isActiveRate(target)
        if (verified) method = "already"

        if (root) {
            runRootPolicy(target, mode, packageName)
            delay(SETTLE_MS)
            verified = isActiveRate(target)
            if (verified) method = "root"
            if (!verified && tryVendorKeys(target)) {
                verified = true
                method = "root-vendor"
            }
        }

        if (!verified && mode != null && overlay.canShow()) {
            overlay.show(mode.modeId, mode.refreshRate)
            delay(SETTLE_MS + 200)
            verified = isActiveRate(target)
            if (verified) method = "overlay"
        }
        // The overlay is only a fallback: drop it when another path works or when going back to the default.
        if (method != "overlay" || target == defaultRefreshRate()) overlay.hide()

        if (verified && target > defaultRefreshRate() && isGamePackage(packageName)) {
            markGameOverride(packageName!!, true)
        }

        if (recordLog) {
            val active = readActiveDisplayRefreshRate()
            logRepository.append(
                LogEntry(
                    timestamp = System.currentTimeMillis(),
                    action = "APPLY_REFRESH_RATE",
                    target = packageName ?: "(default)",
                    previousValue = null,
                    newValue = "$target Hz ($method)",
                    result = if (verified) "SUCCESS" else "FAILED",
                    error = when {
                        verified -> null
                        !root && !overlay.canShow() -> "no root and overlay permission not granted"
                        active != null -> "system reports active ${active}Hz after requesting ${target}Hz"
                        else -> "could not verify the active refresh rate"
                    }
                )
            )
        }
        verified
    }

    private suspend fun hasRoot(): Boolean = try { rootExecutor.status().isAvailable } catch (_: Throwable) { false }

    private suspend fun runRootPolicy(target: Int, mode: Display.Mode?, packageName: String?) {
        val displayCommand = mode?.let {
            "cmd display set-user-preferred-display-mode ${it.physicalWidth} ${it.physicalHeight} ${it.refreshRate} 0"
        } ?: "true"
        // Preference 0 = MATCH_CONTENT_FRAMERATE_NEVER, so a 60 fps game cannot pull a forced rate back down.
        val matchContentCommand = "cmd display set-match-content-frame-rate-pref 0"
        val gameFpsCommand = if (target > defaultRefreshRate() && isGamePackage(packageName)) {
            "cmd game set --mode 2 --fps $target ${packageName!!.replace("'", "")}"
        } else "true"
        // Hz-valued vendor keys are written only when the device already has them.
        val vendor = listOf("system user_refresh_rate", "secure user_refresh_rate", "secure miui_refresh_rate")
            .joinToString("; ") { "set_if $it $target" }
        val script = "set_if() { v=\$(settings get \$1 \$2 2>/dev/null); " +
            "if [ -n \"\$v\" ] && [ \"\$v\" != \"null\" ]; then settings put \$1 \$2 \$3; fi; }; " +
            "$matchContentCommand; $gameFpsCommand; $displayCommand; " +
            "settings put system peak_refresh_rate $target; settings put system min_refresh_rate $target; $vendor"
        rootExecutor.execute(script, APPLY_TIMEOUT_MS)
    }

    /** Finds vendor "mode" keys on this ROM and keeps the value that really switches the panel. */
    private suspend fun tryVendorKeys(target: Int): Boolean {
        vendorWorking[target]?.let { (ns, key, value) ->
            rootExecutor.execute("settings put $ns $key $value", APPLY_TIMEOUT_MS)
            delay(PROBE_SETTLE_MS)
            if (isActiveRate(target)) return true
            vendorWorking.remove(target)
        }
        val now = System.currentTimeMillis()
        if (now - (lastProbeAt[target] ?: 0L) < PROBE_COOLDOWN_MS) return false
        lastProbeAt[target] = now

        val dump = rootExecutor.execute(
            "for ns in system secure global; do settings list \$ns 2>/dev/null | sed \"s/^/\$ns /\"; done",
            APPLY_TIMEOUT_MS
        ).stdout
        val skip = setOf("peak_refresh_rate", "min_refresh_rate", "user_refresh_rate", "miui_refresh_rate")
        val keyPattern = Regex("refresh_?rate|screen_?fps|fps_?mode|display_?rate", RegexOption.IGNORE_CASE)
        val candidates = dump.lines().mapNotNull { line ->
            val ns = line.substringBefore(' ', "")
            val rest = line.substringAfter(' ', "")
            val key = rest.substringBefore('=', "")
            val value = rest.substringAfter('=', "").trim()
            if (ns.isBlank() || key.isBlank() || key in skip || !keyPattern.containsMatchIn(key)) null
            else if (!Regex("^\\d{1,2}$").matches(value)) null
            else Triple(ns, key, value)
        }.distinct().take(6)

        for ((ns, key, original) in candidates) {
            for (value in 0..4) {
                if (value.toString() == original) continue
                rootExecutor.execute("settings put $ns $key $value", APPLY_TIMEOUT_MS)
                delay(PROBE_SETTLE_MS)
                if (isActiveRate(target)) {
                    vendorWorking[target] = Triple(ns, key, value.toString())
                    return true
                }
            }
            rootExecutor.execute("settings put $ns $key $original", APPLY_TIMEOUT_MS)
        }
        return false
    }

    /** Real active refresh rate from the public Display API; works without root on every device. */
    fun currentDisplayHz(): Int? = try {
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        dm?.getDisplay(Display.DEFAULT_DISPLAY)?.refreshRate?.roundToInt()
    } catch (_: Throwable) {
        null
    }

    private suspend fun isActiveRate(target: Int): Boolean {
        val hz = readActiveDisplayRefreshRate() ?: return false
        return abs(hz - target) <= 1
    }

    /** Prefers modes with the panel's current resolution, so a rate switch never changes resolution. */
    private fun supportedDisplayModeFor(targetHz: Int): Display.Mode? {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        @Suppress("DEPRECATION")
        val display = windowManager?.defaultDisplay ?: return null
        val all = display.supportedModes.filter { it.refreshRate > 0f }
        val current = display.mode
        val sameSize = all.filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
        return (sameSize.ifEmpty { all }).minByOrNull { abs(it.refreshRate - targetHz) }
    }

    private fun isGamePackage(packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        return try {
            val info = context.packageManager.getApplicationInfo(packageName, 0)
            info.category == android.content.pm.ApplicationInfo.CATEGORY_GAME
        } catch (_: Throwable) {
            false
        }
    }
    /** The refresh rate the display is really running at right now (root dumpsys when available, public API otherwise). */
    suspend fun readActiveDisplayRefreshRate(): Int? = withContext(Dispatchers.IO) {
        if (hasRoot()) {
            val result = rootExecutor.execute(
                "dumpsys display | grep -E 'mActiveSfDisplayMode|renderFrameRate' | head -n 4",
                APPLY_TIMEOUT_MS
            )
            if (result.isSuccess) {
                val text = result.stdout
                val refresh = Regex("refreshRate=([0-9]+(?:\\.[0-9]+)?)").find(text)?.groupValues?.get(1)
                    ?: Regex("renderFrameRate\\s+([0-9]+(?:\\.[0-9]+)?)").find(text)?.groupValues?.get(1)
                refresh?.toFloatOrNull()?.roundToInt()?.let { return@withContext it }
            }
        }
        currentDisplayHz()
    }

    /** The global peak setting, readable without root. Used only as a fallback/diagnostic. */
    suspend fun currentAppliedHz(): Int? = withContext(Dispatchers.IO) { readPeakRefreshRate() }

    private suspend fun readPeakRefreshRate(): Int? = withContext(Dispatchers.IO) {
        val direct = try {
            Settings.System.getFloat(context.contentResolver, "peak_refresh_rate", 0f).takeIf { it > 0f }?.roundToInt()
        } catch (_: Throwable) {
            null
        }
        direct ?: if (hasRoot()) {
            rootExecutor.execute("settings get system peak_refresh_rate", APPLY_TIMEOUT_MS)
                .stdout.trim().toFloatOrNull()?.roundToInt()
        } else null
    }

    suspend fun clearGameFrameRateOverride(packageName: String) = withContext(Dispatchers.IO) {
        val result = rootExecutor.execute("cmd game reset $packageName", APPLY_TIMEOUT_MS)
        if (result.isSuccess) {
            val current = backupStore.load(GAME_OVERRIDES_KEY).orEmpty().toMutableMap()
            current.remove(packageName)
            backupStore.save(GAME_OVERRIDES_KEY, current)
        }
        result.isSuccess
    }

    suspend fun clearTrackedGameFrameRateOverrides() = withContext(Dispatchers.IO) {
        val tracked = backupStore.load(GAME_OVERRIDES_KEY).orEmpty().keys.toList()
        for (packageName in tracked) {
            try {
                clearGameFrameRateOverride(packageName)
            } catch (_: Throwable) {
                // Best effort cleanup; the display policy itself remains safe.
            }
        }
    }

    private suspend fun markGameOverride(packageName: String, applied: Boolean) {
        val current = backupStore.load(GAME_OVERRIDES_KEY).orEmpty().toMutableMap()
        if (applied) current[packageName] = "true" else current.remove(packageName)
        backupStore.save(GAME_OVERRIDES_KEY, current)
    }

    /** Android 13+ AOSP display preference. 0 = never match content frame rate. */
    suspend fun matchContentFrameRatePreference(): Int? = withContext(Dispatchers.IO) {
        val result = rootExecutor.execute("cmd display get-match-content-frame-rate-pref", APPLY_TIMEOUT_MS)
        Regex("(?:type:|preference|rate type:|: )\\s*(-?\\d+)", RegexOption.IGNORE_CASE)
            .find(result.stdout)?.groupValues?.get(1)?.toIntOrNull()
            ?: result.stdout.trim().takeLastWhile { it.isDigit() }.toIntOrNull()
    }

    suspend fun setMatchContentFrameRatePreference(value: Int) = withContext(Dispatchers.IO) {
        rootExecutor.execute("cmd display set-match-content-frame-rate-pref $value", APPLY_TIMEOUT_MS).isSuccess
    }

    suspend fun restoreDeviceDefault() = applyForPackage(null)

    suspend fun isEnabled(): Boolean = withContext(Dispatchers.IO) {
        backupStore.load(ENABLED_KEY)?.get("value") == "true"
    }

    suspend fun setEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        backupStore.save(ENABLED_KEY, mapOf("value" to enabled.toString()))
    }
}
