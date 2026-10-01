package com.kynox.gaming.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = KynoxAccentLight,
    onPrimary = Color.White,
    primaryContainer = KynoxAccentContainerLight,
    onPrimaryContainer = KynoxAccentLight,
    secondary = KynoxAccentLight,
    background = KynoxBackgroundLight,
    onBackground = KynoxOnSurfaceLight,
    surface = KynoxSurfaceLight,
    onSurface = KynoxOnSurfaceLight,
    surfaceVariant = KynoxSurfaceSunkenLight,
    onSurfaceVariant = KynoxOnSurfaceMutedLight,
    surfaceTint = Color.Transparent,
    outline = KynoxOutlineLight,
    outlineVariant = KynoxOutlineLight,
    error = StatusDanger,
    onError = Color.White
)

private val DarkColors = darkColorScheme(
    primary = KynoxAccentDark,
    onPrimary = Color(0xFF04211D),
    primaryContainer = KynoxAccentContainerDark,
    onPrimaryContainer = KynoxAccentDark,
    secondary = KynoxAccentDark,
    background = KynoxBackgroundDark,
    onBackground = KynoxOnSurfaceDark,
    surface = KynoxSurfaceDark,
    onSurface = KynoxOnSurfaceDark,
    surfaceVariant = KynoxSurfaceSunkenDark,
    onSurfaceVariant = KynoxOnSurfaceMutedDark,
    surfaceTint = Color.Transparent,
    outline = KynoxOutlineDark,
    outlineVariant = KynoxOutlineDark,
    error = StatusDanger,
    onError = Color.White
)

private val LightExtendedColors = KynoxColorRoles(
    surfaceSunken = KynoxSurfaceSunkenLight,
    surfaceRaised = KynoxSurfaceLight,
    outlineStrong = KynoxOutlineStrongLight,
    onSurfaceFaint = KynoxOnSurfaceFaintLight,
    accentContainer = KynoxAccentContainerLight,
    seriesSecondary = KynoxSeriesGpuLight,
    grid = KynoxOutlineLight,
    navBar = KynoxNavBarDark
)

private val DarkExtendedColors = KynoxColorRoles(
    surfaceSunken = KynoxSurfaceSunkenDark,
    surfaceRaised = KynoxSurfaceDark,
    outlineStrong = KynoxOutlineStrongDark,
    onSurfaceFaint = KynoxOnSurfaceFaintDark,
    accentContainer = KynoxAccentContainerDark,
    seriesSecondary = KynoxSeriesGpuDark,
    grid = KynoxOutlineDark,
    navBar = KynoxNavBarDark
)

@Composable
fun KynoxTheme(useDarkTheme: Boolean = true, content: @Composable () -> Unit) {
    val colors = if (useDarkTheme) DarkColors else LightColors
    val extended = if (useDarkTheme) DarkExtendedColors else LightExtendedColors
    CompositionLocalProvider(LocalKynoxColors provides extended) {
        MaterialTheme(
            colorScheme = colors,
            typography = KynoxTypography,
            shapes = KynoxShapes.material,
            content = content
        )
    }
}
