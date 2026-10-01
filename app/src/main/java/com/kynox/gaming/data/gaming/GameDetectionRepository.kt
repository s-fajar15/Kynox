package com.kynox.gaming.data.gaming

import com.kynox.gaming.core.root.RootExecutor
import com.kynox.gaming.core.utils.AppResult
import com.kynox.gaming.core.utils.Logger
import com.kynox.gaming.data.backup.BackupStore
import com.kynox.gaming.data.logs.LogRepository
import com.kynox.gaming.domain.model.LogEntry
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val TAG = "GameDetection"
private const val STATE_KEY = "game_autodetect"
private const val AWAY_POLLS_BEFORE_RESTORE = 3

sealed interface DetectionEvent {
    object None : DetectionEvent
    data class GameStarted(val packageName: String, val label: String) : DetectionEvent
    data class GameStopped(val packageName: String) : DetectionEvent
}

/**
 * Auto Game Mode: when a game from the user's managed list comes to the
 * foreground it turns Game Mode on, and once the user has been away from it
 * for a few polls it restores the previous profile.
 *
 * It only ever undoes what it did itself. If Game Mode was already on
 * (turned on by hand) it is left alone, and if the user switches it off
 * while a game is still open, it will not switch it back on until they
 * leave that game.
 */
class GameDetectionRepository(
    private val rootExecutor: RootExecutor,
    private val foregroundAppReader: ForegroundAppReader,
    private val gameLibraryRepository: GameLibraryRepository,
    private val gamingModeRepository: GamingModeRepository,
    private val backupStore: BackupStore,
    private val logRepository: LogRepository
) {
    private val mutex = Mutex()
    private var loaded = false
    private var enabled = false
    private var owned: String? = null
    private var suppressed: String? = null
    private var awayPolls = 0

    /** The managed game currently in front, or null once the user has been away from it for a few polls. */
    @Volatile var foregroundGame: String? = null
        private set

    suspend fun isRootAvailable(): Boolean = rootExecutor.status().isAvailable

    suspend fun managedCount(): Int = gameLibraryRepository.managedCount()

    suspend fun isEnabled(): Boolean = mutex.withLock {
        ensureLoaded()
        enabled
    }

    suspend fun setEnabled(value: Boolean) {
        mutex.withLock {
            ensureLoaded()
            enabled = value
            persist()
        }
    }

    suspend fun poll(selfPackage: String): DetectionEvent = mutex.withLock {
        ensureLoaded()
        val foreground = foregroundAppReader.read() ?: return@withLock DetectionEvent.None
        if (foreground == selfPackage) return@withLock DetectionEvent.None
        val managed = gameLibraryRepository.managedPackages()
        if (foreground in managed) {
            foregroundGame = foreground
            onManagedForeground(foreground)
        } else {
            onOtherForeground()
        }
    }

    suspend fun release() {
        mutex.withLock {
            ensureLoaded()
            owned?.let { restore(it) }
            suppressed = null
            foregroundGame = null
            awayPolls = 0
        }
    }

    private suspend fun onManagedForeground(pkg: String): DetectionEvent {
        awayPolls = 0
        if (suppressed == pkg) return DetectionEvent.None
        val modeActive = gamingModeRepository.isActive()
        val current = owned
        if (current != null) {
            if (!modeActive) {
                owned = null
                suppressed = pkg
                persist()
                return DetectionEvent.None
            }
            if (current != pkg) {
                val result = gamingModeRepository.switchProfileFor(pkg)
                val error = (result as? AppResult.Failure)?.message
                log("AUTO_GAME_MODE_SWITCH", pkg, current, pkg, error)
                owned = pkg
                persist()
                return DetectionEvent.GameStarted(pkg, gameLibraryRepository.labelFor(pkg))
            }
            return DetectionEvent.None
        }
        if (modeActive) return DetectionEvent.None
        return enableFor(pkg)
    }

    private suspend fun onOtherForeground(): DetectionEvent {
        awayPolls = minOf(awayPolls + 1, AWAY_POLLS_BEFORE_RESTORE)
        if (awayPolls < AWAY_POLLS_BEFORE_RESTORE) return DetectionEvent.None
        suppressed = null
        foregroundGame = null
        val current = owned ?: return DetectionEvent.None
        return restore(current)
    }

    private suspend fun enableFor(pkg: String): DetectionEvent = withContext(NonCancellable) {
        val result = gamingModeRepository.enable(pkg)
        val error = (result as? AppResult.Failure)?.message
        log("AUTO_GAME_MODE_ON", pkg, "off", "on", error)
        if (error == null) {
            owned = pkg
            persist()
            Logger.i(TAG, "Game Mode enabled for $pkg")
            DetectionEvent.GameStarted(pkg, gameLibraryRepository.labelFor(pkg))
        } else {
            suppressed = pkg
            Logger.w(TAG, "Could not enable Game Mode for $pkg: $error")
            DetectionEvent.None
        }
    }

    private suspend fun restore(pkg: String): DetectionEvent = withContext(NonCancellable) {
        val result = gamingModeRepository.disable()
        val error = (result as? AppResult.Failure)?.message
        log("AUTO_GAME_MODE_OFF", pkg, "on", "off", error)
        owned = null
        awayPolls = 0
        persist()
        Logger.i(TAG, "Game Mode restored after $pkg")
        DetectionEvent.GameStopped(pkg)
    }

    private suspend fun ensureLoaded() {
        if (loaded) return
        val saved = backupStore.load(STATE_KEY)
        enabled = saved?.get("enabled") == "true"
        owned = saved?.get("owned")
        loaded = true
    }

    private suspend fun persist() {
        backupStore.save(STATE_KEY, mapOf("enabled" to enabled.toString(), "owned" to owned))
    }

    private suspend fun log(action: String, target: String, previous: String, new: String, error: String?) {
        logRepository.append(
            LogEntry(
                System.currentTimeMillis(),
                action,
                target,
                previous,
                new,
                if (error == null) "SUCCESS" else "FAILED",
                error
            )
        )
    }
}
