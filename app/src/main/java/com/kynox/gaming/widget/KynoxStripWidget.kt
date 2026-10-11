package com.kynox.gaming.widget

import android.content.Context
import android.widget.RemoteViews
import com.kynox.gaming.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Widget 4x1 "Strip": satu baris baterai, suhu, RAM, dan tombol overlay. */
class KynoxStripWidget : KynoxWidgetBase() {
    override val layoutRes = R.layout.widget_strip
    override fun rootId() = R.id.ws_root
    override fun clickTargets() = listOf(R.id.ws_overlay to ACTION_TOGGLE_OVERLAY)

    override fun bind(context: Context, views: RemoteViews, data: WidgetData, fahrenheit: Boolean) {
        views.setTextViewText(R.id.ws_battery, WidgetFormat.percent(data.batteryPercent))
        views.setTextViewText(R.id.ws_temp, WidgetFormat.temperature(data.tempTenthsCelsius, fahrenheit))
        views.setTextViewText(R.id.ws_ram, WidgetFormat.percent(data.ramPercent))
        views.setTextViewText(R.id.ws_overlay, if (data.overlayActive) "Aktif" else "Mati")
        views.setInt(R.id.ws_overlay, "setBackgroundResource", if (data.overlayActive) R.drawable.widget_btn_primary else R.drawable.widget_btn_outline)
        views.setTextColor(R.id.ws_overlay, if (data.overlayActive) 0xFF003736.toInt() else 0xFFE2ECEA.toInt())
    }
}
