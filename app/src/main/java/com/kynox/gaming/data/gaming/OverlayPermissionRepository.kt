package com.kynox.gaming.data.gaming

import android.content.Context
import android.provider.Settings
import com.kynox.gaming.core.root.RootExecutor
import com.kynox.gaming.data.logs.LogRepository
import com.kynox.gaming.domain.model.LogEntry

/**
 * Tracks the "display over other apps" permission the FPS overlay needs.
 * With root it is granted directly through appops, so the user does not
 * have to dig through system settings; without root (or if the ROM ignores
 * appops) the screen falls back to sending them to the system settings page.
 */
class OverlayPermissionRepository(
    private val context: Context,
    private val rootExecutor: RootExecutor,
    private val logRepository: LogRepository
) {
    fun isGranted(): Boolean = Settings.canDrawOverlays(context)

    suspend fun ensureGranted(): Boolean {
        if (isGranted()) return true
        if (!rootExecutor.status().isAvailable) return false
        val result = rootExecutor.execute("appops set ${context.packageName} SYSTEM_ALERT_WINDOW allow")
        val granted = isGranted()
        logRepository.append(
            LogEntry(
                System.currentTimeMillis(),
                "GRANT_OVERLAY_PERMISSION",
                "SYSTEM_ALERT_WINDOW",
                "denied",
                "allow",
                if (granted) "SUCCESS" else "FAILED",
                if (granted) null else result.stderr.ifBlank { "Permission still not granted" }
            )
        )
        return granted
    }
}
