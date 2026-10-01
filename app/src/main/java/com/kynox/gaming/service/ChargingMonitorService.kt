package com.kynox.gaming.service

import android.app.Notification
import android.app.NotificationManager
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
import com.kynox.gaming.core.utils.AppResult
import com.kynox.gaming.core.utils.Logger
import com.kynox.gaming.data.settings.AppSettings
import com.kynox.gaming.domain.model.BatteryInfo
import com.kynox.gaming.domain.model.CHARGE_LIMIT_RESUME_HYSTERESIS
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

private const val TAG = "ChargingMonitor"
private const val POLL_INTERVAL_MS = 2500L
/** Two consecutive unplugged reads before stopping, so a brief USB blip doesn't flash the notification off and back on. */
private const val STOP_AFTER_MISSES = 2
private val sourcePattern = Regex("""\((.+)\)""")

/**
 * Optional (Settings > Notifikasi) foreground service that shows a live
 * charging notification -- level, current, power, voltage, battery temp --
 * refreshed every ~2.5s, only while a charger is actually connected: the
 * moment it's unplugged this removes the notification and stops itself
 * completely, re-arming [ChargingJobService] so the next connection wakes
 * it again even if Kynox's process gets killed in between.
 *
 * Also enforces the charge-limit feature (Battery screen) in this same
 * loop, rather than a second service -- one charging session should not
 * mean two notifications. [BatteryInfo.isPlugged] (from
 * `EXTRA_PLUGGED`, not the "charging" status) is what keeps the loop alive
 * while paused at the limit, since pausing charging itself makes the
 * framework report "not charging" even though the cable is still in.
 */
