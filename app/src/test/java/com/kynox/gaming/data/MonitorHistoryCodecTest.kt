package com.kynox.gaming.data

import com.kynox.gaming.data.monitor.MonitorHistoryCodec
import com.kynox.gaming.domain.model.MonitorSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MonitorHistoryCodecTest {
    private val sample = MonitorSample(1_700_000_000_000L, 42.5f, 1800f, 45.2f, null, null, 61f, 850f, 3.4f, 33.1f)

    @Test fun roundTripKeepsValuesAndNulls() {
        val decoded = MonitorHistoryCodec.decode(MonitorHistoryCodec.encode(sample))!!
        assertEquals(sample.tMs, decoded.tMs)
        assertEquals(42.5f, decoded.cpuUsage!!, 0.01f)
        assertNull(decoded.gpuUsage)
        assertNull(decoded.gpuFreqMhz)
        assertEquals(3.4f, decoded.powerWatts!!, 0.01f)
    }

    @Test fun nonFiniteValuesAreStoredAsMissing() {
        val decoded = MonitorHistoryCodec.decode(MonitorHistoryCodec.encode(sample.copy(cpuUsage = Float.NaN)))!!
        assertNull(decoded.cpuUsage)
    }

    @Test fun corruptLinesAreSkipped() {
        assertNull(MonitorHistoryCodec.decode(""))
        assertNull(MonitorHistoryCodec.decode("1,2,3"))
        assertNull(MonitorHistoryCodec.decode("abc,,,,,,,,,"))
    }

    @Test fun evenlySpacedKeepsEndsAndRespectsMax() {
        val items = (0 until 1000).toList()
        val picked = MonitorHistoryCodec.evenlySpaced(items, 100)
        assertEquals(100, picked.size)
        assertEquals(0, picked.first())
        assertEquals(999, picked.last())
        assertEquals(items, MonitorHistoryCodec.evenlySpaced(items, 5000))
        assertEquals(listOf(999), MonitorHistoryCodec.evenlySpaced(items, 1))
    }

    @Test fun trimDropsOldAndCorruptThenEnforcesSizeFromTheOldestSide() {
        val lines = listOf("100,a", "200,b", "300,c", "400,d", "garbage")
        assertEquals(listOf("300,c", "400,d"), MonitorHistoryCodec.trim(lines, 250, 1000))
        // Setiap baris 5 karakter + newline = 6 byte; batas 12 menyisakan dua baris terbaru.
        assertEquals(listOf("300,c", "400,d"), MonitorHistoryCodec.trim(lines, 0, 12))
    }
}
