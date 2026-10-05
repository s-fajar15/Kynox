package com.kynox.gaming.data.backup

import org.json.JSONObject

const val BACKUP_FORMAT = "kynox-backup"

/** Naik bila struktur berkas berubah dengan cara yang tidak kompatibel. Berkas bernomor lebih tinggi ditolak. */
const val BACKUP_VERSION = 1

/** Isi berkas cadangan setelah divalidasi. */
data class BackupFile(
    val version: Int,
    val appVersion: String,
    val createdAt: Long,
    val settings: Map<String, Any?>,
    val stores: Map<String, Map<String, String?>>
)

sealed interface BackupParse {
    data class Ok(val file: BackupFile, val skippedKeys: Int) : BackupParse
    data class Rejected(val reason: String) : BackupParse
}

/**
 * Baca/tulis berkas cadangan. Hanya kunci [BackupFormat.isAllowedStoreKey]
 * yang diterima: status perangkat (profil asli, Game Mode, sesi Automation)
 * sengaja tidak ikut, karena memulihkannya di perangkat lain akan menyesatkan.
 */
object BackupFormat {

    private val fixedStoreKeys = setOf(
        "managed_games", "game_profiles", "profile_custom",
        "refresh_rate_overrides", "refresh_rate_enabled",
        "automation_index", "automation_engine"
    )
    private val ruleKey = Regex("^automation_rule_[A-Za-z0-9_-]{1,64}$")

    fun isAllowedStoreKey(key: String): Boolean = key in fixedStoreKeys || ruleKey.matches(key)

    fun serialize(file: BackupFile): String {
        val root = JSONObject()
        root.put("format", BACKUP_FORMAT)
        root.put("version", file.version)
        root.put("appVersion", file.appVersion)
        root.put("createdAt", file.createdAt)
        val settings = JSONObject()
        file.settings.forEach { (k, v) -> settings.put(k, v) }
        root.put("settings", settings)
        val stores = JSONObject()
        file.stores.forEach { (name, values) ->
            val o = JSONObject()
            values.forEach { (k, v) -> o.put(k, v ?: JSONObject.NULL) }
            stores.put(name, o)
        }
        root.put("stores", stores)
        return root.toString(2)
    }

    fun parse(text: String): BackupParse {
        val root = try {
            JSONObject(text)
        } catch (t: Throwable) {
            return BackupParse.Rejected("Berkas bukan JSON yang valid.")
        }
        if (root.optString("format") != BACKUP_FORMAT) return BackupParse.Rejected("Ini bukan berkas cadangan Kynox.")
        val version = root.optInt("version", -1)
        if (version < 1) return BackupParse.Rejected("Nomor versi cadangan tidak terbaca.")
        if (version > BACKUP_VERSION) {
            return BackupParse.Rejected("Cadangan ini dibuat oleh Kynox yang lebih baru (format v$version). Perbarui Kynox lebih dulu.")
        }

        var skipped = 0
        val settings = LinkedHashMap<String, Any?>()
        root.optJSONObject("settings")?.let { o ->
            o.keys().forEach { k -> settings[k] = if (o.isNull(k)) null else o.get(k) }
        }
        val stores = LinkedHashMap<String, Map<String, String?>>()
        root.optJSONObject("stores")?.let { o ->
            o.keys().forEach { name ->
                val inner = o.optJSONObject(name)
                if (inner == null || !isAllowedStoreKey(name)) {
                    skipped++
                    return@forEach
                }
                val values = LinkedHashMap<String, String?>()
                var valid = true
                inner.keys().forEach { k ->
                    if (inner.isNull(k)) values[k] = null
                    else {
                        val v = inner.get(k)
                        if (v is String) values[k] = v else valid = false
                    }
                }
                if (valid) stores[name] = values else skipped++
            }
        }
        return BackupParse.Ok(
            BackupFile(version, root.optString("appVersion"), root.optLong("createdAt"), settings, stores),
            skipped
        )
    }
}
