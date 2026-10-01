package com.kynox.gaming.domain.model

data class BatteryInfo(
    val capacityPercent: Int?,
    val voltageMilliVolts: Int?,
    val currentMicroAmps: Int?,
    val temperatureCelsius: Float?,
    val chargingStatus: String,
    /** True whenever a charger is physically connected, independent of whether charging is paused (e.g. by the charge-limit feature). */
    val isPlugged: Boolean,
    val health: String,
    val powerWatts: Float?,
    val chargeCounterMicroAh: Int?,
    val cycleCount: Int?
)

data class ChargerInfo(
    val supported: Boolean,
    val online: Boolean,
    val chargerType: String,
    val fastChargeProtocol: String,
    val chargerTemperatureCelsius: Float?,
    val negotiatedVoltageMilliVolts: Int?,
    val negotiatedCurrentMilliAmps: Int?,
    val typeCMode: String?
)

data class FastChargingState(
    val supported: Boolean,
    val active: Boolean,
    val advancedActive: Boolean,
    val targetCurrentMa: Int,
    val activeNodePath: String?,
    val activeNodeValue: String?,
    val writableNodeCount: Int,
    val reason: String
)
