package com.kynox.gaming.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.kynox.gaming.KynoxApplication
import com.kynox.gaming.MainActivity
import com.kynox.gaming.R
import com.kynox.gaming.core.utils.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAG = "GameSessionService"

/**
 * Foreground service that samples FPS/CPU/GPU/thermal data once a second
 * for the duration of a recording. Has to be a real foreground service (not
 * just a coroutine in a ViewModel) because a gaming session, by definition,
 * happens while Kynox itself is in the background -- a plain background
 * coroutine is exactly what aggressive OEM battery management (MIUI
 * included) is most likely to kill mid-session.
 *
 * While recording it also shows the live FPS overlay, whose stop button and
 * the notification's stop action both end the session from inside the game.
 */
class GameSessionService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var samplingJob: Job? = null
    private var overlayJob: Job? = null
    private var styleJob: Job? = null
    private var overlay: FpsOverlay? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val pkg = intent.getStringExtra(EXTRA_PACKAGE)
                if (pkg == null) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                val label = intent.getStringExtra(EXTRA_LABEL) ?: pkg
                startForeground(NOTIFICATION_ID, buildNotification(label))
                val container = (application as KynoxApplication).container
                val repository = container.gameSessionRepository

                samplingJob?.cancel()
                overlayJob?.cancel()
                overlay?.hide()
                val fpsOverlay = FpsOverlay(this, onResize = { delta -> scope.launch { container.settingsRepository.resizeOverlay(delta) } }) { requestStop() }
                overlay = fpsOverlay
                styleJob?.cancel()
                styleJob = scope.launch {
                    container.settingsRepository.settingsFlow.collect {
                        fpsOverlay.setStyle(it.overlayScalePercent, it.overlayOpacityPercent)
                    }
                }
                overlayJob = scope.launch {
                    repository.live.collect { metrics ->
                        if (fpsOverlay.canShow()) fpsOverlay.show()
                        fpsOverlay.update(metrics)
                    }
                }
                samplingJob = scope.launch {
                    repository.begin(pkg, label)
                    while (isActive) {
                        repository.sampleOnce()
                        delay(1000)
                    }
                }
            }
            ACTION_STOP -> {
                val runningSampler = samplingJob
                samplingJob = null
                overlayJob?.cancel()
                overlayJob = null
                styleJob?.cancel()
                styleJob = null
                overlay?.hide()
                overlay = null
                scope.launch {
                    try {
                        runningSampler?.cancelAndJoin()
                        (application as KynoxApplication).container.gameSessionRepository.finish()
                        showToast(getString(R.string.session_saved_toast))
                    } catch (t: Throwable) {
                        Logger.e(TAG, "Failed to finish session", t)
                    } finally {
                        stopForegroundCompat()
                        stopSelf()
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        samplingJob?.cancel()
        overlayJob?.cancel()
        styleJob?.cancel()
        overlay?.hide()
        super.onDestroy()
    }

    private fun requestStop() {
        startService(Intent(this, GameSessionService::class.java).apply { action = ACTION_STOP })
    }

    private fun showToast(message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= 24) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun buildNotification(label: String?): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, GameSessionService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, NotificationChannels.SESSION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_kynox)
            .setColor(0xFF2F6FED.toInt())
            .setContentTitle(getString(R.string.notif_session_title))
            .setContentText(getString(R.string.notif_session_text, label ?: ""))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openIntent)
            .addAction(0, getString(R.string.btn_stop_recording), stopIntent)
            .build()
    }

    companion object {
        const val ACTION_START = "com.kynox.gaming.action.START_SESSION"
        const val ACTION_STOP = "com.kynox.gaming.action.STOP_SESSION"
        const val EXTRA_PACKAGE = "package"
        const val EXTRA_LABEL = "label"
        private const val NOTIFICATION_ID = 1001
    }
}
