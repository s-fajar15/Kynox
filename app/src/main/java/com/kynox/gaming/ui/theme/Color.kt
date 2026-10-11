package com.kynox.gaming.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

// Kynox 4.0 "Material You yang rapi": hijau-teal tenang, permukaan tonal.
// Token mengikuti spesifikasi desain persis. Nama konstanta lama dipertahankan (alias) agar layar yang
// belum dialihkan ke sistem desain baru tetap tersambung dan terkompilasi.

// ---- Terang ----
val KynoxBackgroundLight = Color(0xFFF4F8F7)
val KynoxSurfaceLight = Color(0xFFE8F0EE)
val KynoxSurface2Light = Color(0xFFD9E5E2)
val KynoxOnSurfaceLight = Color(0xFF101C1B)
val KynoxOnSurfaceMutedLight = Color(0xFF4A5C59)
val KynoxPrimaryLight = Color(0xFF00696A)
val KynoxOnPrimaryLight = Color(0xFFFFFFFF)
val KynoxPrimaryContainerLight = Color(0xFFBDEDEB)
val KynoxOnPrimaryContainerLight = Color(0xFF00201F)
val KynoxGoodLight = Color(0xFF1F7A3E)
val KynoxWarningLight = Color(0xFF9A5B00)
val KynoxDangerLight = Color(0xFFB3261E)
/** Turunan (bukan token spesifikasi): garis tepi kontrol (>= 3:1) dan pemisah halus. */
val KynoxOutlineStrongLight = Color(0xFF6F7977)
val KynoxOutlineLight = Color(0xFFBFC9C7)

// ---- Gelap ----
val KynoxBackgroundDark = Color(0xFF0D1413)
val KynoxSurfaceDark = Color(0xFF162120)
val KynoxSurface2Dark = Color(0xFF223130)
val KynoxOnSurfaceDark = Color(0xFFE2ECEA)
val KynoxOnSurfaceMutedDark = Color(0xFF9DB0AD)
val KynoxPrimaryDark = Color(0xFF4FD8D2)
val KynoxOnPrimaryDark = Color(0xFF003736)
val KynoxPrimaryContainerDark = Color(0xFF00504F)
val KynoxOnPrimaryContainerDark = Color(0xFFBDEDEB)
val KynoxGoodDark = Color(0xFF6FD592)
val KynoxWarningDark = Color(0xFFF2B766)
val KynoxDangerDark = Color(0xFFFFB4AB)
/** Turunan (bukan token spesifikasi). */
val KynoxOutlineStrongDark = Color(0xFF899391)
val KynoxOutlineDark = Color(0xFF3F4947)

// ---- Alias nama lama (jangan dihapus; dipakai layar yang belum dialihkan) ----
val KynoxAccentLight = KynoxPrimaryLight
val KynoxAccentContainerLight = KynoxPrimaryContainerLight
val KynoxAccentDark = KynoxPrimaryDark
val KynoxAccentContainerDark = KynoxPrimaryContainerDark
val KynoxOnAccent = KynoxOnPrimaryDark
val KynoxSurfaceSunkenLight = KynoxSurface2Light
val KynoxSurfaceSunkenDark = KynoxSurface2Dark
val KynoxOnSurfaceFaintLight = KynoxOnSurfaceMutedLight
val KynoxOnSurfaceFaintDark = KynoxOnSurfaceMutedDark
val KynoxSurfaceRaisedDark = KynoxSurfaceDark
val KynoxAccentVioletDark = KynoxOnSurfaceMutedDark
val KynoxNavBarDark = KynoxSurfaceDark
/** Seri kedua grafik (mis. GPU): nada teks sekunder, dibedakan dari seri utama berwarna aksen. */
val KynoxSeriesGpuLight = KynoxOnSurfaceMutedLight
val KynoxSeriesGpuDark = KynoxOnSurfaceMutedDark

/**
 * Tema yang sedang aktif. Diisi oleh [KynoxTheme]. Disimpan sebagai state agar komposabel yang hanya
 * membaca StatusGood/Warning/Danger ikut terkomposisi ulang saat tema berganti.
 */
object KynoxPalette {
    var dark: Boolean by mutableStateOf(true)
}

/**
 * Warna status mengikuti tema (token terang/gelap berbeda, keduanya lolos kontras AA terhadap permukaannya).
 * Berupa properti ber-getter agar semua pemakai lama (StatusGood dst.) otomatis benar tanpa diubah satu per satu.
 */
val StatusGood: Color get() = if (KynoxPalette.dark) KynoxGoodDark else KynoxGoodLight
val StatusWarning: Color get() = if (KynoxPalette.dark) KynoxWarningDark else KynoxWarningLight
val StatusDanger: Color get() = if (KynoxPalette.dark) KynoxDangerDark else KynoxDangerLight
val StatusInfo: Color get() = if (KynoxPalette.dark) KynoxPrimaryDark else KynoxPrimaryLight
val StatusDisabled: Color get() = if (KynoxPalette.dark) KynoxOnSurfaceMutedDark else KynoxOnSurfaceMutedLight
