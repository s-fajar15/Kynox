package com.kynox.gaming.data.logs

import android.content.Context
import com.kynox.gaming.core.utils.Logger
import com.kynox.gaming.domain.model.LogEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * Append-only structured log used by every feature that mutates a system
 * parameter, per PRD section 14 (timestamp/action/target/previous/new/result/error).
 * Stored as JSON Lines in app-private storage -- no extra database dependency needed.
 */
class LogRepository(context: Context) {

    private val logFile = File(context.filesDir, "kynox_log.jsonl")
    private val mutex = Mutex()

    suspend fun append(entry: LogEntry) = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val json = JSONObject().apply {
                    put("timestamp", entry.timestamp)
                    put("action", entry.action)
                    put("target", entry.target)
                    put("previousValue", entry.previousValue)
                    put("newValue", entry.newValue)
                    put("result", entry.result)
                    put("error", entry.error)
                }
                logFile.appendText(json.toString() + "\n")
            } catch (t: Throwable) {
                Logger.e("LogRepository", "Failed to append log entry", t)
            }
        }
    }

    suspend fun readAll(): List<LogEntry> = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!logFile.exists()) return@withContext emptyList()
            try {
                logFile.readLines()
                    .filter { it.isNotBlank() }
                    .mapNotNull { line ->
                        try {
                            val obj = JSONObject(line)
                            LogEntry(
                                timestamp = obj.optLong("timestamp"),
                                action = obj.optString("action"),
                                target = obj.optString("target"),
                                previousValue = obj.optString("previousValue").takeIf { obj.has("previousValue") && !obj.isNull("previousValue") },
                                newValue = obj.optString("newValue").takeIf { obj.has("newValue") && !obj.isNull("newValue") },
                                result = obj.optString("result"),
                                error = obj.optString("error").takeIf { obj.has("error") && !obj.isNull("error") }
                            )
                        } catch (t: Throwable) {
                            null
                        }
                    }
                    .sortedByDescending { it.timestamp }
            } catch (t: Throwable) {
                Logger.e("LogRepository", "Failed to read log file", t)
                emptyList()
            }
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                if (logFile.exists()) logFile.writeText("")
            } catch (t: Throwable) {
                Logger.e("LogRepository", "Failed to clear log file", t)
            }
        }
    }
}
