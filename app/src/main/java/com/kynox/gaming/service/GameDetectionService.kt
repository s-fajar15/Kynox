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
import com.kynox.gaming.data.automation.AutomationEvent
import com.kynox.gaming.data.gaming.DetectionEvent
import com.kynox.gaming.data.notify.thermalWarnThreshold
import com.kynox.gaming.data.settings.AppSettings
import com.kynox.gaming.data.thermal.ThermalRepository
import com.kynox.gaming.domain.model.labelRes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAG = "GameDetectionService"
private const val POLL_INTERVAL_MS = 3000L
private const val SCREEN_OFF_INTERVAL_MS = 10000L
private const val THERMAL_CHECK_EVERY_N_POLLS = 10
/** Berapa polling berturut-turut tanpa fitur aktif sebelum service berhenti sendiri (memberi waktu pengaturan tersimpan). */
private const val IDLE_POLLS_BEFORE_STOP = 3

/**
 * Foreground service di balik Game Mode otomatis dan Automation Rules. Harus
 * foreground service karena tugasnya mengenali game yang dibuka saat Kynox
 * sendiri ada di latar belakang. Polling berhenti saat layar mati.
 *
 * Satu polling membaca aplikasi di depan sekali, lalu dipakai oleh dua hal:
 * Game Mode otomatis (game di Perpustakaan Game) dan Automation Rules (aturan
 * per aplikasi). Keduanya mengembalikan pengaturan sebelumnya saat aplikasinya
 * ditinggalkan. Service berhenti sendiri bila keduanya dimatikan.
 */
class GameDetectionService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loopJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val container = (application as KynoxApplication).container
        val repository = container.gameDetectionRepository
        val automation = container.automationRepository

        if (intent?.action == ACTION_STOP) {
            scope.launch {
                try {
                    repository.release()
                } catch (t: Throwable) {
                    Logger.e(TAG, "Failed to restore Game Mode on stop", t)
                }
                // Automation Rules tetap jalan walau Game Mode otomatis dimatikan.
                if (runCatching { automation.isEnabled() }.getOrDefault(false)) return@launch
                try {
                    container.automationCoordinator.release()
                } catch (t: Throwable) {
                    Logger.e(TAG, "Failed to restore automation on stop", t)
                }
                loopJob?.cancel()
                loopJob = null
                stopForegroundCompat()
                stopSelf()
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
            var idlePolls = 0
            while (isActive) {
                if (powerManager != null && !powerManager.isInteractive) {
                    delay(SCREEN_OFF_INTERVAL_MS)
                    continue
                }
                val detectionOn = repository.isEnabled()
                val automationOn = automation.isEnabled()
                if (!detectionOn && !automationOn) {
                    runCatching { container.automationCoordinator.release() }
                    idlePolls++
                    if (idlePolls >= IDLE_POLLS_BEFORE_STOP) {
                        stopForegroundCompat()
                        stopSelf()
                        return@launch
                    }
                    delay(POLL_INTERVAL_MS)
                    continue
                }
                idlePolls = 0

                val settings = try {
                    container.settingsRepository.settingsFlow.first()
                } catch (t: Throwable) {
                    AppSettings()
                }
                val cooldownMs = settings.notifyCooldownMin * 60_000L

                var foreground: String? = null
                if (detectionOn) {
                    val event = try {
                        repository.poll(packageName)
                    } catch (c: CancellationException) {
                        throw c
                    } catch (t: Throwable) {
                        Logger.e(TAG, "Detection poll failed", t)
                        DetectionEvent.None
                    }
                    foreground = repository.lastForeground
                    handleDetectionEvent(container, event, settings, cooldownMs)
                } else {
                    foreground = try {
                        container.foregroundAppReader.read()
                    } catch (c: CancellationException) {
                        throw c
                    } catch (t: Throwable) {
                        null
                    }
                }

                try {
                    val gameModeActive = automationOn && container.gamingModeRepository.isActive()
                    val autoEvent = container.automationCoordinator.onForeground(foreground, packageName, gameModeActive)
                    handleAutomationEvent(container, autoEvent, settings, cooldownMs)
                } catch (c: CancellationException) {
                    throw c
                } catch (t: Throwable) {
                    Logger.e(TAG, "Automation step failed", t)
                }

                pollCount++
                if (settings.notifyThermal && pollCount % THERMAL_CHECK_EVERY_N_POLLS == 0) {
                    checkThermalWarning(container, settings)
                }

                delay(POLL_INTERVAL_MS)
            }
        }
        return START_STICKY
    }

    private suspend fun handleDetectionEvent(
        container: com.kynox.gaming.AppContainer,
        event: DetectionEvent,
        settings: AppSettings,
        cooldownMs: Long
    ) {
        if (!settings.notifyProfileApplied) return
        when (event) {
            is DetectionEvent.GameStarted -> {
                if (!container.notifyThrottle.tryAcquire("apply:" + event.packageName, cooldownMs)) return
                val profile = container.gameLibraryRepository.profileFor(event.packageName) ?: ProfileType.GAMING
                GameEventNotifier.notifyProfileApplied(applicationContext, getString(profile.labelRes()), event.label)
            }
            is DetectionEvent.GameStopped -> {
                if (!container.notifyThrottle.tryAcquire("restore:" + event.packageName, cooldownMs)) return
                GameEventNotifier.notifyRestored(applicationContext, container.gameLibraryRepository.labelFor(event.packageName))
            }
            DetectionEvent.None -> Unit
        }
    }

    private fun handleAutomationEvent(
        container: com.kynox.gaming.AppContainer,
        event: AutomationEvent,
        settings: AppSettings,
        cooldownMs: Long
    ) {
        if (!settings.notifyProfileApplied) return
        when (event) {
            is AutomationEvent.Applied -> {
                if (!container.notifyThrottle.tryAcquire("apply:" + event.packageName, cooldownMs)) return
                val parts = listOfNotNull(
                    event.profile?.let { getString(R.string.notif_detail_profile, getString(it.labelRes())) },
                    event.refreshRateHz?.let { getString(R.string.notif_detail_refresh, it) }
                )
                if (parts.isEmpty()) return
                GameEventNotifier.notifyAutomationApplied(applicationContext, event.label, parts.joinToString(" · "))
            }
            is AutomationEvent.Restored -> {
                if (!container.notifyThrottle.tryAcquire("restore:" + event.packageName, cooldownMs)) return
                GameEventNotifier.notifyRestored(applicationContext, event.label)
            }
            AutomationEvent.None -> Unit
        }
    }

    private suspend fun checkThermalWarning(container: com.kynox.gaming.AppContainer, settings: AppSettings) {
        val thermalRepository: ThermalRepository = container.thermalRepository
        val zones = try {
            thermalRepository.readZones()
        } catch (t: Throwable) {
            Logger.e(TAG, "Thermal check failed", t)
            return
        }
        val cooldownMs = settings.notifyCooldownMin * 60_000L
        zones.forEach { zone ->
            val temperature = zone.temperatureCelsius ?: return@forEach
            val lowestTrip = zone.tripPoints.mapNotNull { it.temperatureCelsius }.minOrNull()
            val threshold = thermalWarnThreshold(settings.thermalWarnThresholdC, lowestTrip)
            if (container.thermalWarner.shouldWarn(zone.zoneName, temperature, threshold, cooldownMs)) {
                GameEventNotifier.notifyThermalWarning(applicationContext, zone.sensorType, temperature)
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
            .setColor(0xFF2F6FED.toInt())
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
