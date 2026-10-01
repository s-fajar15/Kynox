package com.kynox.gaming.data.automation

import com.kynox.gaming.data.backup.BackupStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AutomationRule(
    val id: String,
    val packageName: String,
    val label: String,
    val enabled: Boolean,
    val refreshRateHz: Int?,
    val profile: String?
)

class AutomationRepository(private val backupStore: BackupStore) {
    private val prefix = "automation_rule_"
    private val engineKey = "automation_engine"

    suspend fun isEnabled(): Boolean = withContext(Dispatchers.IO) {
        backupStore.load(engineKey)?.get("enabled") == "true"
    }
    suspend fun setEnabled(enabled: Boolean) = backupStore.save(engineKey, mapOf("enabled" to enabled.toString()))

    suspend fun list(): List<AutomationRule> = withContext(Dispatchers.IO) {
        val ids = backupStore.load("automation_index")?.get("ids")?.split(",")?.filter { it.isNotBlank() }.orEmpty()
        ids.mapNotNull { id ->
            backupStore.load(prefix + id)?.let { m ->
                val pkg = m["package"] ?: return@mapNotNull null
                AutomationRule(id, pkg, m["label"] ?: pkg, m["enabled"] == "true", m["refresh"]?.toIntOrNull(), m["profile"])
            }
        }
    }

    suspend fun upsert(rule: AutomationRule) = withContext(Dispatchers.IO) {
        backupStore.save(prefix + rule.id, mapOf(
            "package" to rule.packageName,
            "label" to rule.label,
            "enabled" to rule.enabled.toString(),
            "refresh" to rule.refreshRateHz?.toString(),
            "profile" to rule.profile
        ))
        val ids = list().map { it.id }.toMutableList()
        if (rule.id !in ids) ids += rule.id
        backupStore.save("automation_index", mapOf("ids" to ids.joinToString(",")))
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        backupStore.clear(prefix + id)
        val ids = list().map { it.id }.filterNot { it == id }
        backupStore.save("automation_index", mapOf("ids" to ids.joinToString(",")))
    }
}
