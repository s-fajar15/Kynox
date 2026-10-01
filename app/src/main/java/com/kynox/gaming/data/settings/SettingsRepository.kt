package com.kynox.gaming.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "kynox_settings")

enum class ThemeMode { DARK, LIGHT, SYSTEM }
enum class TemperatureUnit { CELSIUS, FAHRENHEIT }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val refreshIntervalMs: Long = 1500L,
    val temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
    val applyOnBoot: Boolean = false,
    val requireConfirmation: Boolean = true,
    val loggingEnabled: Boolean = true,
    val chargingNotification: Boolean = false,
    val chargeLimitEnabled: Boolean = false,
    val chargeLimitPercent: Int = 80,
    val overlayShowFps: Boolean = true,
    val overlayShowCpu: Boolean = true,
    val overlayShowGpu: Boolean = true,
    val overlayShowBatteryTemp: Boolean = true,
    val overlayScalePercent: Int = 100,
    val overlayOpacityPercent: Int = 95,
    val monitorEnabled: Boolean = true,
    /** Consecutive boots where a previous apply-on-boot cycle never confirmed it finished safely. */
    val bootFailureCount: Int = 0,
    /** Set automatically after [MAX_CONSECUTIVE_BOOT_FAILURES] suspected boot failures. While true, ProfileBootReceiver never applies a profile, regardless of applyOnBoot. */
    val safeModeActive: Boolean = false
)

/** Consecutive unresolved boots after which apply-on-boot auto-disables and Safe Mode engages. */
const val MAX_CONSECUTIVE_BOOT_FAILURES = 2

const val OVERLAY_SCALE_MIN = 60
const val OVERLAY_SCALE_MAX = 200
const val OVERLAY_OPACITY_MIN = 20

