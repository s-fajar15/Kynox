package com.kynox.gaming.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.kynox.gaming.MainActivity
import com.kynox.gaming.R

/**
 * One-shot event notifications, separate from the silent, ongoing "game
 * detection" status notification. That one only ever says whether Kynox is
 * watching for a game right now; anything that actually *happened* --
 * a profile getting applied, a thermal zone crossing into warning range --
 * is posted once here instead, on its own channel, so it can alert and then
 * go away like a normal notification.
 */
object GameEventNotifier {

    private const val PROFILE_APPLIED_ID = 2001
    private const val THERMAL_WARNING_ID = 2002
    private const val RESTORED_ID = 2003
    private const val AUTOMATION_ID = 2004

    /** Event notifications remove themselves after this long, so they never pile up in the shade. */
    private const val EVENT_TIMEOUT_MS = 15_000L

    fun notifyProfileApplied(context: Context, profileLabel: String, gameLabel: String?) {
        val text = if (gameLabel != null) {
            context.getString(R.string.notif_profile_applied_text_for_game, profileLabel, gameLabel)
        } else {
            context.getString(R.string.notif_profile_applied_text, profileLabel)
        }
        post(context, PROFILE_APPLIED_ID, context.getString(R.string.notif_profile_applied_title), text)
    }

    fun notifyThermalWarning(context: Context, zoneLabel: String, temperatureCelsius: Float) {
        val text = context.getString(R.string.notif_thermal_warning_text, zoneLabel, temperatureCelsius)
        post(context, THERMAL_WARNING_ID, context.getString(R.string.notif_thermal_warning_title), text)
    }

    /** [detail] sudah berupa teks siap tampil, mis. "Profil Performa · 120 Hz". */
    fun notifyAutomationApplied(context: Context, appLabel: String, detail: String) {
        post(
            context, AUTOMATION_ID, context.getString(R.string.notif_profile_applied_title),
            context.getString(R.string.notif_automation_applied_text, appLabel, detail)
        )
    }

    fun notifyRestored(context: Context, appLabel: String) {
        post(
            context, RESTORED_ID, context.getString(R.string.notif_restored_title),
            context.getString(R.string.notif_restored_text, appLabel)
        )
    }

    private fun post(context: Context, id: Int, title: String, text: String) {
        val openIntent = PendingIntent.getActivity(
            context, id,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, NotificationChannels.EVENTS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_kynox)
            .setColor(0xFF2F6FED.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setTimeoutAfter(EVENT_TIMEOUT_MS)
            .setOnlyAlertOnce(false)
            .setContentIntent(openIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        context.getSystemService(NotificationManager::class.java)?.notify(id, notification)
    }
}
