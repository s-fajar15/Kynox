package com.kynox.gaming.data.gaming

import com.kynox.gaming.core.root.RootExecutor
import com.kynox.gaming.core.utils.Logger

private const val TAG = "FpsReader"

// Each command brackets the SurfaceFlinger calls with the device's own
// uptime, so the length of a sampling window is measured on the same clock
// that the frames are counted against, not on the app side where process
// start-up latency would skew it.
private const val SAMPLE_COMMAND =
    "cat /proc/uptime; dumpsys SurfaceFlinger --timestats -dump; " +
        "dumpsys SurfaceFlinger --timestats -clear -enable >/dev/null 2>&1; cat /proc/uptime"
private const val RESET_COMMAND =
    "dumpsys SurfaceFlinger --timestats -clear -enable >/dev/null 2>&1; cat /proc/uptime"
private const val COMMAND_TIMEOUT_MS = 4000L
private const val RESOLUTION_TIMEOUT_MS = 8000L
private const val MIN_WINDOW_SECONDS = 0.5
private const val MIN_FRAMES_FOR_READING = 3
private const val MAX_PLAUSIBLE_FPS = 240f

private val numberPattern = Regex("""-?\d+(\.\d+)?""")
private val bufferPattern = Regex("""activeBuffer=\[\s*(\d+)x(\d+)""")
private val whitespace = Regex("\\s+")

data class FpsReading(
    val fps: Float,
    val layerName: String,
    val totalFrames: Long,
    val droppedFrames: Long,
    val windowSeconds: Double,
    val displayRefreshRate: Float?,
    val renderRate: Float?
)

/** [clearedAtUptime] is when this sample's counters were reset, i.e. when the next window starts. */
data class FpsSample(val reading: FpsReading?, val clearedAtUptime: Double?)

/**
 * Fallback FPS source, used by [FpsMeter] only when the ROM gives no frame
 * timestamps. Reads the FPS of another app from SurfaceFlinger's TimeStats. Each call
 * dumps the frames counted since the previous call and then clears them, so
 * every reading covers exactly one sampling window. FPS is the frame count
 * divided by the measured length of that window. The layer is found on every
 * read by matching the package name, so it does not matter if the game is
 * opened after recording started or recreates its surface during a match.
 */
object FpsReader {

    /** Starts a fresh window and returns the uptime at which it began. */
    suspend fun reset(rootExecutor: RootExecutor): Double? {
        val result = rootExecutor.execute(RESET_COMMAND, COMMAND_TIMEOUT_MS)
        return if (result.timedOut) null else lastUptime(result.stdout)
    }

    suspend fun sample(
        packageName: String,
        previousClearUptime: Double?,
        rootExecutor: RootExecutor,
        allowIdle: Boolean = false
    ): FpsSample {
        val result = rootExecutor.execute(SAMPLE_COMMAND, COMMAND_TIMEOUT_MS)
        if (result.timedOut || result.stdout.isBlank()) return FpsSample(null, null)

        val dumpedAt = uptimeOf(result.stdout.lineSequence().firstOrNull())
        val clearedAt = lastUptime(result.stdout)
        val window = if (dumpedAt != null && previousClearUptime != null) dumpedAt - previousClearUptime else null

        // A window this short holds only a handful of frames, and dividing by
        // it gives wild values (this is what the first sample after starting
        // a recording used to produce), so it is dropped instead of reported.
        val reading = if (window != null && window >= MIN_WINDOW_SECONDS) parse(result.stdout, packageName, window, allowIdle) else null
        return FpsSample(reading, clearedAt)
    }

    /**
     * Best-effort size of the game's own render buffer (its SurfaceView),
     * which is usually lower than the screen resolution. Returns null when
     * the layer cannot be found or the dump format is not recognised.
     */
    suspend fun readResolution(packageName: String, rootExecutor: RootExecutor): String? {
        val command = "dumpsys SurfaceFlinger | grep -F -A 60 '$packageName/' | " +
            "grep -E 'activeBuffer=|$packageName/|^ *\\+ '"
        val result = rootExecutor.execute(command, RESOLUTION_TIMEOUT_MS)
        if (result.timedOut || result.stdout.isBlank()) return null

        var inTarget = false
        for (rawLine in result.stdout.lineSequence()) {
            val line = rawLine.trim()
            if (line.startsWith("+ ") || line.contains("$packageName/")) {
                inTarget = line.contains("SurfaceView[$packageName/") && !line.contains("Background for")
                continue
            }
            if (!inTarget) continue
            val match = bufferPattern.find(line) ?: continue
            val width = match.groupValues[1].toIntOrNull() ?: continue
            val height = match.groupValues[2].toIntOrNull() ?: continue
            if (width > 0 && height > 0) return "${width}x$height"
        }
        return null
    }

