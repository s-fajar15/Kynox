package com.kynox.gaming.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kynox.gaming.AppContainer
import com.kynox.gaming.core.utils.Logger
import com.kynox.gaming.data.settings.MAX_CONSECUTIVE_BOOT_FAILURES
import com.kynox.gaming.data.logs.LogRepository
import com.kynox.gaming.domain.model.LogEntry
import com.kynox.gaming.service.ChargingMonitorService
import com.kynox.gaming.service.MonitorService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val TAG = "ProfileBootReceiver"

/**
 * Delay before a profile is (re-)applied after BOOT_COMPLETED, so the rest
 * of the system (SurfaceFlinger, vendor daemons, the boot animation) has
 * finished settling before this process touches any sysfs node. Also gives
 * a genuinely bad write more time to surface as a visible problem (reboot,
 * ANR) *before* [clearBootFailure] runs, rather than racing it.
 */
private const val BOOT_APPLY_DELAY_MS = 15_000L

/**
 * Re-applies the active Performance Profile after a reboot, only when the
 * user turned "Apply on boot" on in Settings. Never touches Fast Charging or
 * Disable Thermal -- those stay manual, confirmation-gated actions -- and
 * never blocks the OS: Android's own boot sequence does not wait on this
 * receiver (goAsync only extends how long *this process* may keep running
 * work in the background; it has no bearing on system boot completion).
 *
 * Boot-loop guard: [registerBootAttempt] increments a persisted counter
 * before anything is touched, and returns the count from BEFORE this boot.
 * [clearBootFailure] only runs after a full cycle (delay + apply attempt,
 * whatever its result) completes without the process dying first. If two
 * boots in a row never reach that point -- i.e. the counter keeps climbing
 * instead of resetting -- Settings has already auto-disabled Apply on Boot
 * and engaged Safe Mode (see [com.kynox.gaming.data.settings.SettingsRepository.registerBootAttempt]),
 * and this receiver applies nothing more until the user exits Safe Mode.
 */
class ProfileBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val container = AppContainer(appContext)
                val settings = container.settingsRepository.settingsFlow.first()

                if (settings.chargingNotification) {
                    ChargingMonitorService.enable(appContext)
                }
                if (settings.monitorEnabled) {
                    MonitorService.start(appContext)
                }

                if (settings.safeModeActive) {
                    Logger.w(TAG, "Safe Mode is active, skipping any profile apply")
                    return@launch
                }
                if (!settings.applyOnBoot) {
                    // Nothing was going to be applied this boot anyway, so this boot
                    // cannot be blamed for a loop -- don't let stale counts linger.
                    container.settingsRepository.clearBootFailure()
                    Logger.d(TAG, "Apply on boot is off, skipping")
                    return@launch
                }

                val previousFailureCount = container.settingsRepository.registerBootAttempt()
                if (previousFailureCount + 1 >= MAX_CONSECUTIVE_BOOT_FAILURES) {
                    Logger.e(TAG, "$previousFailureCount consecutive unresolved boots -- disabling Apply on Boot and engaging Safe Mode")
                    logSafety(container.logRepository, "BOOT_SAFE_MODE_ENGAGED", "unresolved boots=${previousFailureCount + 1}")
                    return@launch
                }

                // Let the system settle before touching sysfs. A device that
                // reboots or hangs during this window will never reach
                // clearBootFailure() below, which is exactly the signal the
                // counter above needs to catch a real bootloop.
                delay(BOOT_APPLY_DELAY_MS)

                val active = container.profileRepository.activeProfile()
                if (active == null) {
                    Logger.d(TAG, "No active profile saved, nothing to re-apply")
                } else {
                    val result = container.profileRepository.apply(active)
                    Logger.i(TAG, "Boot re-apply of $active: $result")
                }

                // Reached the end of the cycle without the process dying: this boot is healthy.
                container.settingsRepository.clearBootFailure()
            } catch (t: Throwable) {
                Logger.e(TAG, "Boot re-apply failed", t)
                // Deliberately NOT calling clearBootFailure() here: a thrown
                // exception during apply is exactly the kind of failure the
                // counter exists to accumulate toward Safe Mode.
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun logSafety(logRepository: LogRepository, action: String, detail: String) {
        runCatching {
            logRepository.append(LogEntry(System.currentTimeMillis(), action, "boot", null, detail, "SAFETY", null))
        }
    }
}
