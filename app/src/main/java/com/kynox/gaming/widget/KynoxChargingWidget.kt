package com.kynox.gaming.widget

import android.content.Context
import android.widget.RemoteViews
import com.kynox.gaming.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Widget 3x2 "Pengisian": status, arus, daya, tegangan, dan bar baterai. Tombol segarkan. */
class KynoxChargingWidget : KynoxWidgetBase() {
    override val layoutRes = R.layout.widget_charging
    override fun rootId() = R.id.wc_root
    override fun clickTargets() = listOf(R.id.wc_refresh to ACTION_REFRESH)

    override fun bind(context: Context, views: RemoteViews, data: WidgetData, fahrenheit: Boolean) {
        views.setTextViewText(R.id.wc_status, WidgetFormat.chargeStatusLabel(data.status, data.plugged))
        views.setTextViewText(R.id.wc_percent, WidgetFormat.percent(data.batteryPercent))
        views.setProgressBar(R.id.wc_bar, 100, data.batteryPercent ?: 0, false)
        views.setTextViewText(R.id.wc_current, WidgetFormat.milliAmps(data.currentMilliAmps))
        views.setTextViewText(R.id.wc_power, WidgetFormat.watts(data.voltageMilliVolts, data.currentMilliAmps))
        views.setTextViewText(R.id.wc_voltage, WidgetFormat.voltage(data.voltageMilliVolts))
        views.setTextViewText(R.id.wc_temp, WidgetFormat.temperature(data.tempTenthsCelsius, fahrenheit))
        views.setTextViewText(R.id.wc_updated, SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()))
    }
}
