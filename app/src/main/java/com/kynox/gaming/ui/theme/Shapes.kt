package com.kynox.gaming.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

object KynoxShapes {
    val chip = RoundedCornerShape(50)
    val control = RoundedCornerShape(14.dp)
    val section = RoundedCornerShape(22.dp)
    val dialog = RoundedCornerShape(24.dp)

    val material = Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = control,
        medium = RoundedCornerShape(18.dp),
        large = section,
        extraLarge = dialog
    )
}
