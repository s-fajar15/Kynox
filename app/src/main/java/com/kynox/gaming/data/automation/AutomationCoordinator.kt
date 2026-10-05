package com.kynox.gaming.data.automation

import com.kynox.gaming.core.utils.AppResult
import com.kynox.gaming.core.utils.Logger
import com.kynox.gaming.data.backup.BackupStore
import com.kynox.gaming.data.display.RefreshRateRepository
import com.kynox.gaming.data.logs.LogRepository
import com.kynox.gaming.data.profiles.ProfileRepository
import com.kynox.gaming.domain.model.LogEntry
import com.kynox.gaming.domain.model.ProfileType
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val TAG = "AutomationCoordinator"
private const val SESSION_KEY = "automation_session"

/** Berapa kali polling berturut-turut pengguna harus menjauh dari aplikasi sebelum pengaturan dikembalikan. */
const val AUTOMATION_AWAY_POLLS = 3

sealed interface AutomationEvent {
    object None : AutomationEvent
    data class Applied(val packageName: String, val label: String, val profile: ProfileType?, val refreshRateHz: Int?) : AutomationEvent
    data class Restored(val packageName: String, val label: String) : AutomationEvent
}

/**
 * Menjalankan aturan Automation dan, yang sebelumnya belum ada, mengembalikan
 * pengaturan setelah aplikasinya ditutup.
 *
 * - Saat aplikasi yang punya aturan dibuka: simpan profil yang aktif sekarang,
 *   lalu terapkan profil dan/atau refresh rate dari aturan.
 * - Setelah pengguna meninggalkan aplikasi selama [AUTOMATION_AWAY_POLLS]
 *   polling: kembalikan profil sebelumnya dan refresh rate bawaan perangkat.
 * - Profil hanya dikembalikan bila profil aktif masih yang diterapkan aturan.
 *   Jika pengguna mengganti profil sendiri di tengah jalan, pilihannya dihormati.
 * - Bila Game Mode sedang aktif, profil milik game (Perpustakaan Game) menang;
 *   aturan hanya mengatur refresh rate.
 *
 * Status disimpan di [BackupStore] sehingga tetap bisa dikembalikan walau
 * proses sempat dimatikan sistem.
 */
