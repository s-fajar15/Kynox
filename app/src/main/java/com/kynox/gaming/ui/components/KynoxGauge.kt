package com.kynox.gaming.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kynox.gaming.ui.theme.KynoxMotion
import com.kynox.gaming.ui.theme.kynoxColors
import com.kynox.gaming.ui.theme.rememberMotionScale

/**
 * Cincin busur 270 derajat polos (tanpa takik/tick-mark): trek surface2 + busur isi berujung bulat.
 * Nama dan signature dipertahankan untuk pemakai lama.
 */
@Composable
fun KynoxGauge(
    progress: Float,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 10.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    content: @Composable () -> Unit
) {
    val scale = rememberMotionScale()
    val animated by animateFloatAsState(
        progress.coerceIn(0f, 1f),
        tween((KynoxMotion.SLOW * scale).toInt()),
        label = "gauge"
    )
    val track = MaterialTheme.kynoxColors.surfaceSunken
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val diameter = minOf(size.width, size.height) - stroke
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arc = Size(diameter, diameter)
            drawArc(track, 135f, 270f, false, topLeft, arc, style = Stroke(stroke, cap = StrokeCap.Round))
            if (animated > 0f) {
                drawArc(color, 135f, 270f * animated, false, topLeft, arc, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        content()
    }
}
