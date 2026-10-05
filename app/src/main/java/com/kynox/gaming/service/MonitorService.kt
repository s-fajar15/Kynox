package com.kynox.gaming.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.kynox.gaming.KynoxApplication
import com.kynox.gaming.MainActivity
import com.kynox.gaming.R
import com.kynox.gaming.core.utils.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAG = "MonitorService"
private const val SCREEN_OFF_INTERVAL_MS = 15_000L

/**
 * The always-on monitor. It is a foreground service so the system leaves it
 * alone while other apps are open (the same reason [GameSessionService] is
 * one), and it keeps sampling whether or not Kynox itself is on screen.
 * While the screen is off it slows right down, so it costs almost nothing
 * on a phone sitting in a pocket.
 */
class MonitorService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loopJob: Job? = null
    private var powerReceiver: BroadcastReceiver? = null
    private var foregroundStarted = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val container = (application as KynoxApplication).container
        val recorder = container.monitorRecorder

        if (intent?.action == ACTION_STOP) {
            scope.launch { container.settingsRepository.setMonitorEnabled(false) }
            loopJob?.cancel()
            loopJob = null
            recorder.setRunning(false)
            stopForegroundCompat()
            stopSelf()
            return START_NOT_STICKY
        }

        // Already running: leave the notification alone. Posting it again would bring back
        // one the user has just swiped away.
        if (foregroundStarted && loopJob?.isActive == true) return START_STICKY

        try {
            startForeground(NOTIFICATION_ID, buildNotification())
            foregroundStarted = true
        } catch (t: Throwable) {
            Logger.e(TAG, "Could not start monitor service", t)
            stopSelf()
            return START_NOT_STICKY
        }

        if (loopJob?.isActive == true) return START_STICKY

        val restartedBySystem = intent == null
        registerPowerReceiver(container.settingsRepository)
        loopJob = scope.launch {
            if (restartedBySystem && !container.settingsRepository.settingsFlow.first().monitorEnabled) {
                recorder.setRunning(false)
                stopForegroundCompat()
                stopSelf()
                return@launch
            }
            recorder.loadHistory()
            recorder.setRunning(true)
            val powerManager = getSystemService(PowerManager::class.java)
            while (isActive) {
                val settings = container.settingsRepository.settingsFlow.first()
                try {
                    recorder.sampleOnce()
                    if (settings.historyEnabled) {
                        recorder.recordHistory(settings.historyIntervalSec, settings.historyRetentionHours)
                    }
                    checkThermal(container, settings)
                } catch (c: CancellationException) {
                    throw c
                } catch (t: Throwable) {
                    Logger.e(TAG, "Monitor sample failed, skipping this tick", t)
                }
                val interactive = powerManager?.isInteractive != false
                val interval = if (interactive) settings.refreshIntervalMs else SCREEN_OFF_INTERVAL_MS
                delay(interval)
            }
        }
        return START_STICKY
    }

    /**
     * Peringatan suhu berdasarkan ambang pilihan pengguna. Mode "otomatis"
     * butuh titik trip per zona, jadi dibiarkan ke GameDetectionService.
     */
    private fun checkThermal(container: com.kynox.gaming.AppContainer, settings: com.kynox.gaming.data.settings.AppSettings) {
        if (!settings.notifyThermal || settings.thermalWarnThresholdC <= 0) return
        val sample = container.monitorRecorder.samples.value.lastOrNull() ?: return
        val threshold = settings.thermalWarnThresholdC.toFloat()
        val cooldownMs = settings.notifyCooldownMin * 60_000L
        sample.cpuTemp?.let { temp ->
            if (container.thermalWarner.shouldWarn("monitor:cpu", temp, threshold, cooldownMs)) {
                GameEventNotifier.notifyThermalWarning(applicationContext, getString(R.string.thermal_source_cpu), temp)
            }
        }
        sample.batteryTemp?.let { temp ->
            if (container.thermalWarner.shouldWarn("monitor:battery", temp, threshold, cooldownMs)) {
                GameEventNotifier.notifyThermalWarning(applicationContext, getString(R.string.thermal_source_battery), temp)
            }
        }
    }

    /**
     * Charging notification trigger that does not depend on the scheduled job
     * waking up on time: this service is already running, so it hears the
     * charger being plugged in immediately and starts the charging monitor.
     */
    private fun registerPowerReceiver(settingsRepository: com.kynox.gaming.data.settings.SettingsRepository) {
        if (powerReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action != Intent.ACTION_POWER_CONNECTED) return
                scope.launch {
                    try {
                        if (settingsRepository.settingsFlow.first().chargingNotification) {
                            ChargingMonitorService.start(applicationContext)
                        }
                    } catch (t: Throwable) {
                        Logger.e(TAG, "Could not start charging monitor from power event", t)
                    }
                }
            }
        }
        try {
            registerReceiver(receiver, IntentFilter(Intent.ACTION_POWER_CONNECTED))
            powerReceiver = receiver
        } catch (t: Throwable) {
            Logger.e(TAG, "Could not register power receiver", t)
        }
    }

    override fun onCreate() {
        super.onCreate()
        running = true
    }

    override fun onDestroy() {
        running = false
        powerReceiver?.let { runCatching { unregisterReceiver(it) } }
        powerReceiver = null
        loopJob?.cancel()
        (application as? KynoxApplication)?.container?.monitorRecorder?.setRunning(false)
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
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, MonitorService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, NotificationChannels.MONITOR_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_kynox)
            .setColor(0xFF2F6FED.toInt())
            .setContentTitle(getString(R.string.notif_monitor_title))
            .setContentText(getString(R.string.notif_monitor_text))
            .setOngoing(false)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setContentIntent(openIntent)
            .addAction(0, getString(R.string.notif_monitor_stop), stopIntent)
            .build()
    }

    companion object {
        const val ACTION_START = "com.kynox.gaming.action.START_MONITOR"
        const val ACTION_STOP = "com.kynox.gaming.action.STOP_MONITOR"
        private const val NOTIFICATION_ID = 1006

        @Volatile private var running = false

        fun start(context: Context) {
            val intent = Intent(context, MonitorService::class.java).apply { action = ACTION_START }
            try {
                // A plain start is enough (and does not demand a fresh notification) once it is already up.
                if (running || Build.VERSION.SDK_INT < 26) context.startService(intent) else context.startForegroundService(intent)
            } catch (t: Throwable) {
                Logger.e(TAG, "Could not start monitor service", t)
            }
        }

        fun stop(context: Context) {
            try {
                context.startService(Intent(context, MonitorService::class.java).apply { action = ACTION_STOP })
            } catch (t: Throwable) {
                Logger.e(TAG, "Could not stop monitor service", t)
            }
        }
    }
}
