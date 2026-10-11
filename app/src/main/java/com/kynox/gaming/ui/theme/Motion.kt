package com.kynox.gaming.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** Gerak singkat dan tenang (semua <= 250 ms). Tidak ada yang berulang atau memantul. */
object KynoxMotion {
    const val FAST = 120
    const val NORMAL = 220
    const val SLOW = 250

    val easing = FastOutSlowInEasing
    val emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    fun <T> fast() = tween<T>(FAST, easing = easing)
    fun <T> normal() = tween<T>(NORMAL, easing = easing)
    fun <T> slow() = tween<T>(SLOW, easing = emphasized)
}

/**
 * Skala animasi sistem (Setelan > Opsi pengembang > Skala durasi animator). 0 = animasi dimatikan.
 * Dipakai supaya transisi mengikuti pengaturan pengguna.
 */
@Composable
fun rememberMotionScale(): Float {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }.getOrDefault(1f).coerceIn(0f, 2f)
    }
}
