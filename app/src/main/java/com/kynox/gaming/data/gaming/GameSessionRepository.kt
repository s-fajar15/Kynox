package com.kynox.gaming.data.gaming

import android.content.Context
import com.kynox.gaming.core.root.RootExecutor
import com.kynox.gaming.core.utils.Logger
import com.kynox.gaming.core.utils.gpuFreqMhz
import com.kynox.gaming.data.battery.BatteryRepository
import com.kynox.gaming.data.cpu.CpuRepository
import com.kynox.gaming.data.gpu.GpuRepository
import com.kynox.gaming.data.thermal.ThermalRepository
import com.kynox.gaming.domain.model.LiveMetrics
import com.kynox.gaming.domain.model.MINOR_DROP_RATIO
import com.kynox.gaming.domain.model.SEVERE_DROP_RATIO
import com.kynox.gaming.domain.model.SessionReport
import com.kynox.gaming.domain.model.SessionSample
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

private const val TAG = "GameSessionRepository"
private const val LEGACY_REPORT_FILE = "kynox_last_session.json"
private const val SESSIONS_DIR = "sessions"
private const val MAX_HISTORY = 30
private const val MAX_RESOLUTION_ATTEMPTS = 5
private const val MIN_DRAIN_SPAN_MS = 60_000L

