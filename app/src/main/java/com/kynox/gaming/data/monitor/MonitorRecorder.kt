package com.kynox.gaming.data.monitor

import android.content.Context
import com.kynox.gaming.core.utils.Logger
import com.kynox.gaming.core.utils.gpuFreqMhz
import com.kynox.gaming.data.battery.BatteryRepository
import com.kynox.gaming.data.cpu.CpuRepository
import com.kynox.gaming.data.device.DeviceInfoRepository
import com.kynox.gaming.data.gpu.GpuRepository
import com.kynox.gaming.data.thermal.ThermalRepository
import com.kynox.gaming.domain.model.MonitorSample
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import kotlin.math.abs

private const val TAG = "MonitorRecorder"
private const val HISTORY_FILE = "monitor_history.json"

/** How much history the always-on monitor keeps: enough for the longest chart window. */
const val MONITOR_HISTORY_MS = 30 * 60 * 1000L

private const val PERSIST_EVERY_MS = 60_000L

/**
 * The always-on monitor's data source. [com.kynox.gaming.service.MonitorService]
 * calls [sampleOnce] on a loop for as long as the phone is on, so the
 * Monitor tab shows history that already exists when it is opened instead
 * of starting empty. The last half hour is also saved to disk periodically,
 * so it survives the process being killed.
 */
class MonitorRecorder(
    context: Context,
    private val batteryRepository: BatteryRepository,
    private val cpuRepository: CpuRepository,
    private val gpuRepository: GpuRepository,
    private val thermalRepository: ThermalRepository,
    private val deviceInfoRepository: DeviceInfoRepository
) {
    private val historyFile = File(context.filesDir, HISTORY_FILE)
    private val buffer = ArrayList<MonitorSample>()
    private var lastPersistMs = 0L
    private var historyLoaded = false

    private val _samples = MutableStateFlow<List<MonitorSample>>(emptyList())
    val samples: StateFlow<List<MonitorSample>> = _samples.asStateFlow()

    private val _gpuSupported = MutableStateFlow(true)
    val gpuSupported: StateFlow<Boolean> = _gpuSupported.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    fun setRunning(value: Boolean) {
        _running.value = value
    }

    /** Reads whatever the previous run saved (only the part still inside the history window). */
    suspend fun loadHistory() = withContext(Dispatchers.IO) {
        synchronized(buffer) {
            if (historyLoaded) return@withContext
            historyLoaded = true
        }
        try {
            if (!historyFile.exists()) return@withContext
            val cutoff = System.currentTimeMillis() - MONITOR_HISTORY_MS
            val array = JSONArray(historyFile.readText())
            val restored = ArrayList<MonitorSample>()
            for (i in 0 until array.length()) {
                val row = array.getJSONArray(i)
                val t = row.getLong(0)
                if (t < cutoff) continue
                fun value(index: Int): Float? = if (row.isNull(index)) null else row.getDouble(index).toFloat()
                restored += MonitorSample(t, value(1), value(2), value(3), value(4), value(5), value(6), value(7), value(8), value(9))
            }
            synchronized(buffer) {
                buffer.addAll(0, restored)
                _samples.value = buffer.toList()
            }
        } catch (t: Throwable) {
            Logger.e(TAG, "Could not restore monitor history", t)
        }
    }

    suspend fun sampleOnce() = withContext(Dispatchers.IO) {
        val battery = batteryRepository.readInfo()
        val cpu = cpuRepository.readSnapshot()
        val gpu = gpuRepository.readState()
        val ram = deviceInfoRepository.readRamUsage()
        val cpuTemp = thermalRepository.findTemperatureFor(listOf("cpu", "soc", "cpuss", "apc", "cpu0", "tsens"))

        val avgFreqMhz = cpu.cores.mapNotNull { it.currentFreqKhz }
            .takeIf { it.isNotEmpty() }
            ?.average()?.toFloat()?.div(1000f)
        val now = System.currentTimeMillis()
        val sample = MonitorSample(
            tMs = now,
            cpuUsage = cpu.overallUsagePercent,
            cpuFreqMhz = avgFreqMhz,
            cpuTemp = cpuTemp,
            gpuUsage = gpu.utilizationPercent,
            gpuFreqMhz = gpu.currentFreqKhz?.let { gpuFreqMhz(it).toFloat() },
            ramUsage = ram.usedPercent,
            currentMa = battery.currentMicroAmps?.let { abs(it) / 1000f },
            powerWatts = battery.powerWatts,
            batteryTemp = battery.temperatureCelsius
        )
        _gpuSupported.value = gpu.supported

        val snapshot: List<MonitorSample>
        synchronized(buffer) {
            buffer.add(sample)
            val cutoff = now - MONITOR_HISTORY_MS
            while (buffer.isNotEmpty() && buffer.first().tMs < cutoff) buffer.removeAt(0)
            snapshot = buffer.toList()
        }
        _samples.value = snapshot
        if (now - lastPersistMs >= PERSIST_EVERY_MS) {
            lastPersistMs = now
            persist(snapshot)
        }
    }

    private fun persist(snapshot: List<MonitorSample>) {
        try {
            val array = JSONArray()
            snapshot.forEach { s ->
                array.put(
                    JSONArray().apply {
                        put(s.tMs)
                        listOf(s.cpuUsage, s.cpuFreqMhz, s.cpuTemp, s.gpuUsage, s.gpuFreqMhz, s.ramUsage, s.currentMa, s.powerWatts, s.batteryTemp)
                            .forEach { v -> if (v == null) put(org.json.JSONObject.NULL) else put(v.toDouble()) }
                    }
                )
            }
            historyFile.writeText(array.toString())
        } catch (t: Throwable) {
            Logger.e(TAG, "Could not save monitor history", t)
        }
    }
}
