package com.kynox.gaming.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Bentuk 4.0: kartu utama 28 dp, kartu biasa 24 dp, ikon tile 12 dp, chip 10 dp, pill penuh. */
object KynoxShapes {
    /** Kartu utama (ringkasan / hero). */
    val hero = RoundedCornerShape(28.dp)
    /** Kartu biasa. */
    val section = RoundedCornerShape(24.dp)
    val iconTile = RoundedCornerShape(12.dp)
    val chip = RoundedCornerShape(10.dp)
    /** Pill penuh (tombol, indikator, penanda status). */
    val pill = RoundedCornerShape(percent = 50)
    val control = RoundedCornerShape(percent = 50)
    val dialog = RoundedCornerShape(28.dp)
    val circle = CircleShape

    val material = Shapes(
        extraSmall = RoundedCornerShape(10.dp),
        small = RoundedCornerShape(12.dp),
        medium = RoundedCornerShape(24.dp),
        large = RoundedCornerShape(24.dp),
        extraLarge = RoundedCornerShape(28.dp)
    )
}
