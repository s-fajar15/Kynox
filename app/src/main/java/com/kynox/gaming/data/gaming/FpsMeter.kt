package com.kynox.gaming.data.gaming

import android.os.SystemClock
import com.kynox.gaming.core.root.RootExecutor
import com.kynox.gaming.core.utils.Logger

private const val TAG = "FpsMeter"
private const val COMMAND_TIMEOUT_MS = 5000L
private const val MAX_LAYERS = 8
private const val MIN_FRAMES_FOR_READING = 3
private const val MAX_PLAUSIBLE_FPS = 240f

/** SurfaceFlinger keeps about 127 frames per layer; a buffer this full means frames may have been pushed out unseen. */
private const val SATURATED_FRAME_COUNT = 100
private const val UNSUPPORTED_SAMPLES_BEFORE_FALLBACK = 3
private const val STEADY_LOW = 0.7
private const val STEADY_HIGH = 1.3

private val PACKAGE_REGEX = Regex("^[A-Za-z0-9._]+$")
private val whitespace = Regex("\\s+")

private class LayerFrames(val name: String, val refreshPeriodNs: Long, val presentNs: List<Long>)

private class Candidate(val name: String, val fps: Float, val frames: Int, val windowSeconds: Double, val refreshPeriodNs: Long)

/**
 * Measures the FPS of the app in front from the *presentation timestamps*
 * SurfaceFlinger records for each of its layers (`dumpsys SurfaceFlinger
 * --latency <layer>`): the moment each frame actually reached the screen.
 *
 * Why this and not a frame counter: a counter has to be read and reset with
 * two separate commands, and the time between them (each `su` + `dumpsys`
 * start-up costs tens of milliseconds) lands in neither the measured window
 * nor the count, so the result drifts high. Here every frame is counted
 * exactly once -- "frames newer than the newest one seen last time" -- and
 * the time base is the frames' own timestamps, so command latency does not
 * enter the result at all. The shown FPS is therefore what reached the
 * display (a game rendering 90 on a 60 Hz panel reads 60).
 *
 * The first call after starting, or after switching to another app, only
 * remembers where the frames were and returns null; readings begin with the
 * next call. If the ROM does not support `--latency` for a few calls in a
 * row, it falls back to the older frame-counter method ([FpsReader]).
 *
 * [allowIdle] is for the everyday overlay: an app that draws nothing while
 * its screen sits still really is at 0 FPS, so that is reported. Session
 * recording leaves it off, so loading screens (a couple of frames spread
 * over a long window) do not show up as fake stutter in a report.
 */
