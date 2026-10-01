package com.kynox.gaming.data.gaming

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.kynox.gaming.data.backup.BackupStore
import com.kynox.gaming.domain.model.InstalledGame
import com.kynox.gaming.domain.model.ProfileType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val MANAGED_KEY = "managed_games"
private const val PROFILE_MAP_KEY = "game_profiles"

/**
 * Lists installed, launchable apps via the standard <queries> + MAIN/LAUNCHER
 * intent approach (no QUERY_ALL_PACKAGES permission needed), flags the ones
 * Android itself categorizes as games (ApplicationInfo.CATEGORY_GAME), and
 * lets the user add/remove any app from their own managed list -- since not
 * every game correctly declares that category.
 *
 * The managed list is what auto Game Mode detection watches for.
 */
class GameLibraryRepository(
    private val context: Context,
    private val backupStore: BackupStore
) {

    suspend fun listInstalledApps(): List<InstalledGame> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val managed = loadManagedSet()
        val profiles = loadProfileMap()

        val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        @Suppress("DEPRECATION")
        val resolved = pm.queryIntentActivities(intent, 0)

        resolved.mapNotNull { info ->
            val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
            if (pkg == context.packageName) return@mapNotNull null
            val appInfo: ApplicationInfo = try {
                pm.getApplicationInfo(pkg, 0)
            } catch (t: Throwable) {
                null
            } ?: return@mapNotNull null
            val label = try { info.loadLabel(pm)?.toString() } catch (t: Throwable) { null } ?: pkg
            val isGameCategory = appInfo.category == ApplicationInfo.CATEGORY_GAME
            InstalledGame(
                packageName = pkg,
                label = label,
                isAutoDetectedGame = isGameCategory,
                isManaged = managed.contains(pkg),
                assignedProfile = profiles[pkg]
            )
        }
            .distinctBy { it.packageName }
            .sortedWith(
                compareByDescending<InstalledGame> { it.isManaged }
                    .thenByDescending { it.isAutoDetectedGame }
                    .thenBy { it.label.lowercase() }
            )
    }

    suspend fun setManaged(packageName: String, managed: Boolean) = withContext(Dispatchers.IO) {
        val current = loadManagedSet().toMutableSet()
        if (managed) current.add(packageName) else current.remove(packageName)
        backupStore.save(MANAGED_KEY, mapOf("packages" to current.joinToString(",")))
        if (!managed) setProfileFor(packageName, null)
    }

    /** The profile a specific game should use when Game Mode turns on for it, or null to use the default Gaming profile. */
    suspend fun profileFor(packageName: String): ProfileType? = loadProfileMap()[packageName]

    suspend fun setProfileFor(packageName: String, profile: ProfileType?) = withContext(Dispatchers.IO) {
        val current = loadProfileMap().toMutableMap()
        if (profile == null) current.remove(packageName) else current[packageName] = profile
        backupStore.save(PROFILE_MAP_KEY, current.mapValues { it.value.name })
    }

    private suspend fun loadProfileMap(): Map<String, ProfileType> {
        val saved = backupStore.load(PROFILE_MAP_KEY) ?: return emptyMap()
        return saved.mapNotNull { (pkg, value) ->
            value?.let { runCatching { ProfileType.valueOf(it) }.getOrNull() }?.let { pkg to it }
        }.toMap()
    }

    suspend fun managedCount(): Int = loadManagedSet().size

    suspend fun managedPackages(): Set<String> = loadManagedSet()

    fun labelFor(packageName: String): String {
        val pm = context.packageManager
        return try {
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        } catch (t: Throwable) {
            packageName
        }
    }

    private suspend fun loadManagedSet(): Set<String> {
        val saved = backupStore.load(MANAGED_KEY)?.get("packages") ?: return emptySet()
        return saved.split(",").filter { it.isNotBlank() }.toSet()
    }
}
