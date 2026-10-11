package com.kynox.gaming.service

import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import com.kynox.gaming.data.settings.SettingsRepository
import kotlinx.coroutines.flow.first

/**
 * Gaya overlay Kynox 4.0: panel tonal gelap berujung membulat, tanpa sudut potong, tanpa siku aksen dan tanpa bingkai.
 * Warna mengikuti token mode gelap aplikasi (permukaan #162120, teks #E2ECEA, aksen #4FD8D2).
 */
object OverlayStyle {
    private const val PANEL_RGB = 0x162120
    const val DEFAULT_OPACITY_PERCENT = 94
    const val ACCENT = 0xFF4FD8D2.toInt()
    const val TEXT = 0xFFE2ECEA.toInt()
    const val MUTED = 0xFF9DB0AD.toInt()
    const val DIVIDER = 0x339DB0AD
    const val GOOD = 0xFF6FD592.toInt()
    const val WARNING = 0xFFF2B766.toInt()
    const val DANGER = 0xFFFFB4AB.toInt()

    private fun withOpacity(rgb: Int, percent: Int): Int = ((percent.coerceIn(0, 100) * 255 / 100) shl 24) or rgb

    /**
     * Panel utama. [cutPx] kini menentukan radius sudut (dua kali nilainya); [strokeWidthPx] tidak dipakai lagi
     * (dipertahankan agar pemanggil lama tetap kompatibel). [opacityPercent] memengaruhi isian panel.
     */
    @Suppress("UNUSED_PARAMETER")
    fun hudBackground(cutPx: Float, strokeWidthPx: Int, opacityPercent: Int = DEFAULT_OPACITY_PERCENT): Drawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = (cutPx * 2f).coerceAtLeast(8f)
            setColor(withOpacity(PANEL_RGB, opacityPercent))
        }

    /** Tombol kecil netral (perkecil/perbesar): isian tipis berujung bulat, tanpa garis tepi. */
    fun buttonBackground(cornerRadiusPx: Float): GradientDrawable = GradientDrawable().apply {
        cornerRadius = cornerRadiusPx
        setColor(0x229DB0AD)
    }

    /** Tombol berhenti: isian tipis berwarna bahaya. */
    fun stopBackground(cornerRadiusPx: Float): GradientDrawable = GradientDrawable().apply {
        cornerRadius = cornerRadiusPx
        setColor(0x40FFB4AB)
    }
}

/** Langkah perubahan ukuran overlay lewat tombol - dan +, dalam persen. */
const val OVERLAY_RESIZE_STEP_PERCENT = 10

/** Mengubah ukuran overlay sebesar [deltaPercent]; hasilnya dijepit ke batas pengaturan dan memicu overlay dibangun ulang. */
suspend fun SettingsRepository.resizeOverlay(deltaPercent: Int) {
    val current = settingsFlow.first()
    setOverlayStyle(current.overlayScalePercent + deltaPercent, current.overlayOpacityPercent)
}

/** Menghapus posisi tersimpan kedua overlay. Dipakai tombol "Reset posisi overlay" di Pengaturan. */
fun resetOverlayPositions(context: android.content.Context) = OverlayPosition.reset(context)
