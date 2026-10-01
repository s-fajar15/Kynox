package com.kynox.gaming.data.debloat

import android.content.Context
import com.kynox.gaming.core.root.RootExecutor
import com.kynox.gaming.core.utils.AppResult
import com.kynox.gaming.data.logs.LogRepository
import com.kynox.gaming.domain.model.LogEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DebloatApp(
    val packageName: String,
    val label: String,
    val enabled: Boolean
)

/**
 * Lets the user disable (never fully remove) non-essential preinstalled
 * system/OEM apps. "Disable for this user" (`pm disable-user`) is
 * reversible from this same screen, unlike `pm uninstall`, so that is the
 * only destructive action this feature offers.
 *
 * [PROTECTED_PACKAGES] is enforced here in the repository, not just in the
 * UI, so it can't be bypassed by a future screen. This follows the same
 * "when in doubt, don't apply" principle as the CPU/GPU/profile safety
 * work: disabling the wrong system package (SystemUI, telephony, the
 * package installer, the only keyboard...) is a bootloop/lockout risk no
 * different from a bad sysfs write, so this list is intentionally broad
 * and app list queries NEVER even surface a protected package as an
 * option, regardless of how the user searches.
 */
class DebloatRepository(
    private val context: Context,
    private val rootExecutor: RootExecutor,
    private val logRepository: LogRepository
) {

    /**
     * Preinstalled (system-partition) apps only -- regular user-installed
     * apps are never offered here, to keep this strictly a "remove OEM
     * bloat" tool, not a general app manager/uninstaller.
     *
     * The package list itself comes from `pm list packages` run as root,
     * not [PackageManager.getInstalledApplications]: since Android 11, that
     * API is filtered by package visibility, and this app only declares
     * visibility into *launchable* apps (for the Game Library). Most real
     * bloatware (carrier services, OEM telemetry, preloaded "partner" apps)
     * has no launcher icon at all, so the filtered API would show almost
     * nothing worth debloating. A root shell command isn't subject to the
     * calling app's manifest visibility rules, so it sees everything --
     * only the friendly label/icon lookup below still goes through
     * PackageManager, and falls back to the raw package name on any
     * package that call can't resolve.
     */
    suspend fun listCandidates(): List<DebloatApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val disabledResult = rootExecutor.execute("pm list packages -d -s", 8000L)
        val disabled = if (disabledResult.isSuccess) parsePackageList(disabledResult.stdout) else emptySet()

        val allResult = rootExecutor.execute("pm list packages -s", 8000L)
        if (!allResult.isSuccess) return@withContext emptyList()

        parsePackageList(allResult.stdout)
            .asSequence()
            .filter { it != context.packageName }
            .filter { !isProtected(it) }
            .map { pkg ->
                val label = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
                DebloatApp(packageName = pkg, label = label, enabled = pkg !in disabled)
            }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    private fun parsePackageList(output: String): Set<String> =
        output.lineSequence()
            .mapNotNull { line -> line.substringAfter("package:", "").trim().takeIf { it.isNotEmpty() } }
            .toSet()

    fun isProtected(packageName: String): Boolean =
        packageName in PROTECTED_PACKAGES || PROTECTED_PREFIXES.any { packageName.startsWith(it) }

    suspend fun disable(packageName: String): AppResult<Unit> = runPm("disable-user", packageName, "DEBLOAT_DISABLE")

    suspend fun enable(packageName: String): AppResult<Unit> = runPm("enable", packageName, "DEBLOAT_ENABLE")

    private suspend fun runPm(subcommand: String, packageName: String, action: String): AppResult<Unit> = withContext(Dispatchers.IO) {
        if (isProtected(packageName)) {
            return@withContext AppResult.Failure("$packageName is protected and cannot be changed")
        }
        if (!SAFE_PACKAGE_REGEX.matches(packageName)) {
            return@withContext AppResult.Failure("invalid package name")
        }
        val result = rootExecutor.execute("pm $subcommand --user 0 $packageName", 8000L)
        logRepository.append(
            LogEntry(
                System.currentTimeMillis(), action, packageName, null, subcommand,
                if (result.isSuccess) "SUCCESS" else "FAILED",
                if (result.isSuccess) null else result.stderr.ifBlank { "command failed" }
            )
        )
        if (result.isSuccess) AppResult.Success(Unit) else AppResult.Failure(result.stderr.ifBlank { "command failed" })
    }

    companion object {
        private val SAFE_PACKAGE_REGEX = Regex("^[a-zA-Z0-9_.]+$")

        /**
         * Exact packages this feature will never touch: the OS itself, the
         * settings/launcher/input/telephony/connectivity stack, and the app
         * stores needed to reinstall anything. Not exhaustive by device --
         * [PROTECTED_PREFIXES] below covers whole vendor namespaces too.
         */
        val PROTECTED_PACKAGES = setOf(
            "android",
            "com.android.systemui",
            "com.android.settings",
            "com.android.server.telecom",
            "com.android.phone",
            "com.android.providers.settings",
            "com.android.providers.telephony",
            "com.android.providers.media",
            "com.android.providers.downloads",
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
            "com.android.vending",
            "com.google.android.gms",
            "com.google.android.gsf",
            "com.android.launcher",
            "com.android.launcher3",
            "com.android.inputmethod.latin",
            "com.android.keychain",
            "com.android.shell",
            "com.android.bluetooth",
            "com.android.nfc",
            "com.android.wifi",
            "com.android.wifi.resources",
            "com.android.networkstack",
            "com.android.networkstack.tethering",
            "com.android.connectivity.resources",
            "com.android.cellbroadcastreceiver"
        )

        /** Whole namespaces, not just one package: a kernel/display/telephony vendor HAL or service under one of these is core firmware glue, not bloat. */
        val PROTECTED_PREFIXES = listOf(
            "com.android.server",
            "com.qualcomm.qti.",
            "com.mediatek.",
            "android.ext.",
            "com.google.android.ext.",
            "com.google.android.apps.setupwizard",
            "com.google.android.setupwizard"
        )
    }
}
