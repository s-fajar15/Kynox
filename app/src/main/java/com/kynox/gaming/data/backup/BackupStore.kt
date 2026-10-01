package com.kynox.gaming.data.backup

import android.content.Context
import com.kynox.gaming.core.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * Generic key/value snapshot store used by any feature that mutates system
 * parameters (fast charging, thermal, performance profiles, ...). Before a
 * feature writes a new value it saves the previous one here, so it can be
 * restored later (PRD section 13/21: backup before write, restore on demand).
 */
class BackupStore(context: Context) {

    private val dir = File(context.filesDir, "backups").apply { mkdirs() }
    private val mutex = Mutex()

    suspend fun save(featureKey: String, values: Map<String, String?>) = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val json = JSONObject()
                values.forEach { (k, v) -> json.put(k, v ?: JSONObject.NULL) }
                File(dir, "$featureKey.json").writeText(json.toString())
            } catch (t: Throwable) {
                Logger.e("BackupStore", "Failed to save snapshot for $featureKey", t)
            }
        }
    }

    suspend fun load(featureKey: String): Map<String, String?>? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val file = File(dir, "$featureKey.json")
            if (!file.exists()) return@withContext null
            try {
                val json = JSONObject(file.readText())
                json.keys().asSequence().associateWith { key ->
                    if (json.isNull(key)) null else json.getString(key)
                }
            } catch (t: Throwable) {
                Logger.e("BackupStore", "Failed to load snapshot for $featureKey", t)
                null
            }
        }
    }

    suspend fun clear(featureKey: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            File(dir, "$featureKey.json").delete()
        }
    }

    suspend fun hasBackup(featureKey: String): Boolean = withContext(Dispatchers.IO) {
        File(dir, "$featureKey.json").exists()
    }
}
