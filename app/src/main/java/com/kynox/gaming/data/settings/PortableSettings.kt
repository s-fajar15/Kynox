package com.kynox.gaming.data.settings

/**
 * Bagian pengaturan yang aman dibawa lewat cadangan. Sengaja tidak ikut:
 * "Terapkan saat boot", mode aman, batas pengisian, notifikasi pengisian dan
 * monitor selalu aktif. Semuanya menyalakan service atau mengubah perilaku
 * saat boot, jadi harus dinyalakan sendiri oleh pengguna di perangkat tujuan.
 *
 * Kunci memakai nama yang sama dengan kunci DataStore.
 */
object PortableSettings {

    fun export(s: AppSettings): Map<String, Any> = linkedMapOf(
        "theme_mode" to s.themeMode.name,
        "temperature_unit" to s.temperatureUnit.name,
        "refresh_interval_ms" to s.refreshIntervalMs.toInt(),
        "require_confirmation" to s.requireConfirmation,
        "logging_enabled" to s.loggingEnabled,
        "overlay_show_fps" to s.overlayShowFps,
        "overlay_show_cpu" to s.overlayShowCpu,
        "overlay_show_gpu" to s.overlayShowGpu,
        "overlay_show_battery_temp" to s.overlayShowBatteryTemp,
        "overlay_scale_percent" to s.overlayScalePercent,
        "overlay_opacity_percent" to s.overlayOpacityPercent,
        "notify_thermal" to s.notifyThermal,
        "thermal_warn_threshold_c" to s.thermalWarnThresholdC,
        "notify_cooldown_min" to s.notifyCooldownMin,
        "notify_profile_applied" to s.notifyProfileApplied,
        "history_enabled" to s.historyEnabled,
        "history_interval_sec" to s.historyIntervalSec,
        "history_retention_hours" to s.historyRetentionHours
    )

    private val booleanKeys = setOf(
        "require_confirmation", "logging_enabled", "overlay_show_fps", "overlay_show_cpu",
        "overlay_show_gpu", "overlay_show_battery_temp", "notify_thermal", "notify_profile_applied", "history_enabled"
    )

    private val intRanges: Map<String, IntRange> = mapOf(
        "refresh_interval_ms" to 500..5000,
        "overlay_scale_percent" to OVERLAY_SCALE_MIN..OVERLAY_SCALE_MAX,
        "overlay_opacity_percent" to OVERLAY_OPACITY_MIN..100,
        "notify_cooldown_min" to 1..120,
        "history_interval_sec" to 5..3600,
        "history_retention_hours" to 1..168
    )

    /**
     * Menyaring nilai dari berkas: kunci tak dikenal dan tipe yang salah dibuang,
     * angka dijepit ke rentang yang sah. Hasilnya aman ditulis langsung ke DataStore.
     */
    fun sanitize(raw: Map<String, Any?>): Map<String, Any> {
        val out = LinkedHashMap<String, Any>()
        for ((key, value) in raw) {
            when {
                key in booleanKeys -> if (value is Boolean) out[key] = value
                key == "theme_mode" -> (value as? String)?.takeIf { v -> ThemeMode.values().any { it.name == v } }?.let { out[key] = it }
                key == "temperature_unit" -> (value as? String)?.takeIf { v -> TemperatureUnit.values().any { it.name == v } }?.let { out[key] = it }
                key == "thermal_warn_threshold_c" -> asInt(value)?.let { out[key] = if (it <= 0) THERMAL_WARN_AUTO else it.coerceIn(35, 80) }
                key in intRanges -> asInt(value)?.let { out[key] = it.coerceIn(intRanges.getValue(key)) }
            }
        }
        return out
    }

    private fun asInt(value: Any?): Int? = when (value) {
        is Int -> value
        is Long -> value.toInt()
        is Number -> value.toDouble().takeIf { it == Math.rint(it) }?.toInt()
        else -> null
    }
}
