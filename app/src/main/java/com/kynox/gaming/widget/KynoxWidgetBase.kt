package com.kynox.gaming.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.RemoteViews
import com.kynox.gaming.KynoxApplication
import com.kynox.gaming.MainActivity
import com.kynox.gaming.data.settings.TemperatureUnit
import com.kynox.gaming.service.QuickOverlayService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Dasar semua widget Kynox: membaca data sekali, menyegarkan SEMUA jenis widget yang terpasang,
 * dan menangani dua aksi nyata -- menyegarkan, dan menyalakan/mematikan overlay cepat.
 *
 * Widget tidak memperbarui diri tiap detik (Android membatasi pembaruan berkala widget;
 * kami meminta tiap 30 menit). Nilai diperbarui saat dipasang, saat tombol segarkan diketuk,
 * dan setelah overlay dinyalakan/dimatikan dari widget.
 */
abstract class KynoxWidgetBase : AppWidgetProvider() {

    abstract val layoutRes: Int

    /** Isi teks/bar widget ini dari [data]. */
    abstract fun bind(context: Context, views: RemoteViews, data: WidgetData, fahrenheit: Boolean)

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        refresh(context, goAsync(), 0L)
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_REFRESH -> refresh(context, goAsync(), 0L)
            ACTION_TOGGLE_OVERLAY -> {
                toggleOverlay(context)
                // Beri waktu service overlay mengubah statusnya sebelum widget digambar ulang.
                refresh(context, goAsync(), 700L)
            }
            else -> super.onReceive(context, intent)
        }
    }

    protected fun refresh(context: Context, pending: PendingResult, delayMs: Long) {
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (delayMs > 0) delay(delayMs)
                val fahrenheit = try {
                    (app as? KynoxApplication)?.container?.settingsRepository?.settingsFlow?.first()
                        ?.temperatureUnit == TemperatureUnit.FAHRENHEIT
                } catch (e: Exception) { false }
                val data = WidgetData.read(app, QuickOverlayService.isActive.value)
                val manager = AppWidgetManager.getInstance(app)
                ALL.forEach { cls ->
                    val ids = manager.getAppWidgetIds(ComponentName(app, cls))
                    if (ids.isEmpty()) return@forEach
                    val provider = cls.getDeclaredConstructor().newInstance()
                    val views = RemoteViews(app.packageName, provider.layoutRes)
                    provider.bind(app, views, data, fahrenheit)
                    provider.attachClicks(app, views, cls)
                    ids.forEach { manager.updateAppWidget(it, views) }
                }
            } finally {
                pending.finish()
            }
        }
    }

    /** Ketuk badan widget membuka Kynox; tombol dipasang oleh [bind] lewat [actionIntent]. */
    private fun attachClicks(context: Context, views: RemoteViews, cls: Class<out KynoxWidgetBase>) {
        views.setOnClickPendingIntent(
            rootId(),
            PendingIntent.getActivity(context, 3, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        )
        clickTargets().forEach { (viewId, action) ->
            views.setOnClickPendingIntent(viewId, actionIntent(context, cls, action))
        }
    }

    /** View akar widget (untuk membuka aplikasi). */
    abstract fun rootId(): Int

    /** Pasangan id view -> aksi ([ACTION_REFRESH] / [ACTION_TOGGLE_OVERLAY]). */
    open fun clickTargets(): List<Pair<Int, String>> = emptyList()

    private fun actionIntent(context: Context, cls: Class<out KynoxWidgetBase>, action: String): PendingIntent =
        PendingIntent.getBroadcast(
            context, action.hashCode(),
            Intent(context, cls).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun toggleOverlay(context: Context) {
        if (QuickOverlayService.isActive.value) {
            QuickOverlayService.stop(context)
            return
        }
        // Tanpa izin "tampil di atas aplikasi" overlay tidak bisa muncul: buka Kynox agar izin bisa diberikan.
        if (!Settings.canDrawOverlays(context)) {
            context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        try {
            QuickOverlayService.start(context)
        } catch (e: RuntimeException) {
            // Android menolak memulai service latar depan dari sini: buka aplikasi sebagai gantinya.
            context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.kynox.gaming.widget.REFRESH"
        const val ACTION_TOGGLE_OVERLAY = "com.kynox.gaming.widget.TOGGLE_OVERLAY"

        /** Semua jenis widget; ditambah di sini bila ada widget baru. */
        val ALL: List<Class<out KynoxWidgetBase>> = listOf(
            KynoxWidgetProvider::class.java,
            KynoxBatteryWidget::class.java,
            KynoxStripWidget::class.java,
            KynoxChargingWidget::class.java,
            KynoxOverlayTileWidget::class.java
        )
    }
}
