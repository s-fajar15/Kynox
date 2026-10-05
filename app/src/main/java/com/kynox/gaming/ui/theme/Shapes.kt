package com.kynox.gaming.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

object KynoxShapes {
    val chip = RoundedCornerShape(50)
    val pill = RoundedCornerShape(50)
    val control = RoundedCornerShape(16.dp)
    val section = RoundedCornerShape(26.dp)
    val hero = RoundedCornerShape(32.dp)
    val dialog = RoundedCornerShape(28.dp)

    val material = Shapes(
        extraSmall = RoundedCornerShape(10.dp),
        small = control,
        medium = RoundedCornerShape(20.dp),
        large = section,
        extraLarge = dialog
    )
}
