package com.kynox.gaming.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import com.kynox.gaming.R
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kynox.gaming.ui.theme.KynoxMotion
import com.kynox.gaming.ui.theme.kynoxColors
import com.kynox.gaming.ui.theme.rememberMotionScale

/** Tanda "K" garis, dipakai splash/identitas. Header Beranda tidak lagi memakainya (cukup tulisan "Kynox"). */
fun DrawScope.drawKynoxMark(color: Color, box: Size, topLeft: Offset = Offset.Zero, progress: Float = 1f) {
    val stroke = box.width * 0.14f
    fun point(x: Float, y: Float) = Offset(topLeft.x + box.width * x, topLeft.y + box.height * y)
    val segments = listOf(
        point(0.30f, 0.14f) to point(0.30f, 0.86f),
        point(0.72f, 0.14f) to point(0.30f, 0.54f),
        point(0.44f, 0.42f) to point(0.74f, 0.86f)
    )
    segments.forEach { (start, end) ->
        val tip = Offset(start.x + (end.x - start.x) * progress, start.y + (end.y - start.y) * progress)
        drawLine(color, start, tip, strokeWidth = stroke, cap = StrokeCap.Round)
    }
}

/** Logo Kynox (K dan X abstrak), siluet satu warna yang diwarnai mengikuti tema. */
@Composable
fun KMark(
    modifier: Modifier = Modifier,
    markSize: Dp = 24.dp,
    color: Color = MaterialTheme.colorScheme.primary
) {
    Image(
        painter = painterResource(R.drawable.ic_stat_kynox),
        contentDescription = null,
        colorFilter = ColorFilter.tint(color),
        modifier = modifier.size(markSize)
    )
}

@Composable
fun KLoadingIndicator(
    modifier: Modifier = Modifier,
    markSize: Dp = 32.dp,
    color: Color = MaterialTheme.colorScheme.primary
) {
    CircularProgressIndicator(
        modifier = modifier.size(markSize),
        color = color,
        strokeWidth = 3.dp,
        trackColor = MaterialTheme.kynoxColors.surfaceSunken
    )
}

/**
 * Batang progres: tinggi 6 dp, ujung bulat (radius 3). Perubahan nilai dianimasikan singkat.
 * Di kartu surface, trek bawaan = surface2; di kartu primaryContainer berikan [trackColor] sendiri.
 */
@Composable
fun KLinearProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    trackColor: Color = MaterialTheme.kynoxColors.surfaceSunken,
    fillColor: Color = MaterialTheme.colorScheme.primary
) {
    val scale = rememberMotionScale()
    val clamped by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween((KynoxMotion.NORMAL * scale).toInt()),
        label = "progress"
    )
    Canvas(modifier.fillMaxWidth().height(6.dp)) {
        val radius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
        drawRoundRect(trackColor, size = size, cornerRadius = radius)
        if (clamped > 0f) {
            // Isi minimal selebar tingginya agar ujung bulatnya tetap terlihat pada nilai kecil.
            val w = (size.width * clamped).coerceAtLeast(size.height)
            drawRoundRect(fillColor, size = Size(w, size.height), cornerRadius = radius)
        }
    }
}

@Composable
fun KDivider(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.outlineVariant
) {
    HorizontalDivider(modifier = modifier, thickness = 1.dp, color = color)
}
