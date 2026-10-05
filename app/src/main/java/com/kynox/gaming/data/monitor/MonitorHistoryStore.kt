package com.kynox.gaming.data.monitor

import android.content.Context
import com.kynox.gaming.core.utils.Logger
import com.kynox.gaming.domain.model.MonitorSample
import java.io.File

private const val TAG = "MonitorHistoryStore"
private const val FILE_NAME = "monitor_history.csv"
private const val PRUNE_EVERY_APPENDS = 500

/** Batas keras ukuran berkas riwayat. Jika terlampaui, sampel tertua dibuang lebih dulu. */
const val HISTORY_MAX_BYTES = 3L * 1024 * 1024

/** Format satu baris riwayat: `t,cpu,freq,cpuTemp,gpu,gpuFreq,ram,mA,W,battTemp`; nilai kosong = tidak tersedia. */
object MonitorHistoryCodec {
    fun encode(s: MonitorSample): String {
        fun f(v: Float?): String = if (v == null || v.isNaN() || v.isInfinite()) "" else "%.2f".format(java.util.Locale.US, v)
        return listOf(
            s.tMs.toString(), f(s.cpuUsage), f(s.cpuFreqMhz), f(s.cpuTemp), f(s.gpuUsage),
            f(s.gpuFreqMhz), f(s.ramUsage), f(s.currentMa), f(s.powerWatts), f(s.batteryTemp)
        ).joinToString(",")
    }

    /** Mengembalikan null untuk baris rusak (terpotong saat proses mati, dsb.) agar bisa dilewati. */
    fun decode(line: String): MonitorSample? {
        val parts = line.split(',')
        if (parts.size != 10) return null
        val t = parts[0].toLongOrNull() ?: return null
        fun v(i: Int): Float? = parts[i].takeIf { it.isNotEmpty() }?.toFloatOrNull()
        return MonitorSample(t, v(1), v(2), v(3), v(4), v(5), v(6), v(7), v(8), v(9))
    }

    /** Memilih paling banyak [max] elemen dengan jarak merata, selalu menyertakan yang terakhir. */
    fun <T> evenlySpaced(items: List<T>, max: Int): List<T> {
        if (max <= 0) return emptyList()
        if (items.size <= max) return items
        if (max == 1) return listOf(items.last())
        val out = ArrayList<T>(max)
        val step = (items.size - 1).toDouble() / (max - 1)
        for (i in 0 until max) out.add(items[Math.round(i * step).toInt().coerceIn(0, items.size - 1)])
        return out
    }

    /** Hapus yang lebih tua dari [cutoffMs], lalu buang yang tertua sampai total byte (perkiraan) ≤ [maxBytes]. */
    fun trim(lines: List<String>, cutoffMs: Long, maxBytes: Long): List<String> {
        val fresh = lines.filter { line ->
            val t = line.substringBefore(',').toLongOrNull()
            t != null && t >= cutoffMs
        }
        var total = fresh.sumOf { it.length + 1L }
        var drop = 0
        while (total > maxBytes && drop < fresh.size) {
            total -= fresh[drop].length + 1L
            drop++
        }
        return fresh.drop(drop)
    }
}

data class HistoryStats(val count: Int, val sizeBytes: Long, val oldestMs: Long?)

/**
 * Riwayat monitor yang bertahan setelah aplikasi ditutup. Berbeda dari buffer
 * 30 menit di [MonitorRecorder]: sampel disimpan dengan selang yang dipilih
 * pengguna (bukan setiap tick), dibuang menurut umur, dan ukurannya dibatasi
 * [HISTORY_MAX_BYTES]. Semua operasi aman dipanggil dari thread IO mana pun.
 */
class MonitorHistoryStore(context: Context) {
    private val file = File(context.filesDir, FILE_NAME)
    private val lock = Any()
    private var lastAppendMs = 0L
    private var appendsSincePrune = PRUNE_EVERY_APPENDS

    /** Menyimpan [sample] bila sudah lewat [intervalMs] sejak penyimpanan terakhir. Mengembalikan true bila disimpan. */
    fun append(sample: MonitorSample, intervalMs: Long, retentionMs: Long): Boolean = synchronized(lock) {
        if (sample.tMs - lastAppendMs < intervalMs) return false
        try {
            if (appendsSincePrune >= PRUNE_EVERY_APPENDS) {
                pruneLocked(sample.tMs - retentionMs)
                appendsSincePrune = 0
            }
            file.appendText(MonitorHistoryCodec.encode(sample) + "\n")
            lastAppendMs = sample.tMs
            appendsSincePrune++
            true
        } catch (t: Throwable) {
            Logger.e(TAG, "Could not append monitor history", t)
            false
        }
    }

    /** Sampel dengan waktu ≥ [sinceMs], urut naik. */
    fun read(sinceMs: Long): List<MonitorSample> = synchronized(lock) {
        try {
            if (!file.exists()) return emptyList()
            file.readLines().mapNotNull { MonitorHistoryCodec.decode(it) }.filter { it.tMs >= sinceMs }
        } catch (t: Throwable) {
            Logger.e(TAG, "Could not read monitor history", t)
            emptyList()
        }
    }

    fun stats(): HistoryStats = synchronized(lock) {
        try {
            if (!file.exists()) return HistoryStats(0, 0L, null)
            val lines = file.readLines()
            HistoryStats(lines.size, file.length(), lines.firstOrNull()?.substringBefore(',')?.toLongOrNull())
        } catch (t: Throwable) {
            HistoryStats(0, 0L, null)
        }
    }

    fun clear() {
        synchronized(lock) {
            try {
                file.delete()
            } catch (t: Throwable) {
                Logger.e(TAG, "Could not clear monitor history", t)
            }
            lastAppendMs = 0L
            appendsSincePrune = PRUNE_EVERY_APPENDS
        }
    }

    private fun pruneLocked(cutoffMs: Long) {
        if (!file.exists()) return
        val kept = MonitorHistoryCodec.trim(file.readLines(), cutoffMs, HISTORY_MAX_BYTES * 8 / 10)
        val tmp = File(file.parentFile, "$FILE_NAME.tmp")
        tmp.writeText(if (kept.isEmpty()) "" else kept.joinToString("\n") + "\n")
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }
}
