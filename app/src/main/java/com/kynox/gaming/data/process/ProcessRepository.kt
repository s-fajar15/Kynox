package com.kynox.gaming.data.process

import com.kynox.gaming.core.root.RootExecutor
import com.kynox.gaming.core.sysfs.SysfsAccess
import com.kynox.gaming.domain.model.ProcessInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val PS_TIMEOUT_MS = 5000L
private const val CPUINFO_TIMEOUT_MS = 5000L
private val CPU_INFO_LINE = Regex("""^\s*([\d.]+)%\s+(\d+)/([^:]+):""")

/**
 * Lists running processes for a task-manager-style screen, and kills them
 * on request. Two root calls are combined per refresh: `ps` for the
 * process list itself (pid/user/memory/uptime), and Android's own
 * `dumpsys cpuinfo` for per-process CPU% -- toybox's `ps` on Android
 * doesn't compute a usable %CPU column, but `dumpsys cpuinfo` already does
 * this accounting for the whole system, so it is reused instead of trying
 * to compute jiffy deltas per PID by hand.
 */
class ProcessRepository(private val rootExecutor: RootExecutor) {

    suspend fun list(): List<ProcessInfo> = withContext(Dispatchers.IO) {
        val totalMemKb = readTotalMemKb()
        val cpuByPid = readCpuUsage()

        val withEtime = rootExecutor.execute("ps -A -o PID,USER,RSS,ETIME,ARGS", PS_TIMEOUT_MS)
        val hasEtime = withEtime.isSuccess
        val result = if (hasEtime) withEtime else rootExecutor.execute("ps -A -o PID,USER,RSS,ARGS", PS_TIMEOUT_MS)
        if (!result.isSuccess) return@withContext emptyList()

        result.stdout.lineSequence()
            .drop(1) // header row
            .mapNotNull { line -> parsePsLine(line, hasEtime, totalMemKb, cpuByPid) }
            .toList()
    }

    /** Best-effort: a failed or refused kill just leaves the process running, nothing else to roll back. */
    suspend fun kill(pid: Int): Boolean = withContext(Dispatchers.IO) {
        rootExecutor.execute("kill -9 $pid", 3000L).isSuccess
    }

    private suspend fun readCpuUsage(): Map<Int, Float> {
        val result = rootExecutor.execute("dumpsys cpuinfo", CPUINFO_TIMEOUT_MS)
        if (!result.isSuccess) return emptyMap()
        val usage = mutableMapOf<Int, Float>()
        result.stdout.lineSequence().forEach { line ->
            val match = CPU_INFO_LINE.find(line) ?: return@forEach
            val percent = match.groupValues[1].toFloatOrNull()
            val pid = match.groupValues[2].toIntOrNull()
            if (percent != null && pid != null) usage[pid] = percent
        }
        return usage
    }

    private fun readTotalMemKb(): Long? = SysfsAccess.readDirect("/proc/meminfo")
        ?.lineSequence()
        ?.firstOrNull { it.startsWith("MemTotal:") }
        ?.let { Regex("""(\d+)""").find(it)?.value?.toLongOrNull() }

    private fun parsePsLine(line: String, hasEtime: Boolean, totalMemKb: Long?, cpuByPid: Map<Int, Float>): ProcessInfo? {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return null
        val fieldCount = if (hasEtime) 5 else 4
        val parts = trimmed.split(Regex("\\s+"), limit = fieldCount)
        if (parts.size < fieldCount) return null

        val pid = parts[0].toIntOrNull() ?: return null
        val user = parts[1]
        val rssKb = parts[2].toLongOrNull()
        val etime = if (hasEtime) parts[3] else null
        val args = parts.last()

        return ProcessInfo(
            pid = pid,
            user = user,
            name = args,
            cpuPercent = cpuByPid[pid],
            memoryKb = rssKb,
            memoryPercent = if (rssKb != null && totalMemKb != null && totalMemKb > 0) rssKb * 100f / totalMemKb else null,
            uptimeSeconds = etime?.let(::parseElapsedTime)
        )
    }

    /** toybox `ps` ETIME format: `[[DD-]HH:]MM:SS`. */
    private fun parseElapsedTime(etime: String): Long? {
        val dashSplit = etime.split("-")
        val days = if (dashSplit.size == 2) dashSplit[0].toLongOrNull() else null
        val clock = if (dashSplit.size == 2) dashSplit[1] else etime
        val segments = clock.split(":").mapNotNull { it.toLongOrNull() }
        if (segments.isEmpty()) return null
        var seconds = 0L
        for (segment in segments) seconds = seconds * 60 + segment
        return seconds + (days ?: 0L) * 86400L
    }
}
