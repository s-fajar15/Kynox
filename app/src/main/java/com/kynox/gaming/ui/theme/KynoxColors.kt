package com.kynox.gaming.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/** Peran warna yang tidak punya slot di ColorScheme Material. Dibaca lewat [kynoxColors]. */
data class KynoxColorRoles(
    /** Permukaan kedua (surface2): trek progres, kotak ikon, chip. */
    val surfaceSunken: Color,
    val surfaceRaised: Color,
    val outlineStrong: Color,
    /** Teks sekunder (teks2). */
    val onSurfaceFaint: Color,
    val accentContainer: Color,
    /** Seri kedua grafik (GPU), dibedakan dari aksen (CPU). */
    val seriesSecondary: Color,
    /** Garis pemisah halus dan grid. */
    val grid: Color,
    /** Latar bottom bar (= surface). */
    val navBar: Color,
    /** Warna status, terpisah dari aksen dan lolos kontras AA terhadap permukaannya. */
    val good: Color,
    val warning: Color,
    val danger: Color
)

val LocalKynoxColors = compositionLocalOf {
    KynoxColorRoles(
        surfaceSunken = KynoxSurface2Dark,
        surfaceRaised = KynoxSurfaceDark,
        outlineStrong = KynoxOutlineStrongDark,
        onSurfaceFaint = KynoxOnSurfaceMutedDark,
        accentContainer = KynoxPrimaryContainerDark,
        seriesSecondary = KynoxSeriesGpuDark,
        grid = KynoxOutlineDark,
        navBar = KynoxSurfaceDark,
        good = KynoxGoodDark,
        warning = KynoxWarningDark,
        danger = KynoxDangerDark
    )
}

val MaterialTheme.kynoxColors: KynoxColorRoles
    @Composable get() = LocalKynoxColors.current

/** Satu sumber ambang beban / suhu supaya semua layar mewarnai nilai dengan cara yang sama. */
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
