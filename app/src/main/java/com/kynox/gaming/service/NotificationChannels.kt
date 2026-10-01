package com.kynox.gaming.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.kynox.gaming.R

object NotificationChannels {
    const val SESSION_CHANNEL_ID = "kynox_game_session"
    const val DETECTION_CHANNEL_ID = "kynox_game_detection"
    const val CHARGING_CHANNEL_ID = "kynox_charging"
    const val OVERLAY_CHANNEL_ID = "kynox_overlay"
    const val EVENTS_CHANNEL_ID = "kynox_events"
    const val MONITOR_CHANNEL_ID = "kynox_monitor"

    fun createAll(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            SESSION_CHANNEL_ID,
            context.getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.notif_channel_desc)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)

        val detectionChannel = NotificationChannel(
            DETECTION_CHANNEL_ID,
            context.getString(R.string.notif_detect_channel_name),
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = context.getString(R.string.notif_detect_channel_desc)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_SECRET
        }
        manager.createNotificationChannel(detectionChannel)

        val chargingChannel = NotificationChannel(
            CHARGING_CHANNEL_ID,
            context.getString(R.string.notif_charging_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.notif_charging_channel_desc)
            setShowBadge(false)
        }
        manager.createNotificationChannel(chargingChannel)

        val overlayChannel = NotificationChannel(
            OVERLAY_CHANNEL_ID,
            context.getString(R.string.notif_overlay_channel_name),
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = context.getString(R.string.notif_overlay_channel_desc)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_SECRET
        }
        manager.createNotificationChannel(overlayChannel)

        val monitorChannel = NotificationChannel(
            MONITOR_CHANNEL_ID,
            context.getString(R.string.notif_monitor_channel_name),
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = context.getString(R.string.notif_monitor_channel_desc)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_SECRET
        }
        manager.createNotificationChannel(monitorChannel)

        val eventsChannel = NotificationChannel(
            EVENTS_CHANNEL_ID,
            context.getString(R.string.notif_events_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notif_events_channel_desc)
            setShowBadge(false)
        }
        manager.createNotificationChannel(eventsChannel)
    }
}
