package com.kynox.gaming.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.kynox.gaming.KynoxApplication
import com.kynox.gaming.domain.model.ProfileType
import com.kynox.gaming.MainActivity
import com.kynox.gaming.R
import com.kynox.gaming.core.utils.Logger
import com.kynox.gaming.data.gaming.DetectionEvent
import com.kynox.gaming.data.thermal.ThermalRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAG = "GameDetectionService"
private const val POLL_INTERVAL_MS = 3000L
private const val SCREEN_OFF_INTERVAL_MS = 10000L
private const val THERMAL_CHECK_EVERY_N_POLLS = 10
private const val THERMAL_WARNING_THRESHOLD_C = 47f
private const val THERMAL_WARNING_RESET_MARGIN_C = 5f

/**
 * Foreground service behind auto Game Mode. It has to be a real foreground
 * service because the whole point is to notice a game opening while Kynox
 * itself is in the background. The polling loop pauses while the screen is
 * off so it costs nothing when the phone is idle.
 */
class GameDetectionService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loopJob: Job? = null
    private val warnedThermalZones = mutableSetOf<String>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val container = (application as KynoxApplication).container
        val repository = container.gameDetectionRepository
        val automation = container.automationRepository

        if (intent?.action == ACTION_STOP) {
            loopJob?.cancel()
            loopJob = null
            scope.launch {
                try {
                    repository.release()
                } catch (t: Throwable) {
                    Logger.e(TAG, "Failed to restore Game Mode on stop", t)
                } finally {
                    stopForegroundCompat()
                    stopSelf()
                }
            }
            return START_NOT_STICKY
        }

        try {
            startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.notif_detect_idle)))
        } catch (t: Throwable) {
            Logger.e(TAG, "Could not start foreground service", t)
            stopSelf()
            return START_NOT_STICKY
        }

        if (loopJob?.isActive == true) return START_STICKY

        val restartedBySystem = intent?.action != ACTION_START
        loopJob = scope.launch {
            if (restartedBySystem && !repository.isEnabled() && !automation.isEnabled()) {
                stopForegroundCompat()
                stopSelf()
                return@launch
            }
            val powerManager = getSystemService(PowerManager::class.java)
            var pollCount = 0
            var lastAutomationPackage: String? = null
            while (isActive) {
                if (powerManager != null && !powerManager.isInteractive) {
                    delay(SCREEN_OFF_INTERVAL_MS)
                    continue
                }
                val event = try {
                    repository.poll(packageName)
                } catch (c: CancellationException) {
                    throw c
                } catch (t: Throwable) {
                    Logger.e(TAG, "Detection poll failed", t)
                    DetectionEvent.None
                }
                when (event) {
                    is DetectionEvent.GameStarted -> {
                        GameEventNotifier.notifyProfileApplied(
                            applicationContext,
                            getString(R.string.profile_gaming_label),
                            event.label
                        )
                    }
                    is DetectionEvent.GameStopped -> Unit
                    DetectionEvent.None -> Unit
                }

                if (automation.isEnabled()) {
                    val foreground = container.foregroundAppReader.read()
                    if (foreground != null && foreground != packageName && foreground != lastAutomationPackage) {
                        automation.list().firstOrNull { it.enabled && it.packageName == foreground }?.let { rule ->
                            rule.refreshRateHz?.let { hz ->
                                container.refreshRateRepository.setOverride(foreground, hz)
                                container.refreshRateRepository.applyForPackage(foreground)
                            }
                            rule.profile?.let { value ->
                                runCatching { ProfileType.valueOf(value) }.getOrNull()?.let { container.profileRepository.apply(it) }
                            }
                            lastAutomationPackage = foreground
                        }
                    }
                    if (foreground == null || foreground == packageName) lastAutomationPackage = null
                }

                pollCount++
                if (pollCount % THERMAL_CHECK_EVERY_N_POLLS == 0) {
                    checkThermalWarning(container.thermalRepository)
                }

                delay(POLL_INTERVAL_MS)
            }
        }
        return START_STICKY
    }

    private suspend fun checkThermalWarning(thermalRepository: ThermalRepository) {
        val zones = try {
            thermalRepository.readZones()
        } catch (t: Throwable) {
            Logger.e(TAG, "Thermal check failed", t)
            return
        }
        zones.forEach { zone ->
            val temperature = zone.temperatureCelsius ?: return@forEach
            val tripThreshold = zone.tripPoints.mapNotNull { it.temperatureCelsius }.minOrNull()
            val threshold = tripThreshold?.minus(THERMAL_WARNING_RESET_MARGIN_C) ?: THERMAL_WARNING_THRESHOLD_C
            when {
                temperature >= threshold && warnedThermalZones.add(zone.zoneName) -> {
                    GameEventNotifier.notifyThermalWarning(applicationContext, zone.sensorType, temperature)
                }
                temperature < threshold - THERMAL_WARNING_RESET_MARGIN_C -> {
                    warnedThermalZones.remove(zone.zoneName)
                }
            }
        }
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

    private fun buildNotification(text: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, NotificationChannels.DETECTION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_kynox)
            .setContentTitle(getString(R.string.notif_detect_title))
            .setContentText(text)
            .setOngoing(false)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setContentIntent(openIntent)
            .build()
    }

    companion object {
        const val ACTION_START = "com.kynox.gaming.action.START_DETECTION"
        const val ACTION_STOP = "com.kynox.gaming.action.STOP_DETECTION"
        private const val NOTIFICATION_ID = 1002
    }
}
