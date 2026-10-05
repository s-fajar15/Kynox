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

        val cycleCount = (if (Build.VERSION.SDK_INT >= 34) {
            intent?.getIntExtra("android.os.extra.CYCLE_COUNT", -1)?.takeIf { it > 0 }
        } else null)
            ?: listOf(
                "/sys/class/power_supply/battery/cycle_count",
                "/sys/class/power_supply/bms/cycle_count",
                "/sys/class/power_supply/battery/battery_cycle"
            ).firstNotNullOfOrNull { path ->
                SysfsAccess.readDirect(path)?.trim()?.toIntOrNull()?.takeIf { it >= 0 }
            }

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
            BatteryManager.BATTERY_PLUGGED_USB -> "Mengisi daya (USB)"
            BatteryManager.BATTERY_PLUGGED_AC -> "Mengisi daya (AC)"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Mengisi daya (nirkabel)"
            else -> "Mengisi daya"
        }
        BatteryManager.BATTERY_STATUS_DISCHARGING -> "Baterai terpakai"
        BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Tidak mengisi"
        BatteryManager.BATTERY_STATUS_FULL -> "Penuh"
        else -> "Tidak diketahui"
    }

    private fun describeHealth(health: Int): String = when (health) {
        BatteryManager.BATTERY_HEALTH_GOOD -> "Baik"
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Terlalu panas"
        BatteryManager.BATTERY_HEALTH_DEAD -> "Rusak"
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Tegangan berlebih"
        BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Gangguan tak dikenal"
        BatteryManager.BATTERY_HEALTH_COLD -> "Terlalu dingin"
        else -> "Tidak diketahui"
    }
}
