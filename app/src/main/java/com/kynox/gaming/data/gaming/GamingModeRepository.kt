package com.kynox.gaming.data.gaming

import com.kynox.gaming.core.utils.AppResult
import com.kynox.gaming.data.backup.BackupStore
import com.kynox.gaming.data.profiles.ProfileRepository
import com.kynox.gaming.domain.model.ProfileType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val STATE_KEY = "game_mode"

/**
 * Game Mode (PRD section 18): applies the Gaming performance profile while
 * you play and restores what was active before when it is turned off. It is
 * switched on and off either by hand or automatically by
 * [GameDetectionRepository] when a managed game opens or closes.
 *
 * If the game has its own assigned profile ([GameLibraryRepository.profileFor]),
 * that profile is applied instead of the default Gaming preset.
 */
class GamingModeRepository(
    private val profileRepository: ProfileRepository,
    private val gameLibraryRepository: GameLibraryRepository,
    private val backupStore: BackupStore
) {
    suspend fun isActive(): Boolean = withContext(Dispatchers.IO) {
        backupStore.hasBackup(STATE_KEY)
    }

    suspend fun enable(packageName: String? = null): AppResult<Unit> = withContext(Dispatchers.IO) {
        val previous = profileRepository.activeProfile()
        val target = packageName?.let { gameLibraryRepository.profileFor(it) } ?: ProfileType.GAMING
        val result = profileRepository.apply(target)
        if (result is AppResult.Failure) return@withContext AppResult.Failure(result.message)
        backupStore.save(STATE_KEY, mapOf("previous_profile" to previous?.name))
        AppResult.Success(Unit)
    }

    /** Re-applies the target profile for a different managed game while Game Mode is already active, without touching the saved pre-game backup. */
    suspend fun switchProfileFor(packageName: String): AppResult<Unit> = withContext(Dispatchers.IO) {
        val target = gameLibraryRepository.profileFor(packageName) ?: ProfileType.GAMING
        val result = profileRepository.apply(target)
        if (result is AppResult.Failure) AppResult.Failure(result.message) else AppResult.Success(Unit)
    }

    suspend fun disable(): AppResult<Unit> = withContext(Dispatchers.IO) {
        val saved = backupStore.load(STATE_KEY)
        val previousProfile = saved?.get("previous_profile")?.let { runCatching { ProfileType.valueOf(it) }.getOrNull() }
        val result = if (previousProfile != null) {
            profileRepository.apply(previousProfile)
        } else {
            profileRepository.reset()
        }
        backupStore.clear(STATE_KEY)
        if (result is AppResult.Failure) AppResult.Failure(result.message) else AppResult.Success(Unit)
    }
}
