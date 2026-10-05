package com.kynox.gaming.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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
    /** Peringatan suhu (notifikasi). */
    val notifyThermal: Boolean = true,
    /** Ambang peringatan suhu dalam °C; [THERMAL_WARN_AUTO] = otomatis dari titik trip sensor. */
    val thermalWarnThresholdC: Int = THERMAL_WARN_AUTO,
    /** Jeda minimum antar notifikasi sejenis, dalam menit. */
    val notifyCooldownMin: Int = 5,
    /** Notifikasi "profil diterapkan / dikembalikan". */
    val notifyProfileApplied: Boolean = true,
    /** Simpan riwayat monitor ke penyimpanan lokal. */
    val historyEnabled: Boolean = true,
    /** Selang antar sampel yang disimpan ke riwayat, detik. */
    val historyIntervalSec: Int = 30,
    /** Berapa jam riwayat dipertahankan. */
    val historyRetentionHours: Int = 24,
    /** Consecutive boots where a previous apply-on-boot cycle never confirmed it finished safely. */
    val bootFailureCount: Int = 0,
    /** Set automatically after [MAX_CONSECUTIVE_BOOT_FAILURES] suspected boot failures. While true, ProfileBootReceiver never applies a profile, regardless of applyOnBoot. */
    val safeModeActive: Boolean = false
)

/** Consecutive unresolved boots after which apply-on-boot auto-disables and Safe Mode engages. */
const val MAX_CONSECUTIVE_BOOT_FAILURES = 2

const val THERMAL_WARN_AUTO = 0
val THERMAL_WARN_OPTIONS = listOf(THERMAL_WARN_AUTO, 42, 45, 48, 50, 55)
val NOTIFY_COOLDOWN_OPTIONS_MIN = listOf(1, 5, 15, 30)
val HISTORY_INTERVAL_OPTIONS_SEC = listOf(10, 30, 60, 300)
val HISTORY_RETENTION_OPTIONS_HOURS = listOf(1, 6, 24, 72)

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
        val NOTIFY_THERMAL = booleanPreferencesKey("notify_thermal")
        val THERMAL_WARN_C = intPreferencesKey("thermal_warn_threshold_c")
        val NOTIFY_COOLDOWN = intPreferencesKey("notify_cooldown_min")
        val NOTIFY_PROFILE = booleanPreferencesKey("notify_profile_applied")
        val HISTORY_ENABLED = booleanPreferencesKey("history_enabled")
        val HISTORY_INTERVAL = intPreferencesKey("history_interval_sec")
        val HISTORY_RETENTION = intPreferencesKey("history_retention_hours")
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
            notifyThermal = prefs[Keys.NOTIFY_THERMAL] ?: true,
            thermalWarnThresholdC = (prefs[Keys.THERMAL_WARN_C] ?: THERMAL_WARN_AUTO).let { if (it == THERMAL_WARN_AUTO) it else it.coerceIn(35, 80) },
            notifyCooldownMin = (prefs[Keys.NOTIFY_COOLDOWN] ?: 5).coerceIn(1, 120),
            notifyProfileApplied = prefs[Keys.NOTIFY_PROFILE] ?: true,
            historyEnabled = prefs[Keys.HISTORY_ENABLED] ?: true,
            historyIntervalSec = (prefs[Keys.HISTORY_INTERVAL] ?: 30).coerceIn(5, 3600),
            historyRetentionHours = (prefs[Keys.HISTORY_RETENTION] ?: 24).coerceIn(1, 168),
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

    /** Pengaturan yang ikut dicadangkan (lihat [PortableSettings]). */
    suspend fun exportPortable(): Map<String, Any> = PortableSettings.export(settingsFlow.first())

    /** Menulis nilai hasil [PortableSettings.sanitize]; mengembalikan jumlah yang diterapkan. */
    suspend fun importPortable(raw: Map<String, Any?>): Int {
        val clean = PortableSettings.sanitize(raw)
        context.dataStore.edit { prefs ->
            clean.forEach { (key, value) ->
                when (value) {
                    is Boolean -> prefs[booleanPreferencesKey(key)] = value
                    is Int -> prefs[intPreferencesKey(key)] = value
                    is String -> prefs[stringPreferencesKey(key)] = value
                }
            }
        }
        return clean.size
    }

    suspend fun setNotifications(thermal: Boolean, thresholdC: Int, cooldownMin: Int, profileApplied: Boolean) {
        context.dataStore.edit {
            it[Keys.NOTIFY_THERMAL] = thermal
            it[Keys.THERMAL_WARN_C] = thresholdC
            it[Keys.NOTIFY_COOLDOWN] = cooldownMin
            it[Keys.NOTIFY_PROFILE] = profileApplied
        }
    }

    suspend fun setHistory(enabled: Boolean, intervalSec: Int, retentionHours: Int) {
        context.dataStore.edit {
            it[Keys.HISTORY_ENABLED] = enabled
            it[Keys.HISTORY_INTERVAL] = intervalSec
            it[Keys.HISTORY_RETENTION] = retentionHours
        }
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
