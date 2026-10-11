package com.kynox.gaming.ui.theme

import android.content.Context
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Tipografi 4.0: Plus Jakarta Sans bila berkasnya ada di res/font, jika tidak FontFamily.SansSerif.
 * Saat ini hanya plus_jakarta_sans_bold.ttf yang terpasang (dipakai untuk judul, label, dan angka).
 *
 * Berkas yang dicari (huruf kecil, garis bawah; format .ttf atau .otf), tanpa perlu mengubah kode:
 *   res/font/plus_jakarta_sans_regular   (400)
 *   res/font/plus_jakarta_sans_medium    (500)
 *   res/font/plus_jakarta_sans_semibold  (600)
 *   res/font/plus_jakarta_sans_bold      (700)
 *   res/font/plus_jakarta_sans_extrabold (800)
 * Pencarian dilakukan saat aplikasi berjalan (getIdentifier), sehingga build tidak gagal bila berkas belum ada.
 */
val KynoxFont: FontFamily = FontFamily.SansSerif
val KynoxHeading: FontFamily = FontFamily.SansSerif

/** Monospace HANYA untuk path, perintah, dan log. Bukan gaya utama. */
val KynoxData: FontFamily = FontFamily.Monospace

private const val TABULAR = "tnum"

/** Pasangan keluarga font: [heading] untuk judul, label, dan angka (bobot >= 600), [body] untuk teks isi. */
class KynoxFonts(val heading: FontFamily, val body: FontFamily)

/**
 * Mencari berkas font di res/font lewat nama. Bila hanya bobot tebal yang ada (mis. cuma Bold), keluarga itu
 * dipakai khusus untuk judul/label/angka dan teks isi tetap memakai font sans sistem, supaya teks biasa tidak
 * ikut menjadi tebal. Bila Regular atau Medium tersedia, semua gaya memakai Plus Jakarta Sans.
 */
fun buildKynoxFonts(context: Context): KynoxFonts {
    val res = context.resources
    val pkg = context.packageName
    fun font(name: String, weight: FontWeight): Font? {
        val id = res.getIdentifier(name, "font", pkg)
        return if (id != 0) Font(id, weight) else null
    }
    val regular = font("plus_jakarta_sans_regular", FontWeight.Normal)
    val medium = font("plus_jakarta_sans_medium", FontWeight.Medium)
    val all = listOfNotNull(
        regular,
        medium,
        font("plus_jakarta_sans_semibold", FontWeight.SemiBold),
        font("plus_jakarta_sans_bold", FontWeight.Bold),
        font("plus_jakarta_sans_extrabold", FontWeight.ExtraBold)
    )
    if (all.isEmpty()) return KynoxFonts(FontFamily.SansSerif, FontFamily.SansSerif)
    val family = FontFamily(all)
    return if (regular != null || medium != null) KynoxFonts(family, family) else KynoxFonts(family, FontFamily.SansSerif)
}

/** Nama lama: keluarga untuk judul. */
fun buildKynoxFontFamily(context: Context): FontFamily = buildKynoxFonts(context).heading

@Composable
fun rememberKynoxFonts(): KynoxFonts {
    val context = LocalContext.current
    return remember(context) { buildKynoxFonts(context) }
}

@Composable
fun rememberKynoxFontFamily(): FontFamily = rememberKynoxFonts().heading

private fun style(
    fonts: KynoxFonts,
    weight: FontWeight,
    size: Int,
    line: Int,
    tracking: Double = 0.0,
    tabular: Boolean = false
) = TextStyle(
    fontFamily = if (weight.weight >= FontWeight.SemiBold.weight) fonts.heading else fonts.body,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.sp,
    fontFeatureSettings = if (tabular) TABULAR else null
)

fun kynoxTypography(fonts: KynoxFonts): Typography = Typography(
    // Angka metrik besar bergaya editorial: hero 76/700 (-3), kotak bento 40/700. Angka tabular.
    displayLarge = style(fonts, FontWeight.Bold, 76, 78, -3.0, tabular = true),
    displayMedium = style(fonts, FontWeight.Bold, 40, 44, -1.0, tabular = true),
    displaySmall = style(fonts, FontWeight.Bold, 28, 34, -0.5, tabular = true),
    // Judul layar 26/700.
    headlineLarge = style(fonts, FontWeight.Bold, 26, 32, -0.3),
    headlineMedium = style(fonts, FontWeight.Bold, 26, 32, -0.3),
    headlineSmall = style(fonts, FontWeight.SemiBold, 20, 26),
    titleLarge = style(fonts, FontWeight.Bold, 22, 28, -0.2),
    titleMedium = style(fonts, FontWeight.SemiBold, 16, 22),
    titleSmall = style(fonts, FontWeight.SemiBold, 14, 20),
    // Baris daftar 14-15.
    bodyLarge = style(fonts, FontWeight.Normal, 15, 22),
    bodyMedium = style(fonts, FontWeight.Normal, 14, 20),
    bodySmall = style(fonts, FontWeight.Normal, 12, 17),
    // Label kartu 13/600 (warna teks sekunder diberikan pemakai).
    labelLarge = style(fonts, FontWeight.SemiBold, 14, 20),
    labelMedium = style(fonts, FontWeight.SemiBold, 13, 18),
    labelSmall = style(fonts, FontWeight.Medium, 12, 16)
)

/** Dipertahankan untuk pemakai lama; Theme memakai [kynoxTypography] dengan font hasil pencarian. */
val KynoxTypography: Typography = kynoxTypography(KynoxFonts(FontFamily.SansSerif, FontFamily.SansSerif))
