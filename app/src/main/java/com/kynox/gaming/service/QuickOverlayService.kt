package com.kynox.gaming.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.kynox.gaming.KynoxApplication
import com.kynox.gaming.MainActivity
import com.kynox.gaming.R
import com.kynox.gaming.data.gaming.FpsMeter
import com.kynox.gaming.domain.model.OverlaySettings
import com.kynox.gaming.domain.model.QuickOverlayMetrics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAG = "QuickOverlayService"

/**
 * Standalone performance overlay -- FPS, CPU usage, GPU usage/frequency and
 * battery temperature, whichever the user enabled in Settings. Unlike
 * [GameSessionService] this never records a session or writes a report; it
 * is only a live readout for whatever app is in front -- games or not, so an
 * idle screen shows 0 FPS rather than nothing.
 */
class QuickOverlayService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var styleJob: Job? = null
    private var overlay: MetricsOverlay? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForeground(NOTIFICATION_ID, buildNotification())
                val container = (application as KynoxApplication).container

                job?.cancel()
                overlay?.hide()
                val metricsOverlay = MetricsOverlay(this) { requestStop() }
                overlay = metricsOverlay

                styleJob?.cancel()
                styleJob = scope.launch {
                    container.settingsRepository.settingsFlow.collect {
                        metricsOverlay.setStyle(it.overlayScalePercent, it.overlayOpacityPercent)
                    }
                }
                job = scope.launch {
                    val settings = container.settingsRepository.settingsFlow.first()
                    val overlaySettings = OverlaySettings(
                        showFps = settings.overlayShowFps,
                        showCpu = settings.overlayShowCpu,
                        showGpu = settings.overlayShowGpu,
                        showBatteryTemp = settings.overlayShowBatteryTemp
                    )
                    val fpsMeter = FpsMeter(container.rootExecutor, allowIdle = true)
                    var lastForeground: String? = null
                    _isActive.value = true
                    while (isActive) {
                        val fps = if (overlaySettings.showFps) {
                            val pkg = container.foregroundAppReader.read() ?: lastForeground
                            if (pkg != null) {
                                lastForeground = pkg
                                fpsMeter.sample(pkg)?.fps
                            } else null
                        } else null

                        val cpuUsage = if (overlaySettings.showCpu) {
                            container.cpuRepository.readSnapshot().overallUsagePercent
                        } else null

                        val gpu = if (overlaySettings.showGpu) container.gpuRepository.readState() else null

                        val batteryTemp = if (overlaySettings.showBatteryTemp) {
                            container.batteryRepository.readInfo().temperatureCelsius
                        } else null

                        val metrics = QuickOverlayMetrics(
                            fps = fps,
                            cpuUsagePercent = cpuUsage,
                            gpuUsagePercent = gpu?.utilizationPercent,
                            gpuFreqRaw = gpu?.currentFreqKhz,
                            batteryTempCelsius = batteryTemp
                        )
                        if (metricsOverlay.canShow()) metricsOverlay.show()
                        metricsOverlay.update(metrics, overlaySettings)
                        delay(1000)
                    }
                }
            }
            ACTION_STOP -> {
                job?.cancel()
                job = null
                styleJob?.cancel()
                styleJob = null
                overlay?.hide()
                overlay = null
                _isActive.value = false
                stopForegroundCompat()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        job?.cancel()
        styleJob?.cancel()
        overlay?.hide()
        _isActive.value = false
        super.onDestroy()
    }

    private fun requestStop() {
        startService(Intent(this, QuickOverlayService::class.java).apply { action = ACTION_STOP })
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
            Intent(this, QuickOverlayService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, NotificationChannels.OVERLAY_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_kynox)
            .setContentTitle(getString(R.string.notif_overlay_title))
            .setContentText(getString(R.string.notif_overlay_text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openIntent)
            .addAction(0, getString(R.string.btn_stop_overlay), stopIntent)
            .build()
    }

    companion object {
        const val ACTION_START = "com.kynox.gaming.action.START_QUICK_OVERLAY"
        const val ACTION_STOP = "com.kynox.gaming.action.STOP_QUICK_OVERLAY"
        private const val NOTIFICATION_ID = 1005

        private val _isActive = MutableStateFlow(false)
        val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

        fun start(context: android.content.Context) {
            val intent = Intent(context, QuickOverlayService::class.java).apply { action = ACTION_START }
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
        }

        fun stop(context: android.content.Context) {
            context.startService(Intent(context, QuickOverlayService::class.java).apply { action = ACTION_STOP })
        }
    }
}