    private fun uptimeOf(line: String?): Double? =
        line?.trim()?.split(whitespace)?.firstOrNull()?.toDoubleOrNull()

    private fun lastUptime(output: String): Double? =
        uptimeOf(output.lineSequence().lastOrNull { it.isNotBlank() })

    /**
     * For the everyday overlay: an ordinary app that draws nothing while its
     * screen sits still really is at 0 FPS, so that is shown instead of a dash.
     * Sessions never use this (see [MIN_FRAMES_FOR_READING]).
     */
    private fun idleReading(packageName: String, windowSeconds: Double) = FpsReading(
        fps = 0f,
        layerName = packageName,
        totalFrames = 0,
        droppedFrames = 0,
        windowSeconds = windowSeconds,
        displayRefreshRate = null,
        renderRate = null
    )

    private class Record(val layerName: String) {
        var totalFrames = 0L
        var droppedFrames = 0L
        var averageFps: Float? = null
        var displayRefreshRate: Float? = null
        var renderRate: Float? = null
    }

    internal fun parse(dump: String, packageName: String, windowSeconds: Double, allowIdle: Boolean): FpsReading? {
        val records = mutableListOf<Record>()
        var current: Record? = null

        for (rawLine in dump.lineSequence()) {
            val line = rawLine.trim()
            val equals = line.indexOf('=')
            if (equals <= 0) continue
            val key = line.substring(0, equals).trim()
            val value = line.substring(equals + 1).trim()

            if (key == "layerName") {
                current = if (value.contains("$packageName/")) Record(value).also { records.add(it) } else null
                continue
            }
            val record = current ?: continue
            val number = numberPattern.find(value)?.value?.toDoubleOrNull() ?: continue
            when (key) {
                "totalFrames" -> record.totalFrames = number.toLong()
                "droppedFrames" -> record.droppedFrames = number.toLong()
                "averageFPS" -> record.averageFps = number.toFloat()
                "displayRefreshRate" -> record.displayRefreshRate = number.toFloat()
                "renderRate" -> record.renderRate = number.toFloat()
            }
        }

        // A layer can be listed once per refresh/render rate, so its frames
        // are summed by name. The layer that rendered the most frames in this
        // window is the one the game is actually drawing to.
        val byLayer = records.filter { it.totalFrames > 0 }.groupBy { it.layerName }
        val best = byLayer.entries.maxByOrNull { entry -> entry.value.sumOf { it.totalFrames } }
            ?: return if (allowIdle) idleReading(packageName, windowSeconds) else null
        val frames = best.value.sumOf { it.totalFrames }
        val dominant = best.value.maxByOrNull { it.totalFrames } ?: return null

        val fps = (frames / windowSeconds).toFloat()
        Logger.d(
            TAG,
            "layer=${best.key} frames=$frames window=${"%.2f".format(windowSeconds)}s " +
                "fps=${"%.1f".format(fps)} reportedAvg=${dominant.averageFps}"
        )
        if (fps > MAX_PLAUSIBLE_FPS) return null

        // A handful of frames spread over a multi-second window is almost
        // always the game briefly not rendering at all -- backgrounded for
        // a moment, a loading transition, the layer being torn down and
        // recreated -- not a real in-play stutter, and dividing so few
        // frames by the window length produces a number that swings wildly
        // from one sample to the next. Reporting no reading here (rather
        // than a dramatic near-zero FPS) keeps a session's true worst
        // in-game moments from being buried under one loading screen.
        if (frames < MIN_FRAMES_FOR_READING && !allowIdle) return null

        return FpsReading(
            fps = fps,
            layerName = best.key,
            totalFrames = frames,
            droppedFrames = best.value.sumOf { it.droppedFrames },
            windowSeconds = windowSeconds,
            displayRefreshRate = dominant.displayRefreshRate,
            renderRate = dominant.renderRate
        )
    }
}
