package com.kynox.gaming.service

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import com.kynox.gaming.data.settings.SettingsRepository
import kotlinx.coroutines.flow.first

/**
 * Gaya overlay Kynox 2.3: panel hitam pekat bersudut potong (kiri-atas dan
 * kanan-bawah) dengan siku aksen merah, angka kondensed tebal dan label
 * huruf kapital. Terinspirasi tampilan penghitung FPS di ponsel gaming,
 * tanpa logo atau nama merek pihak lain.
 */
object OverlayStyle {
    private const val PANEL_RGB = 0x0A0A0E
    private const val EDGE_RGB = 0x3A3A44
    const val DEFAULT_OPACITY_PERCENT = 94
    const val ACCENT = 0xFFFF2A44.toInt()
    const val TEXT = 0xFFFFFFFF.toInt()
    const val MUTED = 0xFF9A9EA9.toInt()
    const val DIVIDER = 0x99FF2A44.toInt()
    const val GOOD = 0xFF2BE38B.toInt()
    const val WARNING = 0xFFFFB020.toInt()
    const val DANGER = 0xFFFF5A5F.toInt()

    private fun withOpacity(rgb: Int, percent: Int): Int = ((percent.coerceIn(0, 100) * 255 / 100) shl 24) or rgb

    /**
     * Panel utama. [cutPx] ukuran sudut yang dipotong; [opacityPercent] hanya
     * memengaruhi isian panel, siku merah tetap terlihat jelas.
     */
    fun hudBackground(cutPx: Float, strokeWidthPx: Int, opacityPercent: Int = DEFAULT_OPACITY_PERCENT): Drawable =
        AngularPanelDrawable(
            cutPx = cutPx,
            strokePx = strokeWidthPx.toFloat().coerceAtLeast(1f),
            accentPx = (strokeWidthPx * 2).toFloat().coerceAtLeast(2f),
            accentLenPx = cutPx * 2.2f,
            fillColor = withOpacity(PANEL_RGB, opacityPercent),
            edgeColor = withOpacity(EDGE_RGB, opacityPercent),
            accentColor = ACCENT
        )

    /** Tombol kecil netral (perkecil/perbesar): kotak bertepi abu. */
    fun buttonBackground(cornerRadiusPx: Float): GradientDrawable = GradientDrawable().apply {
        cornerRadius = cornerRadiusPx
        setColor(0x22FFFFFF)
        setStroke(1, withOpacity(EDGE_RGB, 100))
    }

    /** Tombol berhenti: kotak kecil bertepi merah. */
    fun stopBackground(cornerRadiusPx: Float): GradientDrawable = GradientDrawable().apply {
        cornerRadius = cornerRadiusPx
        setColor(0x33FF2A44)
        setStroke(1, ACCENT)
    }
}

/** Persegi panjang dengan sudut kiri-atas dan kanan-bawah dipotong, plus siku aksen mengikuti potongan itu. */
private class AngularPanelDrawable(
    private val cutPx: Float,
    private val strokePx: Float,
    private val accentPx: Float,
    private val accentLenPx: Float,
    fillColor: Int,
    edgeColor: Int,
    accentColor: Int
) : Drawable() {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = fillColor
    }
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = strokePx
        color = edgeColor
    }
    private val accent = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = accentPx
        color = accentColor
        strokeJoin = Paint.Join.MITER
        strokeCap = Paint.Cap.BUTT
    }
    private val body = Path()
    private val topLeft = Path()
    private val bottomRight = Path()

    override fun onBoundsChange(bounds: Rect) {
        val inset = accentPx / 2f
        val l = bounds.left + inset
        val t = bounds.top + inset
        val r = bounds.right - inset
        val b = bounds.bottom - inset
        val cut = cutPx.coerceAtMost(minOf(r - l, b - t) / 2f)
        val len = accentLenPx

        body.reset()
        body.moveTo(l + cut, t)
        body.lineTo(r, t)
        body.lineTo(r, b - cut)
        body.lineTo(r - cut, b)
        body.lineTo(l, b)
        body.lineTo(l, t + cut)
        body.close()

        topLeft.reset()
        topLeft.moveTo(l, t + cut + len)
        topLeft.lineTo(l, t + cut)
        topLeft.lineTo(l + cut, t)
        topLeft.lineTo(l + cut + len, t)

        bottomRight.reset()
        bottomRight.moveTo(r, b - cut - len)
        bottomRight.lineTo(r, b - cut)
        bottomRight.lineTo(r - cut, b)
        bottomRight.lineTo(r - cut - len, b)
    }

    override fun draw(canvas: Canvas) {
        canvas.drawPath(body, fill)
        canvas.drawPath(body, edge)
        canvas.drawPath(topLeft, accent)
        canvas.drawPath(bottomRight, accent)
    }

    // Opasitas diatur lewat warna isian saat dibuat, bukan lewat alpha Drawable.
    override fun setAlpha(alpha: Int) = Unit

    override fun setColorFilter(colorFilter: ColorFilter?) = Unit

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
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
