package com.kynox.gaming.ui.theme

import androidx.compose.ui.graphics.Brush

/**
 * Kynox 4.0 tidak memakai gradien. Nama [accent] dipertahankan untuk pemakai lama,
 * isinya satu warna rata (primary gelap). Layar baru memakai MaterialTheme.colorScheme.primary.
 */
object KynoxBrushes {
    val accent: Brush = Brush.linearGradient(listOf(KynoxPrimaryDark, KynoxPrimaryDark))
}
