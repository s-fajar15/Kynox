package com.kynox.gaming.widget

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

/** Satu pembacaan data yang dipakai semua widget (tanpa root, langsung dari Android). */
data class WidgetData(
    val batteryPercent: Int?,
    val tempTenthsCelsius: Int?,
    val status: Int,
    val plugged: Boolean,
    val voltageMilliVolts: Int?,
    val currentMilliAmps: Int?,
    val ramPercent: Int?,
    val overlayActive: Boolean
) {
    companion object {
        fun read(context: Context, overlayActive: Boolean): WidgetData {
            val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val temp = battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val plugged = (battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
            val volt = battery?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)?.takeIf { it > 0 }
            val manager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            val rawCurrent = try { manager?.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) } catch (_: Throwable) { null }
            val memory = ActivityManager.MemoryInfo()
            (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(memory)
            return WidgetData(
                batteryPercent = WidgetFormat.batteryPercent(level, scale),
                tempTenthsCelsius = temp,
                status = status,
                plugged = plugged,
                voltageMilliVolts = volt,
                currentMilliAmps = WidgetFormat.currentMilliAmps(rawCurrent),
                ramPercent = WidgetFormat.ramPercent(memory.availMem, memory.totalMem),
                overlayActive = overlayActive
            )
        }
    }
}
