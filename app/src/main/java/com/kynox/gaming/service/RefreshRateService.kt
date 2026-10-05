package com.kynox.gaming.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.kynox.gaming.KynoxApplication
import com.kynox.gaming.MainActivity
import com.kynox.gaming.R
import com.kynox.gaming.core.utils.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAG = "RefreshRateService"
private const val POLL_INTERVAL_MS = 750L
private const val REPAIR_COOLDOWN_MS = 1500L
private const val NOTIFICATION_ID = 1004

/**
 * Watches the real foreground package and the real active display mode.
 *
 * The previous implementation only checked peak_refresh_rate. On MIUI that
 * can stay at 120 while the actual SurfaceFlinger mode is already back at 60,
 * so Kynox incorrectly believed the game was fixed. This service now checks
 * dumpsys display and repairs the mode when the panel really falls back.
 */
class RefreshRateService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loopJob: Job? = null
    private var previousMatchContentPreference: Int? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val container = (application as KynoxApplication).container

        if (intent?.action == ACTION_STOP) {
            loopJob?.cancel()
            loopJob = null
            scope.launch {
                try {
                    container.refreshRateRepository.clearTrackedGameFrameRateOverrides()
                    container.refreshRateRepository.restoreDeviceDefault()
                    previousMatchContentPreference?.let {
                        container.refreshRateRepository.setMatchContentFrameRatePreference(it)
                    }
                } catch (t: Throwable) {
                    Logger.e(TAG, "Failed to restore default refresh rate on stop", t)
                } finally {
                    previousMatchContentPreference = null
                    stopForegroundCompat()
                    stopSelf()
                }
            }
            return START_NOT_STICKY
        }

        try {
            startForeground(NOTIFICATION_ID, buildNotification())
        } catch (t: Throwable) {
            Logger.e(TAG, "Could not start foreground service", t)
            stopSelf()
            return START_NOT_STICKY
        }

        if (loopJob?.isActive == true) return START_STICKY

        loopJob = scope.launch {
            previousMatchContentPreference = try {
                container.refreshRateRepository.matchContentFrameRatePreference()
            } catch (_: Throwable) {
                null
            }
            // Remove any game FPS intervention left by a previous Kynox run.
            // The per-app refresh policy will add it again only for the game
            // currently in the foreground.
            try {
                container.refreshRateRepository.clearTrackedGameFrameRateOverrides()
            } catch (_: Throwable) {
                // Best effort cleanup of a previous Kynox game override.
            }

            var lastForeground: String? = null
            var lastTarget: Int? = null
            var lastGameOverridePackage: String? = null
            var lastApplyAt = 0L

            while (isActive) {
                val foreground = try {
                    container.foregroundAppReader.read()
                } catch (t: Throwable) {
                    Logger.e(TAG, "Foreground read failed", t)
                    null
                }

                // Never apply the default during the short null window between
                // activities. Wait for a valid foreground package.
                if (foreground != null) {
                    val target = try {
                        container.refreshRateRepository.targetForPackage(foreground)
                    } catch (t: Throwable) {
                        Logger.e(TAG, "Could not resolve refresh-rate target", t)
                        null
                    }

                    if (target != null) {
                        val now = System.currentTimeMillis()
                        val active = try {
                            container.refreshRateRepository.readActiveDisplayRefreshRate()
                        } catch (t: Throwable) {
                            Logger.e(TAG, "Could not read active display refresh rate", t)
                            null
                        }
                        val setting = try {
                            container.refreshRateRepository.currentAppliedHz()
                        } catch (_: Throwable) {
                            null
                        }

                        val packageChanged = foreground != lastForeground
                        val targetChanged = target != lastTarget
                        val activeMismatch = active != null && active != target
                        val settingMismatch = active == null && setting != null && setting != target
                        val needsRepair = activeMismatch || settingMismatch
                        val cooldownElapsed = now - lastApplyAt >= REPAIR_COOLDOWN_MS

                        if (packageChanged && lastGameOverridePackage != null && lastGameOverridePackage != foreground) {
                            try {
                                container.refreshRateRepository.clearGameFrameRateOverride(lastGameOverridePackage!!)
                            } catch (_: Throwable) {
                                // Continue applying the display mode even if GameManager is unavailable.
                            }
                            lastGameOverridePackage = null
                        }

                        if (packageChanged || targetChanged || (needsRepair && cooldownElapsed)) {
                            // Log only the policy transition. Silent repairs keep
                            // the log readable while still enforcing the mode.
                            container.refreshRateRepository.applyForPackage(
                                foreground,
                                recordLog = packageChanged || targetChanged
                            )
                            if (target > container.refreshRateRepository.defaultRefreshRate()) {
                                lastGameOverridePackage = foreground
                            }
                            lastApplyAt = now
                        }

                        lastForeground = foreground
                        lastTarget = target
                    }
                }
                delay(POLL_INTERVAL_MS)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        loopJob?.cancel()
        super.onDestroy()
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= 24) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        // Android requires a foreground service to have a notification. Keep this
        // service notification intentionally generic and dismissible so the UI
        // never exposes the implementation detail "Refresh Rate per App".
        return NotificationCompat.Builder(this, NotificationChannels.MONITOR_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_kynox)
            .setColor(0xFF2F6FED.toInt())
            .setContentTitle(getString(R.string.notif_service_title))
            .setContentText(getString(R.string.notif_service_text))
            .setOngoing(false)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setContentIntent(openIntent)
            .build()
    }

    companion object {
        const val ACTION_START = "com.kynox.gaming.action.START_REFRESH_RATE"
        const val ACTION_STOP = "com.kynox.gaming.action.STOP_REFRESH_RATE"

        fun start(context: Context) {
            val intent = Intent(context, RefreshRateService::class.java).apply { action = ACTION_START }
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, RefreshRateService::class.java).apply { action = ACTION_STOP })
        }
    }
}
