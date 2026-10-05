package com.kynox.gaming.data.notify

/** Batas bawah/atas sensor yang masih dianggap masuk akal untuk peringatan suhu. */
private const val FALLBACK_THRESHOLD_C = 47f
private const val TRIP_MARGIN_C = 5f

/** Selisih turun dari ambang sebelum zona dianggap sudah dingin dan boleh memperingatkan lagi. */
const val THERMAL_RESET_MARGIN_C = 5f

/**
 * Ambang peringatan suhu yang dipakai untuk satu zona.
 * [setting] = 0 berarti otomatis: 5°C di bawah titik trip terendah milik zona itu,
 * atau 47°C bila zona tidak punya titik trip. Selain itu pakai angka pilihan pengguna.
 */
fun thermalWarnThreshold(setting: Int, lowestTripC: Float?): Float = when {
    setting > 0 -> setting.toFloat()
    lowestTripC != null -> lowestTripC - TRIP_MARGIN_C
    else -> FALLBACK_THRESHOLD_C
}

/**
 * Pembatas frekuensi notifikasi: satu kunci hanya boleh lolos sekali per
 * [cooldownMs]. Dipisah dari Android agar bisa diuji sebagai unit test biasa.
 */
class NotifyThrottle(private val clock: () -> Long = { System.currentTimeMillis() }) {
    private val last = HashMap<String, Long>()

    @Synchronized
    fun tryAcquire(key: String, cooldownMs: Long): Boolean {
        val now = clock()
        val previous = last[key]
        if (previous != null && now - previous < cooldownMs) return false
        last[key] = now
        return true
    }

    @Synchronized
    fun reset(key: String) {
        last.remove(key)
    }
}

/**
 * Menentukan kapan peringatan suhu boleh dikirim.
 *
 * - Satu sumber (mis. zona termal) hanya memperingatkan sekali sampai suhunya
 *   turun [THERMAL_RESET_MARGIN_C] di bawah ambang.
 * - Semua sumber berbagi satu pembatas waktu, jadi beberapa sensor yang panas
 *   bersamaan tidak menghasilkan banyak notifikasi.
 */
class ThermalWarner(private val throttle: NotifyThrottle) {
    private val warned = HashSet<String>()

    @Synchronized
    fun shouldWarn(source: String, temperatureC: Float, thresholdC: Float, cooldownMs: Long): Boolean {
        if (temperatureC < thresholdC - THERMAL_RESET_MARGIN_C) {
            warned.remove(source)
            return false
        }
        if (temperatureC < thresholdC || source in warned) return false
        if (!throttle.tryAcquire(THROTTLE_KEY, cooldownMs)) return false
        warned.add(source)
        return true
    }

    private companion object {
        const val THROTTLE_KEY = "thermal"
    }
}