class AutomationCoordinator(
    private val automationRepository: AutomationRepository,
    private val profileRepository: ProfileRepository,
    private val refreshRateRepository: RefreshRateRepository,
    private val backupStore: BackupStore,
    private val logRepository: LogRepository
) {
    private val mutex = Mutex()
    private var loaded = false
    private var ownedPackage: String? = null
    private var ownedLabel: String = ""
    private var previousProfile: String? = null
    private var appliedProfile: String? = null
    private var hzApplied = false
    private var awayPolls = 0

    /** Paket yang sedang dikendalikan aturan, atau null. */
    @Volatile var activePackage: String? = null
        private set

    suspend fun onForeground(foreground: String?, selfPackage: String, gameModeActive: Boolean): AutomationEvent = mutex.withLock {
        ensureLoaded()
        if (!automationRepository.isEnabled()) return@withLock restoreLocked()
        if (foreground == null || foreground == selfPackage) return@withLock AutomationEvent.None
        if (ownedPackage == foreground) {
            awayPolls = 0
            return@withLock AutomationEvent.None
        }
        val rule = automationRepository.list().firstOrNull { it.enabled && it.packageName == foreground }
        if (rule == null) {
            if (ownedPackage == null) return@withLock AutomationEvent.None
            awayPolls = minOf(awayPolls + 1, AUTOMATION_AWAY_POLLS)
            return@withLock if (awayPolls >= AUTOMATION_AWAY_POLLS) restoreLocked() else AutomationEvent.None
        }
        applyLocked(rule, gameModeActive)
    }

    /** Dipanggil saat service berhenti atau Automation dimatikan: kembalikan apa pun yang masih dimiliki aturan. */
    suspend fun release(): AutomationEvent = mutex.withLock {
        ensureLoaded()
        restoreLocked()
    }

    private suspend fun applyLocked(rule: AutomationRule, gameModeActive: Boolean): AutomationEvent = withContext(NonCancellable) {
        val pkg = rule.packageName
        // Saat berpindah dari satu aplikasi beraturan ke yang lain, profil asli tetap yang pertama disimpan.
        if (ownedPackage == null) previousProfile = profileRepository.activeProfile()?.name

        var hz: Int? = null
        rule.refreshRateHz?.let { target ->
            refreshRateRepository.setOverride(pkg, target)
            refreshRateRepository.applyForPackage(pkg)
            hzApplied = true
            hz = target
        }

        val type = rule.profile?.let { runCatching { ProfileType.valueOf(it) }.getOrNull() }
        var appliedType: ProfileType? = null
        var error: String? = null
        if (type != null && !gameModeActive) {
            val result = profileRepository.apply(type)
            if (result is AppResult.Failure) error = result.message else appliedType = type
        }
        if (appliedType != null) appliedProfile = appliedType.name

        ownedPackage = pkg
        ownedLabel = rule.label
        activePackage = pkg
        awayPolls = 0
        persist()
        log("AUTOMATION_APPLY", pkg, previousProfile ?: "-", describe(appliedType, hz), error)
        Logger.i(TAG, "Automation applied for $pkg")
        AutomationEvent.Applied(pkg, rule.label, appliedType, hz)
    }

    private suspend fun restoreLocked(): AutomationEvent = withContext(NonCancellable) {
        val pkg = ownedPackage ?: return@withContext AutomationEvent.None
        val label = ownedLabel
        var changed = false
        var error: String? = null

        appliedProfile?.let { applied ->
            val current = profileRepository.activeProfile()?.name
            if (current == applied) {
                val previous = previousProfile?.let { runCatching { ProfileType.valueOf(it) }.getOrNull() }
                val result = if (previous != null) profileRepository.apply(previous) else profileRepository.reset()
                if (result is AppResult.Failure) error = result.message
                changed = true
            }
        }
        if (hzApplied) {
            refreshRateRepository.restoreDeviceDefault()
            changed = true
        }
        val previousName = previousProfile
        clearState()
        persist()
        if (changed) {
            log("AUTOMATION_RESTORE", pkg, "aturan aktif", previousName ?: "bawaan", error)
            Logger.i(TAG, "Automation restored after $pkg")
            AutomationEvent.Restored(pkg, label)
        } else {
            AutomationEvent.None
        }
    }

    private fun describe(profile: ProfileType?, hz: Int?): String = listOfNotNull(
        profile?.name,
        hz?.let { "$it Hz" }
    ).joinToString(" + ").ifEmpty { "-" }

    private fun clearState() {
        ownedPackage = null
        ownedLabel = ""
        previousProfile = null
        appliedProfile = null
        hzApplied = false
        awayPolls = 0
        activePackage = null
    }

    private suspend fun ensureLoaded() {
        if (loaded) return
        loaded = true
        val saved = backupStore.load(SESSION_KEY) ?: return
        val pkg = saved["pkg"] ?: return
        ownedPackage = pkg
        activePackage = pkg
        ownedLabel = saved["label"] ?: pkg
        previousProfile = saved["previous"]
        appliedProfile = saved["applied"]
        hzApplied = saved["hz"] == "true"
    }

    private suspend fun persist() {
        val pkg = ownedPackage
        if (pkg == null) {
            backupStore.clear(SESSION_KEY)
            return
        }
        backupStore.save(
            SESSION_KEY,
            mapOf(
                "pkg" to pkg,
                "label" to ownedLabel,
                "previous" to previousProfile,
                "applied" to appliedProfile,
                "hz" to hzApplied.toString()
            )
        )
    }

    private suspend fun log(action: String, target: String, previous: String, new: String, error: String?) {
        logRepository.append(
            LogEntry(System.currentTimeMillis(), action, target, previous, new, if (error == null) "SUCCESS" else "FAILED", error)
        )
    }
}