class ChargingMonitorService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loopJob: Job? = null
    private var settingsJob: Job? = null
    private val settings = MutableStateFlow(AppSettings())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopMonitoring(rearm = false)
            return START_NOT_STICKY
        }

        try {
            startForeground(NOTIFICATION_ID, chargingNotification(null, paused = false, limitPercent = 0))
        } catch (t: Throwable) {
            Logger.e(TAG, "Could not start foreground service", t)
            stopSelf()
            return START_NOT_STICKY
        }
        if (loopJob?.isActive == true) return START_NOT_STICKY

        val container = (application as KynoxApplication).container
        val batteryRepository = container.batteryRepository
        val manager = getSystemService(NotificationManager::class.java)
        var misses = 0
        var pausedByLimit = false

        settingsJob?.cancel()
        settingsJob = scope.launch {
            container.settingsRepository.settingsFlow.collect { settings.value = it }
        }

        loopJob = scope.launch {
            while (isActive) {
                val info = try {
                    batteryRepository.readInfo()
                } catch (c: CancellationException) {
                    throw c
                } catch (t: Throwable) {
                    Logger.e(TAG, "Battery read failed", t)
                    null
                }

                if (info?.isPlugged != true) {
                    misses++
                    if (misses >= STOP_AFTER_MISSES) {
                        Logger.i(TAG, "Charger disconnected, stopping")
                        if (pausedByLimit) batteryRepository.resumeCharging()
                        stopMonitoring(rearm = true)
                        return@launch
                    }
                } else {
                    misses = 0
                    val current = settings.value
                    val capacity = info.capacityPercent
                    if (current.chargeLimitEnabled && capacity != null) {
                        val limit = current.chargeLimitPercent
                        if (!pausedByLimit && capacity >= limit) {
                            val result = batteryRepository.pauseCharging()
                            if (result is AppResult.Success) pausedByLimit = true
                        } else if (pausedByLimit && capacity <= limit - CHARGE_LIMIT_RESUME_HYSTERESIS) {
                            batteryRepository.resumeCharging()
                            pausedByLimit = false
                        }
                    } else if (pausedByLimit) {
                        // Limit turned off (or capacity became unreadable) while paused -- don't leave it paused.
                        batteryRepository.resumeCharging()
                        pausedByLimit = false
                    }
                    manager?.notify(
                        NOTIFICATION_ID,
                        chargingNotification(info, paused = pausedByLimit, limitPercent = current.chargeLimitPercent)
                    )
                }
                delay(POLL_INTERVAL_MS)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        loopJob?.cancel()
        settingsJob?.cancel()
        super.onDestroy()
    }

    private fun stopMonitoring(rearm: Boolean) {
        loopJob?.cancel()
        loopJob = null
        settingsJob?.cancel()
        stopForegroundCompat()
        stopSelf()
        if (rearm) ChargingJobService.schedule(applicationContext)
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= 24) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE
    )

    private fun chargingNotification(info: BatteryInfo?, paused: Boolean, limitPercent: Int): Notification {
        val level = info?.capacityPercent ?: 0
        val currentMa = info?.currentMicroAmps?.let { abs(it) / 1000 }
        val source = info?.chargingStatus?.let { sourcePattern.find(it)?.groupValues?.get(1) }
            ?.let { if (it == "Wireless") "Nirkabel" else it }

        val power = info?.powerWatts?.let { String.format("%.1f W", it) }
        val current = currentMa?.let { "$it mA" }
        val temperature = info?.temperatureCelsius?.let { String.format("%.1f\u00B0C", it) }
        val voltage = info?.voltageMilliVolts?.let { String.format("%.2f V", it / 1000f) }

        val title = if (paused) {
            getString(R.string.notif_charging_paused_title, level, limitPercent)
        } else {
            getString(R.string.notif_charging_title, level)
        }

        val compactParts = listOfNotNull(power, current, temperature)
        val compactText = if (compactParts.isNotEmpty()) {
            compactParts.joinToString(" \u00B7 ")
        } else {
            getString(R.string.notif_charging_compact_waiting)
        }

        val detailLines = mutableListOf<String>()
        if (paused) detailLines.add(getString(R.string.notif_charging_paused_text))
        if (power != null || current != null) {
            detailLines.add(listOfNotNull(power, current).joinToString(" \u00B7 "))
        }
        if (voltage != null || temperature != null) {
            detailLines.add(listOfNotNull(voltage, temperature).joinToString(" \u00B7 "))
        }
        info?.cycleCount?.let {
            detailLines.add(getString(R.string.notif_charging_line_cycles, it))
        }

        val builder = NotificationCompat.Builder(this, NotificationChannels.CHARGING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_kynox)
            .setContentTitle(title)
            .setContentText(compactText)
            .setSubText(source?.let { "Kynox \u00B7 $it" } ?: "Kynox")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle(title)
                    .bigText(detailLines.joinToString("\n"))
            )
            .setProgress(100, level, false)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setContentIntent(openAppIntent())

        return builder.build()
    }

    companion object {
        const val ACTION_STOP = "com.kynox.gaming.action.STOP_CHARGING_MONITOR"
        private const val NOTIFICATION_ID = 1003

        fun start(context: Context) {
            try {
                if (Build.VERSION.SDK_INT >= 26) {
                    context.startForegroundService(Intent(context, ChargingMonitorService::class.java))
                } else {
                    context.startService(Intent(context, ChargingMonitorService::class.java))
                }
            } catch (t: Throwable) {
                Logger.e(TAG, "Could not start charging monitor", t)
            }
        }

        /** Stops the service (if running) and removes any pending wake-up job. Used when the user turns the feature off. */
        fun stopAndDisarm(context: Context) {
            val intent = Intent(context, ChargingMonitorService::class.java).apply { action = ACTION_STOP }
            try {
                context.startService(intent)
            } catch (t: Throwable) {
                Logger.e(TAG, "Could not stop charging monitor", t)
            }
            ChargingJobService.cancel(context)
        }

        /** Arms the feature: starts monitoring right away if already charging, otherwise waits for [ChargingJobService]. */
        fun enable(context: Context) {
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? android.os.BatteryManager
            val charging = batteryManager?.isCharging == true
            if (charging) start(context) else ChargingJobService.schedule(context)
        }
    }
}
