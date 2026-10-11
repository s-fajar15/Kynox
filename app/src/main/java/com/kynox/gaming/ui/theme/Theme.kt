package com.kynox.gaming.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = KynoxPrimaryLight,
    onPrimary = KynoxOnPrimaryLight,
    primaryContainer = KynoxPrimaryContainerLight,
    onPrimaryContainer = KynoxOnPrimaryContainerLight,
    inversePrimary = KynoxPrimaryDark,
    secondary = KynoxPrimaryLight,
    onSecondary = KynoxOnPrimaryLight,
    secondaryContainer = KynoxPrimaryContainerLight,
    onSecondaryContainer = KynoxOnPrimaryContainerLight,
    tertiary = KynoxPrimaryLight,
    onTertiary = KynoxOnPrimaryLight,
    tertiaryContainer = KynoxPrimaryContainerLight,
    onTertiaryContainer = KynoxOnPrimaryContainerLight,
    background = KynoxBackgroundLight,
    onBackground = KynoxOnSurfaceLight,
    surface = KynoxSurfaceLight,
    onSurface = KynoxOnSurfaceLight,
    surfaceVariant = KynoxSurface2Light,
    onSurfaceVariant = KynoxOnSurfaceMutedLight,
    surfaceTint = Color.Transparent,
    inverseSurface = KynoxOnSurfaceLight,
    inverseOnSurface = KynoxBackgroundLight,
    error = KynoxDangerLight,
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    outline = KynoxOutlineStrongLight,
    outlineVariant = KynoxOutlineLight,
    scrim = Color.Black,
    surfaceBright = KynoxBackgroundLight,
    surfaceDim = KynoxSurface2Light,
    surfaceContainerLowest = KynoxBackgroundLight,
    surfaceContainerLow = KynoxSurfaceLight,
    surfaceContainer = KynoxSurfaceLight,
    surfaceContainerHigh = KynoxSurface2Light,
    surfaceContainerHighest = KynoxSurface2Light
)

private val DarkColors = darkColorScheme(
    primary = KynoxPrimaryDark,
    onPrimary = KynoxOnPrimaryDark,
    primaryContainer = KynoxPrimaryContainerDark,
    onPrimaryContainer = KynoxOnPrimaryContainerDark,
    inversePrimary = KynoxPrimaryLight,
    secondary = KynoxPrimaryDark,
    onSecondary = KynoxOnPrimaryDark,
    secondaryContainer = KynoxPrimaryContainerDark,
    onSecondaryContainer = KynoxOnPrimaryContainerDark,
    tertiary = KynoxPrimaryDark,
    onTertiary = KynoxOnPrimaryDark,
    tertiaryContainer = KynoxPrimaryContainerDark,
    onTertiaryContainer = KynoxOnPrimaryContainerDark,
    background = KynoxBackgroundDark,
    onBackground = KynoxOnSurfaceDark,
    surface = KynoxSurfaceDark,
    onSurface = KynoxOnSurfaceDark,
    surfaceVariant = KynoxSurface2Dark,
    onSurfaceVariant = KynoxOnSurfaceMutedDark,
    surfaceTint = Color.Transparent,
    inverseSurface = KynoxOnSurfaceDark,
    inverseOnSurface = KynoxBackgroundDark,
    error = KynoxDangerDark,
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = KynoxOutlineStrongDark,
    outlineVariant = KynoxOutlineDark,
    scrim = Color.Black,
    surfaceBright = KynoxSurface2Dark,
    surfaceDim = KynoxBackgroundDark,
    surfaceContainerLowest = KynoxBackgroundDark,
    surfaceContainerLow = KynoxSurfaceDark,
    surfaceContainer = KynoxSurfaceDark,
    surfaceContainerHigh = KynoxSurface2Dark,
    surfaceContainerHighest = KynoxSurface2Dark
)

private val LightExtendedColors = KynoxColorRoles(
    surfaceSunken = KynoxSurface2Light,
    surfaceRaised = KynoxSurfaceLight,
    outlineStrong = KynoxOutlineStrongLight,
    onSurfaceFaint = KynoxOnSurfaceMutedLight,
    accentContainer = KynoxPrimaryContainerLight,
    seriesSecondary = KynoxSeriesGpuLight,
    grid = KynoxOutlineLight,
    navBar = KynoxSurfaceLight,
    good = KynoxGoodLight,
    warning = KynoxWarningLight,
    danger = KynoxDangerLight
)

private val DarkExtendedColors = KynoxColorRoles(
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

@Composable
fun KynoxTheme(useDarkTheme: Boolean = true, content: @Composable () -> Unit) {
    // Memberi tahu StatusGood/Warning/Danger (properti ber-getter) tema mana yang aktif.
    if (KynoxPalette.dark != useDarkTheme) KynoxPalette.dark = useDarkTheme
    val colors = if (useDarkTheme) DarkColors else LightColors
    val extended = if (useDarkTheme) DarkExtendedColors else LightExtendedColors
    val fonts = rememberKynoxFonts()
    val typography = remember(fonts) { kynoxTypography(fonts) }
    CompositionLocalProvider(LocalKynoxColors provides extended) {
        MaterialTheme(
            colorScheme = colors,
            typography = typography,
            shapes = KynoxShapes.material,
            content = content
        )
    }
}
