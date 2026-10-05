package com.kynox.gaming.data.gaming

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import com.kynox.gaming.core.root.RootExecutor

private const val ACTIVITY_COMMAND = "dumpsys activity activities | grep -E 'topResumedActivity|mResumedActivity'"
private const val WINDOW_COMMAND = "dumpsys window | grep -E 'mCurrentFocus|mFocusedApp'"
private const val COMMAND_TIMEOUT_MS = 4000L

private val componentPattern = Regex("""\bu\d+\s+([A-Za-z][A-Za-z0-9_.]*)/""")

/**
 * Reads which app is currently in the foreground. With root it asks the system
 * for the resumed activity (falling back to the focused window). Without root,
 * or when that yields nothing, it uses Usage Access (UsageStatsManager) so the
 * per-app features also work on non-rooted devices. Returns null instead of guessing.
 */
class ForegroundAppReader(
    private val rootExecutor: RootExecutor,
    private val context: Context? = null
) {

    suspend fun read(): String? {
        val rootResult = try { readWithRoot() } catch (_: Throwable) { null }
        if (rootResult != null) return rootResult
        return context?.let { readWithUsageStats(it) }
    }

    private suspend fun readWithRoot(): String? {
        val activities = rootExecutor.execute(ACTIVITY_COMMAND, COMMAND_TIMEOUT_MS)
        parse(activities.stdout)?.let { return it }
        val window = rootExecutor.execute(WINDOW_COMMAND, COMMAND_TIMEOUT_MS)
        return parse(window.stdout)
    }

    @Suppress("DEPRECATION")
    private fun readWithUsageStats(ctx: Context): String? {
        if (!hasUsageAccess(ctx)) return null
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return null
        val now = System.currentTimeMillis()
        // Short window first (cheap); widen only if the foreground app has been static for a while.
        for (windowMs in longArrayOf(2 * 60_000L, 30 * 60_000L, 6 * 3_600_000L)) {
            val events = usm.queryEvents(now - windowMs, now) ?: continue
            val event = UsageEvents.Event()
            var current: String? = null
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                when (event.eventType) {
                    UsageEvents.Event.MOVE_TO_FOREGROUND -> current = event.packageName
                    UsageEvents.Event.MOVE_TO_BACKGROUND -> if (current == event.packageName) current = null
                }
            }
            if (current != null) return current
        }
        return null
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

    companion object {
        /** True when the user has granted Usage Access to Kynox. */
        fun hasUsageAccess(context: Context): Boolean = try {
            val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            @Suppress("DEPRECATION")
            val mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
            mode == AppOpsManager.MODE_ALLOWED
        } catch (_: Throwable) {
            false
        }
    }
}
