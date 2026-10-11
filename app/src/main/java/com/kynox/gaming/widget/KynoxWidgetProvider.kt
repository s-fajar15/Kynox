package com.kynox.gaming.widget

import android.content.Context
import android.widget.RemoteViews
import com.kynox.gaming.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Widget 4x2 "Ringkasan": baterai, suhu baterai, RAM, tombol overlay dan segarkan. */
class KynoxWidgetProvider : KynoxWidgetBase() {
    override val layoutRes = R.layout.widget_kynox
    override fun rootId() = R.id.widget_root
    override fun clickTargets() = listOf(
        R.id.widget_overlay to ACTION_TOGGLE_OVERLAY,
        R.id.widget_refresh to ACTION_REFRESH
    )

    override fun bind(context: Context, views: RemoteViews, data: WidgetData, fahrenheit: Boolean) {
        views.setTextViewText(R.id.widget_battery, WidgetFormat.percent(data.batteryPercent))
        views.setTextViewText(R.id.widget_temp, WidgetFormat.temperature(data.tempTenthsCelsius, fahrenheit))
        views.setTextViewText(R.id.widget_ram, WidgetFormat.percent(data.ramPercent))
        views.setTextViewText(R.id.widget_updated, SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()))
        views.setTextViewText(R.id.widget_overlay, if (data.overlayActive) "Matikan overlay" else "Overlay")
    }
}
