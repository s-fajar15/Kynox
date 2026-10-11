package com.kynox.gaming.domain.model

/** Metrik yang bisa ditampilkan overlay cepat. [id] disimpan di pengaturan, jangan diubah. */
enum class OverlayMetric(val id: String, val label: String) {
    FPS("FPS", "FPS"),
    CPU("CPU", "CPU"),
    GPU("GPU", "GPU"),
    CPU_TEMP("CPU_TEMP", "TEMP"),
    RAM("RAM", "RAM"),
    POWER("POWER", "PWR"),
    BATTERY_TEMP("BATTERY_TEMP", "BAT")
}

const val DEFAULT_OVERLAY_ORDER = "FPS,CPU,GPU,CPU_TEMP,RAM,POWER,BATTERY_TEMP"

/** Urutan bawaan semua metrik. */
val defaultOverlayOrder: List<OverlayMetric> = OverlayMetric.values().toList()

/**
 * Membaca urutan dari teks tersimpan: id tak dikenal dan duplikat dibuang,
 * metrik yang belum ada ditambahkan di belakang sesuai urutan bawaan.
 */
fun parseOverlayOrder(raw: String?): List<OverlayMetric> {
    val result = LinkedHashSet<OverlayMetric>()
    raw.orEmpty().split(',').forEach { part ->
        val id = part.trim()
        OverlayMetric.values().firstOrNull { it.id == id }?.let { result.add(it) }
    }
    defaultOverlayOrder.forEach { result.add(it) }
    return result.toList()
}

fun serializeOverlayOrder(order: List<OverlayMetric>): String = order.joinToString(",") { it.id }

/** Menggeser [metric] sebanyak [delta] posisi (−1 = naik, +1 = turun); di ujung tidak berubah. */
fun moveInOrder(order: List<OverlayMetric>, metric: OverlayMetric, delta: Int): List<OverlayMetric> {
    val from = order.indexOf(metric)
    if (from < 0) return order
    val to = (from + delta).coerceIn(0, order.lastIndex)
    if (to == from) return order
    val mutable = order.toMutableList()
    mutable.removeAt(from)
    mutable.add(to, metric)
    return mutable
}

/** Metrik yang aktif, sudah berurutan sesuai pilihan pengguna (Settings). */
data class OverlaySettings(val metrics: List<OverlayMetric> = listOf(
    OverlayMetric.FPS, OverlayMetric.CPU, OverlayMetric.GPU, OverlayMetric.BATTERY_TEMP
)) {
    fun has(metric: OverlayMetric): Boolean = metric in metrics
    val showFps: Boolean get() = has(OverlayMetric.FPS)
}

/** One tick of live data for the standalone overlay -- independent from a recorded [SessionReport]. */
data class QuickOverlayMetrics(
    val fps: Float?,
    val cpuUsagePercent: Float?,
    val gpuUsagePercent: Float?,
    val gpuFreqRaw: Int?,
    val batteryTempCelsius: Float?,
    val cpuTempCelsius: Float? = null,
    val ramPercent: Float? = null,
    val powerWatts: Float? = null
)
