package com.kynox.gaming.core.utils

private const val PLAUSIBLE_MIN_C = -40f
private const val PLAUSIBLE_MAX_C = 150f

/**
 * Normalizes a raw sysfs temperature reading to Celsius.
 *
 * Kernels report `temp` nodes at different scales -- millidegree (the
 * documented kernel thermal ABI, e.g. 45000 for 45.0C), decidegree (common
 * on several MSM/Qualcomm zones, e.g. 450), or already whole Celsius (some
 * virtual zones) -- so every candidate scale is tried and the first one
 * that lands in a physically plausible device temperature wins.
 *
 * A disabled or unwired sensor often reports a reading that is not a
 * failed read but is not a real temperature either: a huge, physically
 * impossible value at every scale (observed on a disabled thermal zone),
 * or a suspiciously flat 0 on some kgsl GPU temp nodes that exist but were
 * never wired to real hardware. Both are treated as "no reading" here
 * rather than shown as-is.
 */
fun normalizeSensorTemperature(raw: Float, treatZeroAsMissing: Boolean = false): Float? {
    val candidate = listOf(raw, raw / 10f, raw / 1000f).firstOrNull { it in PLAUSIBLE_MIN_C..PLAUSIBLE_MAX_C }
    if (candidate == null || (treatZeroAsMissing && candidate == 0f)) return null
    return candidate
}
