package com.kynox.gaming.data.display

import android.content.Context
import android.view.WindowManager
import com.kynox.gaming.core.root.RootExecutor
import com.kynox.gaming.data.backup.BackupStore
import com.kynox.gaming.data.logs.LogRepository
import com.kynox.gaming.domain.model.LogEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private const val OVERRIDES_KEY = "refresh_rate_overrides"
private const val ENABLED_KEY = "refresh_rate_enabled"
private const val GAME_OVERRIDES_KEY = "refresh_rate_game_overrides"
private const val APPLY_TIMEOUT_MS = 3000L
private const val DEFAULT_REFRESH_RATE_HZ = 60

/**
 * Foreground refresh policy for rooted Xiaomi/POCO devices.
 *
 * Android's public API lets an app request a preferred display mode, but it
 * does not expose a public "set package X to 120 Hz" switch. Kynox therefore
 * combines the AOSP user-preferred display mode with the display settings used
 * by MIUI, then verifies the *active* display mode instead of trusting only
 * peak_refresh_rate. This distinction matters on games: MIUI can leave the
 * setting at 120 while SurfaceFlinger is actually running the panel at 60.
 */
class RefreshRateRepository(
    private val context: Context,
    private val rootExecutor: RootExecutor,
    private val backupStore: BackupStore,
    private val logRepository: LogRepository
) {

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
        val displayCommand = mode?.let {
            "cmd display set-user-preferred-display-mode ${it.physicalWidth} ${it.physicalHeight} ${it.refreshRate} 0"
        } ?: "true"

        // Never let content frame-rate matching pull a forced 120 Hz policy back
        // to 60 Hz simply because a game is rendering at 60 fps. AOSP exposes
        // preference 0 as MATCH_CONTENT_FRAMERATE_NEVER.
        val matchContentCommand = "cmd display set-match-content-frame-rate-pref 0"
        // Android's GameManager has a frame-rate intervention for game apps.
        // On Android 13+ this can influence the active display mode as well.
        // Use it only when the selected package is actually categorized as a
        // game; normal apps keep the pure display-mode path.
        val gameFpsCommand = if (target > defaultRefreshRate() && isGamePackage(packageName)) {
            "cmd game set --mode 2 --fps $target ${packageName!!.replace("'", "")}"
        } else {
            "true"
        }

        val result = rootExecutor.execute(
            "$matchContentCommand; $gameFpsCommand; $displayCommand; " +
                "settings put system peak_refresh_rate $target; " +
                "settings put system min_refresh_rate $target; " +
                "settings put system user_refresh_rate $target; " +
                "settings put secure user_refresh_rate $target; " +
                "settings put secure miui_refresh_rate $target",
            APPLY_TIMEOUT_MS
        )

        // Display mode changes are asynchronous. Give SurfaceFlinger a short
        // window before reading the active mode, otherwise a valid request can
        // be reported as failed while the hardware switch is still pending.
        delay(180)
        val active = readActiveDisplayRefreshRate()
        val setting = readPeakRefreshRate()
        val verified = result.isSuccess && (active == target || (active == null && setting == target))

        if (verified && target > defaultRefreshRate() && isGamePackage(packageName)) {
            markGameOverride(packageName!!, true)
        }

        if (recordLog) {
            logRepository.append(
                LogEntry(
                    timestamp = System.currentTimeMillis(),
                    action = "APPLY_REFRESH_RATE",
                    target = packageName ?: "(default)",
                    previousValue = null,
                    newValue = "$target Hz",
                    result = if (verified) "SUCCESS" else "FAILED",
                    error = when {
                        !result.isSuccess -> result.stdout.ifBlank { "command failed" }
                        active != null && active != target -> "system reports active ${active}Hz after requesting ${target}Hz"
                        setting != target -> "peak_refresh_rate reports ${setting ?: "?"}Hz"
                        else -> null
                    }
                )
            )
        }
        verified
    }

    private fun supportedDisplayModeFor(targetHz: Int): android.view.Display.Mode? {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        @Suppress("DEPRECATION")
        val display = windowManager?.defaultDisplay ?: return null
        return display.supportedModes
            .filter { it.refreshRate > 0f }
            .minByOrNull { kotlin.math.abs(it.refreshRate - targetHz) }
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
    /** The value Android/MIUI says is active in the display service right now. */
    suspend fun readActiveDisplayRefreshRate(): Int? = withContext(Dispatchers.IO) {
        val result = rootExecutor.execute(
            "dumpsys display | grep -E 'mActiveSfDisplayMode|renderFrameRate' | head -n 4",
            APPLY_TIMEOUT_MS
        )
        if (!result.isSuccess) return@withContext null
        val text = result.stdout
        val refresh = Regex("refreshRate=([0-9]+(?:\\.[0-9]+)?)").find(text)?.groupValues?.get(1)
            ?: Regex("renderFrameRate\\s+([0-9]+(?:\\.[0-9]+)?)").find(text)?.groupValues?.get(1)
        refresh?.toFloatOrNull()?.roundToInt()
    }

    /** Reads the global setting only as a fallback/diagnostic. */
    suspend fun currentAppliedHz(): Int? = withContext(Dispatchers.IO) { readPeakRefreshRate() }

    private suspend fun readPeakRefreshRate(): Int? = withContext(Dispatchers.IO) {
        rootExecutor.execute("settings get system peak_refresh_rate", APPLY_TIMEOUT_MS)
            .stdout.trim().toFloatOrNull()?.roundToInt()
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
