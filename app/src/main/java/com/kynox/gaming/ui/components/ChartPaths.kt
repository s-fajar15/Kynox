package com.kynox.gaming.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path

/**
 * Garis patah lurus lewat semua titik (3.0: tanpa kurva halus, supaya lonjakan data terlihat apa adanya).
 * Nama fungsi dipertahankan untuk pemakai lama.
 */
internal fun smoothLinePath(points: List<Offset>): Path {
    val path = Path()
    if (points.isEmpty()) return path
    path.moveTo(points[0].x, points[0].y)
    for (i in 1 until points.size) path.lineTo(points[i].x, points[i].y)
    return path
}
