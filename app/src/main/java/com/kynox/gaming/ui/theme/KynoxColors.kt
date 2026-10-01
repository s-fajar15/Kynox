package com.kynox.gaming.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/** Colour roles Material's ColorScheme has no slot for. Read through [kynoxColors]. */
data class KynoxColorRoles(
    val surfaceSunken: Color,
    val surfaceRaised: Color,
    val outlineStrong: Color,
    val onSurfaceFaint: Color,
    val accentContainer: Color,
    /** Second chart series (GPU), distinguishable from the accent (CPU). */
    val seriesSecondary: Color,
    /** Faint grid line colour for charts. */
    val grid: Color,
    /** The bottom nav's own colour -- always dark, on both themes (per mockup). */
    val navBar: Color
)

val LocalKynoxColors = compositionLocalOf {
    KynoxColorRoles(
        surfaceSunken = KynoxSurfaceSunkenLight,
        surfaceRaised = KynoxSurfaceLight,
        outlineStrong = KynoxOutlineStrongLight,
        onSurfaceFaint = KynoxOnSurfaceFaintLight,
        accentContainer = KynoxAccentContainerLight,
        seriesSecondary = KynoxSeriesGpuLight,
        grid = KynoxOutlineLight,
        navBar = KynoxNavBarDark
    )
}

val MaterialTheme.kynoxColors: KynoxColorRoles
    @Composable get() = LocalKynoxColors.current

/** Single source of the load / temperature bands so every screen colours a value the same way. */
object KynoxStatusBands {
    const val LOAD_WARN = 75f
    const val LOAD_CRIT = 90f
    const val TEMP_WARN = 40f
    const val TEMP_CRIT = 47f

    fun loadColor(percent: Float?, normal: Color): Color = when {
        percent == null -> normal
        percent >= LOAD_CRIT -> StatusDanger
        percent >= LOAD_WARN -> StatusWarning
        else -> normal
    }

    fun tempColor(celsius: Float?, normal: Color): Color = when {
        celsius == null -> normal
        celsius >= TEMP_CRIT -> StatusDanger
        celsius >= TEMP_WARN -> StatusWarning
        else -> normal
    }
}