class FpsMeter(
    private val rootExecutor: RootExecutor,
    private val allowIdle: Boolean = false
) {
    private var trackedPackage: String? = null
    private val lastNewestNs = HashMap<String, Long>()
    private var lastSampleAtNs = 0L
    private var unsupportedStreak = 0
    private var useLegacy = false
    private var legacyClearUptime: Double? = null

    fun reset() {
        trackedPackage = null
        lastNewestNs.clear()
        lastSampleAtNs = 0L
        unsupportedStreak = 0
        legacyClearUptime = null
    }

    suspend fun sample(packageName: String): FpsReading? {
        if (!PACKAGE_REGEX.matches(packageName)) return null
        if (packageName != trackedPackage) {
            reset()
            trackedPackage = packageName
        }
        return if (useLegacy) sampleLegacy(packageName) else sampleFromTimestamps(packageName)
    }

    private suspend fun sampleLegacy(packageName: String): FpsReading? {
        val sample = FpsReader.sample(packageName, legacyClearUptime, rootExecutor, allowIdle)
        legacyClearUptime = sample.clearedAtUptime
        return sample.reading
    }

    private suspend fun sampleFromTimestamps(packageName: String): FpsReading? {
        val startedNs = SystemClock.elapsedRealtimeNanos()
        val result = rootExecutor.execute(latencyCommand(packageName), COMMAND_TIMEOUT_MS)
        if (result.timedOut) return null

        val wallSeconds = if (lastSampleAtNs > 0L) (startedNs - lastSampleAtNs) / 1e9 else 0.0
        lastSampleAtNs = startedNs

        val layers = parse(result.stdout)
        if (layers.isEmpty()) {
            // Nothing of this app is on screen right now.
            unsupportedStreak = 0
            lastNewestNs.clear()
            return if (allowIdle) idleReading(packageName, wallSeconds) else null
        }

        var best: Candidate? = null
        var anyTimestamps = false
        val seen = HashSet<String>()

        for (layer in layers) {
            val times = layer.presentNs
            if (times.isEmpty()) continue
            anyTimestamps = true
            seen += layer.name

            val newest = times.last()
            val previous = lastNewestNs[layer.name]
            lastNewestNs[layer.name] = newest
            // First sight of this layer (or it was recreated): remember it, measure from the next call.
            if (previous == null || newest < previous) continue

            val fresh = times.count { it > previous }
            val fps: Float
            val window: Double
            var frames = fresh
            if (fresh == 0) {
                fps = 0f
                window = wallSeconds
            } else if (times.first() > previous && times.size >= SATURATED_FRAME_COUNT) {
                // Every frame in the buffer is new, so older ones may have been pushed out unseen.
                // Measure over what is here instead of pretending the count is complete.
                val span = (times.last() - times.first()) / 1e9
                if (span <= 0.0) continue
                fps = ((times.size - 1) / span).toFloat()
                window = span
                frames = times.size
            } else {
                val span = (newest - previous) / 1e9
                val denominator = when {
                    span <= 0.0 -> continue
                    wallSeconds <= 0.0 -> span
                    span in (wallSeconds * STEADY_LOW)..(wallSeconds * STEADY_HIGH) -> span
                    else -> wallSeconds
                }
                fps = (fresh / denominator).toFloat()
                window = denominator
            }

            val candidate = Candidate(layer.name, fps, frames, window, layer.refreshPeriodNs)
            val current = best
            if (current == null || candidate.fps > current.fps) best = candidate
        }
        lastNewestNs.keys.retainAll(seen)

        if (!anyTimestamps) {
            unsupportedStreak++
            if (unsupportedStreak >= UNSUPPORTED_SAMPLES_BEFORE_FALLBACK) {
                Logger.w(TAG, "No frame timestamps for $packageName, falling back to the frame-counter method")
                useLegacy = true
            }
            return null
        }
        unsupportedStreak = 0

        val chosen = best ?: return null
        if (chosen.fps > MAX_PLAUSIBLE_FPS) return null
        if (!allowIdle && chosen.frames < MIN_FRAMES_FOR_READING) return null

        Logger.d(TAG, "layer=${chosen.name} frames=${chosen.frames} window=${"%.2f".format(chosen.windowSeconds)}s fps=${"%.1f".format(chosen.fps)}")
        return FpsReading(
            fps = chosen.fps,
            layerName = chosen.name,
            totalFrames = chosen.frames.toLong(),
            droppedFrames = 0,
            windowSeconds = chosen.windowSeconds,
            displayRefreshRate = if (chosen.refreshPeriodNs > 0) 1e9f / chosen.refreshPeriodNs else null,
            renderRate = null
        )
    }

    private fun idleReading(packageName: String, windowSeconds: Double) = FpsReading(
        fps = 0f,
        layerName = packageName,
        totalFrames = 0,
        droppedFrames = 0,
        windowSeconds = windowSeconds,
        displayRefreshRate = null,
        renderRate = null
    )

    /**
     * One line, because the root executor refuses multi-line commands. Lists the layers that belong to the package
     * (the regex keeps "com.x/" from also matching "abc.com.x/"), then asks for each one's frame timestamps.
     */
    private fun latencyCommand(packageName: String): String {
        val pattern = "(^|[^A-Za-z0-9._])" + packageName.replace(".", "\\.") + "/"
        return "dumpsys SurfaceFlinger --list | grep -E '$pattern' | grep -v 'Background for' | head -$MAX_LAYERS | " +
            "while IFS= read -r l; do echo \"@@\$l\"; dumpsys SurfaceFlinger --latency \"\$l\"; done"
    }

    private fun parse(output: String): List<LayerFrames> {
        val layers = ArrayList<LayerFrames>()
        var name: String? = null
        var period = 0L
        var times = ArrayList<Long>()

        fun flush() {
            name?.let { layers += LayerFrames(it, period, times.sorted()) }
            period = 0L
            times = ArrayList()
        }

        for (raw in output.lineSequence()) {
            val line = raw.trim()
            if (line.startsWith("@@")) {
                flush()
                name = line.substring(2)
                continue
            }
            if (name == null || line.isEmpty()) continue
            val parts = line.split(whitespace)
            when {
                parts.size == 1 -> period = parts[0].toLongOrNull() ?: period
                parts.size >= 3 -> {
                    // Columns: desired present, actual present, frame ready. Unfinished frames hold 0 or Long.MAX_VALUE.
                    val actual = parts[1].toLongOrNull()
                    if (actual != null && actual > 0L && actual < Long.MAX_VALUE) times.add(actual)
                }
            }
        }
        flush()
        return layers
    }
}
