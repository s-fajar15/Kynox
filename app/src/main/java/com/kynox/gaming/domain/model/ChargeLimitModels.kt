package com.kynox.gaming.domain.model

const val CHARGE_LIMIT_DEFAULT_PERCENT = 80
const val CHARGE_LIMIT_MIN_PERCENT = 50
const val CHARGE_LIMIT_MAX_PERCENT = 100
/** Charging resumes at limit-minus-this, so it doesn't rapidly flip on/off right at the threshold. */
const val CHARGE_LIMIT_RESUME_HYSTERESIS = 3

data class ChargeLimitState(
    val supported: Boolean,
    val activeNodePath: String?,
    val reason: String
)