class SettingsRepository(private val context: Context) {

    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val REFRESH_MS = intPreferencesKey("refresh_interval_ms")
        val TEMP_UNIT = stringPreferencesKey("temperature_unit")
        val APPLY_ON_BOOT = booleanPreferencesKey("apply_on_boot")
        val CONFIRM = booleanPreferencesKey("require_confirmation")
        val LOGGING = booleanPreferencesKey("logging_enabled")
        val CHARGING_NOTIFICATION = booleanPreferencesKey("charging_notification")
        val CHARGE_LIMIT_ENABLED = booleanPreferencesKey("charge_limit_enabled")
        val CHARGE_LIMIT_PERCENT = intPreferencesKey("charge_limit_percent")
        val OVERLAY_SHOW_FPS = booleanPreferencesKey("overlay_show_fps")
        val OVERLAY_SHOW_CPU = booleanPreferencesKey("overlay_show_cpu")
        val OVERLAY_SHOW_GPU = booleanPreferencesKey("overlay_show_gpu")
        val OVERLAY_SHOW_BATTERY_TEMP = booleanPreferencesKey("overlay_show_battery_temp")
        val OVERLAY_SCALE = intPreferencesKey("overlay_scale_percent")
        val OVERLAY_OPACITY = intPreferencesKey("overlay_opacity_percent")
        val MONITOR_ENABLED = booleanPreferencesKey("monitor_enabled")
        val BOOT_FAILURE_COUNT = intPreferencesKey("boot_failure_count")
        val SAFE_MODE_ACTIVE = booleanPreferencesKey("safe_mode_active")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            themeMode = prefs[Keys.THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.DARK,
            refreshIntervalMs = (prefs[Keys.REFRESH_MS] ?: 1500).toLong(),
            temperatureUnit = prefs[Keys.TEMP_UNIT]?.let { runCatching { TemperatureUnit.valueOf(it) }.getOrNull() } ?: TemperatureUnit.CELSIUS,
            applyOnBoot = prefs[Keys.APPLY_ON_BOOT] ?: false,
            requireConfirmation = prefs[Keys.CONFIRM] ?: true,
            loggingEnabled = prefs[Keys.LOGGING] ?: true,
            chargingNotification = prefs[Keys.CHARGING_NOTIFICATION] ?: false,
            chargeLimitEnabled = prefs[Keys.CHARGE_LIMIT_ENABLED] ?: false,
            chargeLimitPercent = prefs[Keys.CHARGE_LIMIT_PERCENT] ?: 80,
            overlayShowFps = prefs[Keys.OVERLAY_SHOW_FPS] ?: true,
            overlayShowCpu = prefs[Keys.OVERLAY_SHOW_CPU] ?: true,
            overlayShowGpu = prefs[Keys.OVERLAY_SHOW_GPU] ?: true,
            overlayShowBatteryTemp = prefs[Keys.OVERLAY_SHOW_BATTERY_TEMP] ?: true,
            overlayScalePercent = (prefs[Keys.OVERLAY_SCALE] ?: 100).coerceIn(OVERLAY_SCALE_MIN, OVERLAY_SCALE_MAX),
            overlayOpacityPercent = (prefs[Keys.OVERLAY_OPACITY] ?: 95).coerceIn(OVERLAY_OPACITY_MIN, 100),
            monitorEnabled = prefs[Keys.MONITOR_ENABLED] ?: true,
            bootFailureCount = prefs[Keys.BOOT_FAILURE_COUNT] ?: 0,
            safeModeActive = prefs[Keys.SAFE_MODE_ACTIVE] ?: false
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME] = mode.name }
    }

    suspend fun setRefreshInterval(ms: Long) {
        context.dataStore.edit { it[Keys.REFRESH_MS] = ms.toInt() }
    }

    suspend fun setTemperatureUnit(unit: TemperatureUnit) {
        context.dataStore.edit { it[Keys.TEMP_UNIT] = unit.name }
    }

    suspend fun setApplyOnBoot(enabled: Boolean) {
        context.dataStore.edit { it[Keys.APPLY_ON_BOOT] = enabled }
    }

    suspend fun setRequireConfirmation(enabled: Boolean) {
        context.dataStore.edit { it[Keys.CONFIRM] = enabled }
    }

    suspend fun setLoggingEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.LOGGING] = enabled }
    }

    suspend fun setChargingNotification(enabled: Boolean) {
        context.dataStore.edit { it[Keys.CHARGING_NOTIFICATION] = enabled }
    }

    suspend fun setChargeLimit(enabled: Boolean, percent: Int) {
        context.dataStore.edit {
            it[Keys.CHARGE_LIMIT_ENABLED] = enabled
            it[Keys.CHARGE_LIMIT_PERCENT] = percent
        }
    }

    suspend fun setOverlayMetric(showFps: Boolean, showCpu: Boolean, showGpu: Boolean, showBatteryTemp: Boolean) {
        context.dataStore.edit {
            it[Keys.OVERLAY_SHOW_FPS] = showFps
            it[Keys.OVERLAY_SHOW_CPU] = showCpu
            it[Keys.OVERLAY_SHOW_GPU] = showGpu
            it[Keys.OVERLAY_SHOW_BATTERY_TEMP] = showBatteryTemp
        }
    }

    suspend fun setOverlayStyle(scalePercent: Int, opacityPercent: Int) {
        context.dataStore.edit {
            it[Keys.OVERLAY_SCALE] = scalePercent.coerceIn(OVERLAY_SCALE_MIN, OVERLAY_SCALE_MAX)
            it[Keys.OVERLAY_OPACITY] = opacityPercent.coerceIn(OVERLAY_OPACITY_MIN, 100)
        }
    }

    suspend fun setMonitorEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.MONITOR_ENABLED] = enabled }
    }

    /**
     * Called by [com.kynox.gaming.boot.ProfileBootReceiver] once, near the
     * very start of BOOT_COMPLETED handling, before applying anything.
     * Returns the count read BEFORE this increment, so the caller can tell
     * whether the PREVIOUS boot ever reached [clearBootFailure] -- if it
     * didn't, this is (at best) a second unresolved boot in a row.
     * Also auto-engages Safe Mode and turns Apply on Boot off once the
     * threshold is crossed, so a bootloop cannot repeat indefinitely.
     */
    suspend fun registerBootAttempt(): Int {
        var previousCount = 0
        context.dataStore.edit { prefs ->
            previousCount = prefs[Keys.BOOT_FAILURE_COUNT] ?: 0
            val next = previousCount + 1
            prefs[Keys.BOOT_FAILURE_COUNT] = next
            if (next >= MAX_CONSECUTIVE_BOOT_FAILURES) {
                prefs[Keys.SAFE_MODE_ACTIVE] = true
                prefs[Keys.APPLY_ON_BOOT] = false
            }
        }
        return previousCount
    }

    /** Called once a boot-time apply cycle has run to completion (success OR a clean, handled failure) without the process being killed -- proves this boot is not looping. */
    suspend fun clearBootFailure() {
        context.dataStore.edit { it[Keys.BOOT_FAILURE_COUNT] = 0 }
    }

    /** Manual escape hatch, surfaced in Settings: clears the failure counter and Safe Mode. Apply on Boot stays off until the user turns it back on deliberately. */
    suspend fun exitSafeMode() {
        context.dataStore.edit {
            it[Keys.SAFE_MODE_ACTIVE] = false
            it[Keys.BOOT_FAILURE_COUNT] = 0
        }
    }

    suspend fun resetAll() {
        context.dataStore.edit { it.clear() }
    }
}
