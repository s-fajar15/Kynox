package com.kynox.gaming.widget

import android.content.Context
import android.widget.RemoteViews
import com.kynox.gaming.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Widget 2x1 "Baterai": persentase besar, bar, status dan suhu. Ketuk membuka Kynox. */
class KynoxBatteryWidget : KynoxWidgetBase() {
    override val layoutRes = R.layout.widget_battery_small
    override fun rootId() = R.id.wb_root

    override fun bind(context: Context, views: RemoteViews, data: WidgetData, fahrenheit: Boolean) {
        views.setTextViewText(R.id.wb_percent, WidgetFormat.percent(data.batteryPercent))
        views.setProgressBar(R.id.wb_bar, 100, data.batteryPercent ?: 0, false)
        views.setTextViewText(R.id.wb_status, WidgetFormat.chargeStatusLabel(data.status, data.plugged))
        views.setTextViewText(R.id.wb_temp, WidgetFormat.temperature(data.tempTenthsCelsius, fahrenheit))
    }
}
