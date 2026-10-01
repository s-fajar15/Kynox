package com.kynox.gaming.data.battery

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import com.kynox.gaming.core.sysfs.SysfsAccess
import com.kynox.gaming.domain.model.BatteryInfo

/**
 * Reads live battery/charging state via the public BatteryManager API and
 * the sticky ACTION_BATTERY_CHANGED intent, falling back to power_supply
 * sysfs nodes for fields the framework does not expose (e.g. cycle count on
 * older Android versions).
 */
class BatteryInfoReader(private val context: Context) {

    fun read(): BatteryInfo {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        val capacity = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            ?.takeIf { it in 0..100 }
            ?: intent?.let {
                val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level >= 0 && scale > 0) (level * 100 / scale) else null
            }

        val voltageMv = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)?.takeIf { it > 0 }
        val temperature = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)?.takeIf { it > -1000 }
            ?.let { it / 10f }

        val currentUa = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            ?.takeIf { it != Int.MIN_VALUE }

        val chargeCounterUah = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
            ?.takeIf { it != Int.MIN_VALUE && it > 0 }

        val statusInt = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val pluggedInt = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
        val chargingStatus = describeStatus(statusInt, pluggedInt)

        val healthInt = intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1
        val health = describeHealth(healthInt)

        val cycleCount = if (Build.VERSION.SDK_INT >= 34) {
            intent?.getIntExtra("android.os.extra.CYCLE_COUNT", -1)?.takeIf { it >= 0 }
        } else null
            ?: SysfsAccess.readDirect("/sys/class/power_supply/battery/cycle_count")?.toIntOrNull()

        val power = if (voltageMv != null && currentUa != null) {
            kotlin.math.abs(voltageMv.toLong() * currentUa.toLong()) / 1_000_000_000f
        } else null

        return BatteryInfo(
            capacityPercent = capacity,
            voltageMilliVolts = voltageMv,
            currentMicroAmps = currentUa,
            temperatureCelsius = temperature,
            chargingStatus = chargingStatus,
            isPlugged = pluggedInt != 0,
            health = health,
            powerWatts = power,
            chargeCounterMicroAh = chargeCounterUah,
            cycleCount = cycleCount
        )
    }

    private fun describeStatus(status: Int, plugged: Int): String = when (status) {
        BatteryManager.BATTERY_STATUS_CHARGING -> when (plugged) {
            BatteryManager.BATTERY_PLUGGED_USB -> "Charging (USB)"
            BatteryManager.BATTERY_PLUGGED_AC -> "Charging (AC)"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Charging (Wireless)"
            else -> "Charging"
        }
        BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
        BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not charging"
        BatteryManager.BATTERY_STATUS_FULL -> "Full"
        else -> "Unknown"
    }

    private fun describeHealth(health: Int): String = when (health) {
        BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
        BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over voltage"
        BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Unspecified failure"
        BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
        else -> "Unknown"
    }
}