class GameSessionRepository(
    private val context: Context,
    private val rootExecutor: RootExecutor,
    private val cpuRepository: CpuRepository,
    private val gpuRepository: GpuRepository,
    private val thermalRepository: ThermalRepository,
    private val batteryRepository: BatteryRepository
) {
    private val samples = mutableListOf<SessionSample>()
    private val fpsMeter = FpsMeter(rootExecutor)
    @Volatile private var packageName: String = ""
    @Volatile private var gameLabel: String = ""
    @Volatile private var startedAtMs: Long = 0L
    @Volatile private var resolution: String? = null
    @Volatile private var resolutionAttempts: Int = 0

    /** Wall-clock start of the current recording; only meaningful while [isRecording] is true. */
    fun recordingStartedAtMs(): Long = startedAtMs

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _live = MutableStateFlow(LiveMetrics())
    val live: StateFlow<LiveMetrics> = _live.asStateFlow()

    suspend fun begin(packageName: String, label: String) = withContext(Dispatchers.IO) {
        synchronized(samples) { samples.clear() }
        _live.value = LiveMetrics()
        this@GameSessionRepository.packageName = packageName
        this@GameSessionRepository.gameLabel = label
        startedAtMs = System.currentTimeMillis()
        resolution = null
        resolutionAttempts = 0
        fpsMeter.reset()
        _isRecording.value = true
    }

    suspend fun sampleOnce() = withContext(Dispatchers.IO) {
        if (!_isRecording.value) return@withContext
        try {
            val fps = readFps()
            if (resolution == null && fps != null && resolutionAttempts < MAX_RESOLUTION_ATTEMPTS) {
                resolutionAttempts++
                resolution = FpsReader.readResolution(packageName, rootExecutor)
            }
            val cpu = cpuRepository.readSnapshot()
            val gpu = gpuRepository.readState()
            val cpuTemp = thermalRepository.findTemperatureFor(listOf("cpu", "soc", "cpuss", "apc", "cpu0", "tsens"))
            val battery = batteryRepository.readInfo()
            val avgCpuFreq = cpu.cores.mapNotNull { it.currentFreqKhz }.let { freqs ->
                if (freqs.isEmpty()) null else freqs.sum() / freqs.size
            }
            val sample = SessionSample(
                elapsedMs = System.currentTimeMillis() - startedAtMs,
                fps = fps,
                avgCpuFreqKhz = avgCpuFreq,
                gpuFreqKhz = gpu.currentFreqKhz,
                cpuTempCelsius = cpuTemp,
                batteryTempCelsius = battery.temperatureCelsius,
                powerWatts = battery.powerWatts,
                batteryPercent = battery.capacityPercent,
                chargeCounterMicroAh = battery.chargeCounterMicroAh
            )
            synchronized(samples) { samples.add(sample) }
            _live.value = LiveMetrics(fps, cpuTemp, gpu.currentFreqKhz, battery.powerWatts)
        } catch (t: Throwable) {
            Logger.e(TAG, "Sample failed, skipping this tick", t)
        }
    }

    suspend fun finish(): SessionReport = withContext(Dispatchers.IO) {
        val duration = System.currentTimeMillis() - startedAtMs
        val recorded = synchronized(samples) { samples.toList() }
        val report = buildReport(
            packageName, gameLabel, startedAtMs, duration, recorded,
            recorded.any { it.fps != null }, resolution
        )
        persist(report)
        // Only flip the flag once the file exists, so anything reacting to
        // "recording stopped" by listing reports sees the new one.
        _isRecording.value = false
        _live.value = LiveMetrics()
        report
    }

    private suspend fun readFps(): Float? {
        return fpsMeter.sample(packageName)?.fps
    }

    // ---- History -------------------------------------------------------

    /** All saved sessions, newest first. */
    suspend fun listReports(): List<SessionReport> = withContext(Dispatchers.IO) {
        migrateLegacyReport()
        sessionFiles().mapNotNull { file ->
            try {
                parseReport(JSONObject(file.readText()))
            } catch (t: Throwable) {
                Logger.e(TAG, "Skipping unreadable session file ${file.name}", t)
                null
            }
        }.sortedByDescending { it.startedAtMs }
    }

    suspend fun reportCount(): Int = withContext(Dispatchers.IO) {
        migrateLegacyReport()
        sessionFiles().size
    }

    suspend fun loadReport(id: Long): SessionReport? = withContext(Dispatchers.IO) {
        val file = File(sessionsDir(), "$id.json")
        if (!file.exists()) return@withContext null
        try {
            parseReport(JSONObject(file.readText()))
        } catch (t: Throwable) {
            Logger.e(TAG, "Failed to load session $id", t)
            null
        }
    }

    suspend fun deleteReport(id: Long) = withContext(Dispatchers.IO) {
        File(sessionsDir(), "$id.json").delete()
        Unit
    }

    private fun sessionsDir(): File = File(context.filesDir, SESSIONS_DIR).also { it.mkdirs() }

    private fun sessionFiles(): List<File> =
        sessionsDir().listFiles { f -> f.isFile && f.name.endsWith(".json") }?.toList() ?: emptyList()

    /** Older versions kept a single "last session" file; fold it into the history once. */
    private fun migrateLegacyReport() {
        val legacy = File(context.filesDir, LEGACY_REPORT_FILE)
        if (!legacy.exists()) return
        try {
            persist(parseReport(JSONObject(legacy.readText())))
        } catch (t: Throwable) {
            Logger.e(TAG, "Could not migrate legacy session report", t)
        }
        legacy.delete()
    }

    private fun pruneHistory() {
        val files = sessionFiles().sortedByDescending { it.name.removeSuffix(".json").toLongOrNull() ?: 0L }
        files.drop(MAX_HISTORY).forEach { it.delete() }
    }

    // ---- Building / (de)serialising -------------------------------------

    private fun buildReport(
        pkg: String,
        label: String,
        startedAt: Long,
        duration: Long,
        samples: List<SessionSample>,
        fpsSupported: Boolean,
        resolution: String?
    ): SessionReport {
        val fpsValues = samples.mapNotNull { it.fps }
        val avgFps = fpsValues.average().toFloat().takeIf { fpsValues.isNotEmpty() }
        val maxFps = fpsValues.maxOrNull()
        val minFps = fpsValues.minOrNull()
        val low5 = fpsValues.sorted().let { sorted ->
            if (sorted.isEmpty()) null else {
                val count = (sorted.size * 0.05).toInt().coerceAtLeast(1)
                sorted.take(count).average().toFloat()
            }
        }
        val severeThreshold = (avgFps ?: 0f) * SEVERE_DROP_RATIO
        val minorThreshold = (avgFps ?: 0f) * MINOR_DROP_RATIO
        val severeCount = fpsValues.count { it < severeThreshold }
        val minorCount = fpsValues.count { it >= severeThreshold && it < minorThreshold }

        val cpuTemps = samples.mapNotNull { it.cpuTempCelsius }
        val batteryTemps = samples.mapNotNull { it.batteryTempCelsius }
        val powers = samples.mapNotNull { it.powerWatts }
        val cpuFreqsMhz = samples.mapNotNull { it.avgCpuFreqKhz?.let { khz -> khz / 1000f } }
        val gpuFreqsMhz = samples.mapNotNull { it.gpuFreqKhz?.let { raw -> gpuFreqMhz(raw).toFloat() } }

        val withBattery = samples.filter { it.batteryPercent != null }
        val firstBattery = withBattery.firstOrNull()
        val lastBattery = withBattery.lastOrNull()
        val batterySpanMs = (lastBattery?.elapsedMs ?: 0L) - (firstBattery?.elapsedMs ?: 0L)
        val hours = batterySpanMs / 3_600_000f
        val percentLost = if (firstBattery != null && lastBattery != null) {
            (firstBattery.batteryPercent ?: 0) - (lastBattery.batteryPercent ?: 0)
        } else 0
        val drainPercent = if (batterySpanMs >= MIN_DRAIN_SPAN_MS && percentLost >= 0) percentLost / hours else null

        val withCounter = samples.filter { it.chargeCounterMicroAh != null }
        val counterSpanMs = (withCounter.lastOrNull()?.elapsedMs ?: 0L) - (withCounter.firstOrNull()?.elapsedMs ?: 0L)
        val counterLostMicroAh = (withCounter.firstOrNull()?.chargeCounterMicroAh ?: 0) - (withCounter.lastOrNull()?.chargeCounterMicroAh ?: 0)
        val drainMah = if (counterSpanMs >= MIN_DRAIN_SPAN_MS && counterLostMicroAh >= 0) {
            (counterLostMicroAh / 1000f) / (counterSpanMs / 3_600_000f)
        } else null
        val peakCpu = samples.filter { it.cpuTempCelsius != null }.maxByOrNull { it.cpuTempCelsius ?: 0f }
        val peakBattery = samples.filter { it.batteryTempCelsius != null }.maxByOrNull { it.batteryTempCelsius ?: 0f }
        val lowestFps = samples.filter { it.fps != null }.minByOrNull { it.fps ?: 0f }

        return SessionReport(
            packageName = pkg,
            gameLabel = label,
            startedAtMs = startedAt,
            durationMs = duration,
            samples = samples,
            maxFps = maxFps,
            minFps = minFps,
            avgFps = avgFps,
            low5PercentFps = low5,
            minorDropCount = minorCount,
            severeDropCount = severeCount,
            avgCpuTemp = cpuTemps.average().toFloat().takeIf { cpuTemps.isNotEmpty() },
            maxCpuTemp = cpuTemps.maxOrNull(),
            minCpuTemp = cpuTemps.minOrNull(),
            avgBatteryTemp = batteryTemps.average().toFloat().takeIf { batteryTemps.isNotEmpty() },
            maxBatteryTemp = batteryTemps.maxOrNull(),
            minBatteryTemp = batteryTemps.minOrNull(),
            avgPowerWatts = powers.average().toFloat().takeIf { powers.isNotEmpty() },
            avgCpuFreqMhz = cpuFreqsMhz.average().toFloat().takeIf { cpuFreqsMhz.isNotEmpty() },
            maxCpuFreqMhz = cpuFreqsMhz.maxOrNull(),
            avgGpuFreqMhz = gpuFreqsMhz.average().toFloat().takeIf { gpuFreqsMhz.isNotEmpty() },
            maxGpuFreqMhz = gpuFreqsMhz.maxOrNull(),
            fpsSupported = fpsSupported,
            resolution = resolution,
            startBatteryPercent = firstBattery?.batteryPercent,
            endBatteryPercent = lastBattery?.batteryPercent,
            drainPercentPerHour = drainPercent,
            drainMahPerHour = drainMah,
            peakCpuTempAtMs = peakCpu?.elapsedMs,
            peakBatteryTempAtMs = peakBattery?.elapsedMs,
            lowestFpsAtMs = lowestFps?.elapsedMs
        )
    }

    private fun persist(report: SessionReport) {
        try {
            val json = JSONObject().apply {
                put("packageName", report.packageName)
                put("gameLabel", report.gameLabel)
                put("startedAtMs", report.startedAtMs)
                put("durationMs", report.durationMs)
                put("fpsSupported", report.fpsSupported)
                put("resolution", report.resolution ?: JSONObject.NULL)
                put("samples", JSONArray().apply {
                    report.samples.forEach { s ->
                        put(JSONObject().apply {
                            put("elapsedMs", s.elapsedMs)
                            put("fps", s.fps ?: JSONObject.NULL)
                            put("avgCpuFreqKhz", s.avgCpuFreqKhz ?: JSONObject.NULL)
                            put("gpuFreqKhz", s.gpuFreqKhz ?: JSONObject.NULL)
                            put("cpuTempCelsius", s.cpuTempCelsius ?: JSONObject.NULL)
                            put("batteryTempCelsius", s.batteryTempCelsius ?: JSONObject.NULL)
                            put("powerWatts", s.powerWatts ?: JSONObject.NULL)
                            put("batteryPercent", s.batteryPercent ?: JSONObject.NULL)
                            put("chargeCounterMicroAh", s.chargeCounterMicroAh ?: JSONObject.NULL)
                        })
                    }
                })
            }
            File(sessionsDir(), "${report.startedAtMs}.json").writeText(json.toString())
            pruneHistory()
        } catch (t: Throwable) {
            Logger.e(TAG, "Failed to persist session report", t)
        }
    }

    private fun parseReport(json: JSONObject): SessionReport {
        val samplesArray = json.getJSONArray("samples")
        val samples = (0 until samplesArray.length()).map { i ->
            val s = samplesArray.getJSONObject(i)
            SessionSample(
                elapsedMs = s.getLong("elapsedMs"),
                fps = if (s.isNull("fps")) null else s.getDouble("fps").toFloat(),
                avgCpuFreqKhz = if (s.isNull("avgCpuFreqKhz")) null else s.getInt("avgCpuFreqKhz"),
                gpuFreqKhz = if (s.isNull("gpuFreqKhz")) null else s.getInt("gpuFreqKhz"),
                cpuTempCelsius = if (s.isNull("cpuTempCelsius")) null else s.getDouble("cpuTempCelsius").toFloat(),
                batteryTempCelsius = if (s.isNull("batteryTempCelsius")) null else s.getDouble("batteryTempCelsius").toFloat(),
                powerWatts = if (s.isNull("powerWatts")) null else s.getDouble("powerWatts").toFloat(),
                batteryPercent = if (s.isNull("batteryPercent")) null else s.getInt("batteryPercent"),
                chargeCounterMicroAh = if (s.isNull("chargeCounterMicroAh")) null else s.getInt("chargeCounterMicroAh")
            )
        }
        return buildReport(
            json.getString("packageName"),
            json.getString("gameLabel"),
            json.getLong("startedAtMs"),
            json.getLong("durationMs"),
            samples,
            json.getBoolean("fpsSupported"),
            if (json.isNull("resolution")) null else json.optString("resolution").takeIf { it.isNotBlank() }
        )
    }
}
