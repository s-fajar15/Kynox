package com.kynox.gaming.widget

/** Pemformatan teks widget, dipisah agar bisa diuji tanpa Android. */
object WidgetFormat {

    fun percent(value: Int?): String = if (value == null || value < 0) "--" else "$value%"

    /** Suhu dari baterai dalam persepuluh derajat Celsius (cara Android melaporkannya). */
    fun temperature(tenthsCelsius: Int?, fahrenheit: Boolean): String {
        if (tenthsCelsius == null || tenthsCelsius == Int.MIN_VALUE) return "--"
        val c = tenthsCelsius / 10f
        return if (fahrenheit) String.format("%.0f°F", c * 9f / 5f + 32f) else String.format("%.1f°C", c)
    }

    fun batteryPercent(level: Int, scale: Int): Int? =
        if (level < 0 || scale <= 0) null else (level * 100f / scale).toInt().coerceIn(0, 100)

    fun ramPercent(availBytes: Long, totalBytes: Long): Int? =
        if (totalBytes <= 0L) null else (((totalBytes - availBytes) * 100f) / totalBytes).toInt().coerceIn(0, 100)

    /** Status pengisian dari BatteryManager.EXTRA_STATUS (2 mengisi, 3 tidak mengisi daya terpakai, 4 tidak mengisi, 5 penuh). */
    fun chargeStatus(status: Int, plugged: Boolean): String = when (status) {
        2 -> "MENGISI"
        5 -> "PENUH"
        3 -> if (plugged) "TIDAK MENGISI" else "TERPAKAI"
        4 -> "TIDAK MENGISI"
        else -> if (plugged) "TERHUBUNG" else "TERPAKAI"
    }

    /** Status pengisian untuk tampilan widget: huruf biasa ("Mengisi"), tanpa all-caps. */
    fun chargeStatusLabel(status: Int, plugged: Boolean): String =
        chargeStatus(status, plugged).lowercase().replaceFirstChar { it.uppercase() }

    /**
     * Nilai arus dari BatteryManager (CURRENT_NOW) dalam mikroampere. Sebagian perangkat melaporkan
     * miliampere; nilai absolut di bawah 20.000 diperlakukan sebagai miliampere. Tanda diabaikan
     * karena berbeda antar perangkat.
     */
    fun currentMilliAmps(raw: Long?): Int? {
        if (raw == null || raw == Long.MIN_VALUE || raw == 0L) return null
        val a = Math.abs(raw)
        return (if (a < 20_000L) a else a / 1000L).toInt()
    }

    fun milliAmps(value: Int?): String = if (value == null) "--" else "$value mA"

    fun voltage(milliVolts: Int?): String =
        if (milliVolts == null || milliVolts <= 0) "--" else String.format("%.2f V", milliVolts / 1000f)

    fun watts(milliVolts: Int?, milliAmps: Int?): String {
        if (milliVolts == null || milliVolts <= 0 || milliAmps == null) return "--"
        return String.format("%.1f W", milliVolts / 1000f * milliAmps / 1000f)
    }
}
