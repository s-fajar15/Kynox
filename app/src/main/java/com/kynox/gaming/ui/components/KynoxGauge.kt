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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kynox.gaming.ui.theme.KynoxAccentDark
import com.kynox.gaming.ui.theme.kynoxColors

/** Gauge busur terbuka di bawah (270 derajat) seperti mockup, tanpa glow. */
@Composable
fun KynoxGauge(
    progress: Float,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 12.dp,
    color: androidx.compose.ui.graphics.Color = KynoxAccentDark,
    content: @Composable () -> Unit
) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(450), label = "gauge")
    val track = MaterialTheme.kynoxColors.surfaceSunken
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)
            drawArc(track, 135f, 270f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            if (animated > 0f) {
                // Busur "komet": ekor transparan menuju warna penuh di ujung, jadi arah dan nilai terbaca sekilas.
                val tail = color.copy(alpha = 0.35f)
                val endStop = (0.75f * animated).coerceIn(0.01f, 0.75f)
                rotate(135f, pivot = center) {
                    drawArc(
                        brush = Brush.sweepGradient(colorStops = arrayOf(0f to tail, endStop to color, 1f to color), center = center),
                        startAngle = 0f,
                        sweepAngle = 270f * animated,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(stroke, cap = StrokeCap.Round)
                    )
                }
            }
        }
        content()
    }
}
