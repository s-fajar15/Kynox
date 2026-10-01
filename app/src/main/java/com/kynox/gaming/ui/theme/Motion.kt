package com.kynox.gaming.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween

/** Short, restrained motion. Nothing loops, nothing bounces. */
object KynoxMotion {
    const val FAST = 120
    const val NORMAL = 220
    const val SLOW = 320

    val easing = FastOutSlowInEasing
    val emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    fun <T> fast() = tween<T>(FAST, easing = easing)
    fun <T> normal() = tween<T>(NORMAL, easing = easing)
    fun <T> slow() = tween<T>(SLOW, easing = emphasized)
}
