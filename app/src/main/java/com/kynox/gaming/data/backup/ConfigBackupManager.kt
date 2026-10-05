package com.kynox.gaming.data.backup

import com.kynox.gaming.core.utils.Logger
import com.kynox.gaming.data.automation.AutomationRepository
import com.kynox.gaming.data.logs.LogRepository
import com.kynox.gaming.data.settings.PortableSettings
import com.kynox.gaming.data.settings.SettingsRepository
import com.kynox.gaming.domain.model.LogEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "ConfigBackupManager"

data class RestoreSummary(val settingsApplied: Int, val storesApplied: Int, val rulesApplied: Int)

/**
 * Ekspor/impor konfigurasi pengguna: pengaturan, aturan Automation, daftar
 * game beserta profilnya, profil kustom, dan refresh rate per aplikasi.
 * Tidak ada perintah sistem yang dijalankan saat impor; semua hanya
 * menulis konfigurasi Kynox sendiri, dan hasilnya baru dipakai saat fitur terkait berjalan.
 */
class ConfigBackupManager(
    private val backupStore: BackupStore,
    private val settingsRepository: SettingsRepository,
    private val automationRepository: AutomationRepository,
    private val logRepository: LogRepository,
    private val appVersionName: String
) {
    private val fixedKeys = listOf(
        "managed_games", "game_profiles", "profile_custom",
        "refresh_rate_overrides", "refresh_rate_enabled", "automation_engine"
    )

    suspend fun export(): String = withContext(Dispatchers.IO) {
        val stores = LinkedHashMap<String, Map<String, String?>>()
        fixedKeys.forEach { key -> backupStore.load(key)?.let { stores[key] = it } }
        val rules = automationRepository.list()
        stores["automation_index"] = mapOf("ids" to rules.joinToString(",") { it.id })
        rules.forEach { r ->
            backupStore.load("automation_rule_" + r.id)?.let { stores["automation_rule_" + r.id] = it }
        }
        BackupFormat.serialize(
            BackupFile(BACKUP_VERSION, appVersionName, System.currentTimeMillis(), settingsRepository.exportPortable(), stores)
        )
    }

    /** Menerapkan berkas yang sudah lolos [BackupFormat.parse]. Aturan Automation diganti seluruhnya sesuai berkas. */
    suspend fun restore(file: BackupFile): RestoreSummary = withContext(Dispatchers.IO) {
        val settingsApplied = settingsRepository.importPortable(file.settings)

        val incomingRules = file.stores.keys.filter { it.startsWith("automation_rule_") }
        val incomingIds = incomingRules.map { it.removePrefix("automation_rule_") }
        if (file.stores.containsKey("automation_index") || incomingRules.isNotEmpty()) {
            automationRepository.list().forEach { automationRepository.delete(it.id) }
        }
        var storesApplied = 0
        file.stores.forEach { (key, values) ->
            if (key == "automation_index") return@forEach
            if (!BackupFormat.isAllowedStoreKey(key)) return@forEach
            // Automation baru dinyalakan sendiri oleh pengguna; impor tidak boleh menghidupkannya diam-diam.
            backupStore.save(key, if (key == "automation_engine") mapOf("enabled" to "false") else values)
            storesApplied++
        }
        if (incomingIds.isNotEmpty()) {
            backupStore.save("automation_index", mapOf("ids" to incomingIds.joinToString(",")))
        }
        logRepository.append(
            LogEntry(
                System.currentTimeMillis(), "RESTORE_BACKUP", "konfigurasi Kynox",
                null, "${settingsApplied} pengaturan, ${incomingIds.size} aturan", "SUCCESS", null
            )
        )
        Logger.i(TAG, "Restored backup v${file.version} from ${file.appVersion}")
        RestoreSummary(settingsApplied, storesApplied, incomingIds.size)
    }
}
