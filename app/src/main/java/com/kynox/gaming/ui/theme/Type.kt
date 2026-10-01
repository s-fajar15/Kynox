package com.kynox.gaming.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * The whole app's typeface, defined once. Currently the platform sans
 * (Roboto on Android). To switch to Inter or Geist, add the .ttf files to
 * res/font and replace this with
 * FontFamily(Font(R.font.inter_regular, FontWeight.Normal), ...).
 */
val KynoxFont: FontFamily = FontFamily.SansSerif

/** Tabular figures so live numbers do not jitter as digits change. */
private const val TABULAR = "tnum"

private fun style(weight: FontWeight, size: Int, line: Int, tracking: Double = 0.0, tabular: Boolean = false) = TextStyle(
    fontFamily = KynoxFont,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.sp,
    fontFeatureSettings = if (tabular) TABULAR else null
)

val KynoxTypography = Typography(
    // Display: the one big value on a screen (temperature, battery, frequency, FPS).
    displayLarge = style(FontWeight.Medium, 56, 60, -1.5, tabular = true),
    displayMedium = style(FontWeight.Medium, 44, 48, -1.0, tabular = true),
    displaySmall = style(FontWeight.Medium, 32, 38, -0.5, tabular = true),
    // Heading: page, section, subsystem.
    headlineLarge = style(FontWeight.SemiBold, 28, 34, -0.3),
    headlineMedium = style(FontWeight.SemiBold, 24, 30, -0.2),
    headlineSmall = style(FontWeight.SemiBold, 20, 26, -0.1),
    titleLarge = style(FontWeight.SemiBold, 22, 28, -0.2),
    titleMedium = style(FontWeight.Medium, 16, 22),
    titleSmall = style(FontWeight.Medium, 14, 20, tabular = true),
    // Body: descriptions, explanations, status.
    bodyLarge = style(FontWeight.Normal, 15, 22),
    bodyMedium = style(FontWeight.Normal, 13, 19),
    bodySmall = style(FontWeight.Normal, 12, 17),
    // Caption / label: timestamps, sources, metadata. Sentence case, muted by the caller.
    labelLarge = style(FontWeight.Medium, 14, 20),
    labelMedium = style(FontWeight.Medium, 12, 16, 0.2, tabular = true),
    labelSmall = style(FontWeight.Medium, 11, 15, 0.2, tabular = true)
)
