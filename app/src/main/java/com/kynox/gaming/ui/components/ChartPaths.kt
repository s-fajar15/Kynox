package com.kynox.gaming.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path

/**
 * Garis halus lewat semua titik. Titik kontrol memakai tangen horizontal di tiap titik,
 * jadi kurva tidak pernah melampaui nilai data (tidak ada overshoot) dan terlihat seperti di mockup.
 */
internal fun smoothLinePath(points: List<Offset>): Path {
    val path = Path()
    if (points.isEmpty()) return path
    path.moveTo(points[0].x, points[0].y)
    for (i in 1 until points.size) {
        val a = points[i - 1]
        val b = points[i]
        val midX = (a.x + b.x) / 2f
        path.cubicTo(midX, a.y, midX, b.y, b.x, b.y)
    }
    return path
}
