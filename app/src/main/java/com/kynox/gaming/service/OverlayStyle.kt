package com.kynox.gaming.service

import android.graphics.drawable.GradientDrawable

object OverlayStyle {
    private const val BACKGROUND_RGB = 0x0B1318
    private const val BORDER_RGB = 0x2BE0C7
    private const val BORDER_ALPHA = 0x42
    const val DEFAULT_OPACITY_PERCENT = 94
    const val ACCENT = 0xFF2BE0C7.toInt()
    const val TEXT = 0xFFEAF7F5.toInt()
    const val MUTED = 0xFF8EA6AA.toInt()
    const val GOOD = 0xFF2BD58F.toInt()
    const val WARNING = 0xFFF2B84B.toInt()
    const val DANGER = 0xFFFF6471.toInt()
    const val CORNER_RADIUS_DP = 18

    fun panelBackground(cornerRadiusPx: Float, strokeWidthPx: Int, opacityPercent: Int = DEFAULT_OPACITY_PERCENT): GradientDrawable = GradientDrawable().apply {
        val opacity = opacityPercent.coerceIn(0, 100)
        cornerRadius = cornerRadiusPx
        setColor(((opacity * 255 / 100) shl 24) or BACKGROUND_RGB)
        setStroke(strokeWidthPx, ((BORDER_ALPHA * opacity / 100) shl 24) or BORDER_RGB)
    }

    fun stopBackground(cornerRadiusPx: Float): GradientDrawable = GradientDrawable().apply {
        cornerRadius = cornerRadiusPx
        setColor(0x33FF6471)
        setStroke(1, DANGER)
    }
}
