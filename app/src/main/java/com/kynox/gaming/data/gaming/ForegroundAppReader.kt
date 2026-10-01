package com.kynox.gaming.data.gaming

import com.kynox.gaming.core.root.RootExecutor

private const val ACTIVITY_COMMAND = "dumpsys activity activities | grep -E 'topResumedActivity|mResumedActivity'"
private const val WINDOW_COMMAND = "dumpsys window | grep -E 'mCurrentFocus|mFocusedApp'"
private const val COMMAND_TIMEOUT_MS = 4000L

private val componentPattern = Regex("""\bu\d+\s+([A-Za-z][A-Za-z0-9_.]*)/""")

/**
 * Reads which app is currently in the foreground by asking the system
 * (as root) for its resumed activity, falling back to the focused window.
 * The dumpsys text format varies between Android versions and vendors, so
 * this returns null instead of guessing whenever nothing can be parsed.
 */
class ForegroundAppReader(private val rootExecutor: RootExecutor) {

    suspend fun read(): String? {
        val activities = rootExecutor.execute(ACTIVITY_COMMAND, COMMAND_TIMEOUT_MS)
        parse(activities.stdout)?.let { return it }
        val window = rootExecutor.execute(WINDOW_COMMAND, COMMAND_TIMEOUT_MS)
        return parse(window.stdout)
    }

    private fun parse(output: String): String? {
        if (output.isBlank()) return null
        val lines = output.lines()
        val ordered = lines.filter { it.contains("topResumedActivity") } +
            lines.filter { !it.contains("topResumedActivity") }
        for (line in ordered) {
            val match = componentPattern.find(line)
            if (match != null) return match.groupValues[1]
        }
        return null
    }
}
