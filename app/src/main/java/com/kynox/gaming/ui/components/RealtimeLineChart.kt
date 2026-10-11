package com.kynox.gaming.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Grafik garis tanpa dependensi: garis sederhana + area tipis di bawahnya. Tanpa grid, tanpa tick,
 * tanpa penanda ujung, tanpa glow.
 *
 * [maxValue] bila diisi (mis. 100f untuk persen) dipakai sebagai batas atas tetap; bila null skala mengikuti
 * nilai tertinggi yang ada. [minValue] bila diisi menjadi batas bawah (untuk suhu: skala tidak mulai dari nol,
 * sehingga naik-turunnya terlihat).
 */
@Composable
fun RealtimeLineChart(
    values: List<Float>,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    height: Dp = 64.dp,
    maxValue: Float? = null,
    minValue: Float? = null
) {
    Canvas(modifier = modifier.fillMaxWidth().height(height)) {
        val w = size.width
        val h = size.height
        if (values.size < 2) {
            drawLine(lineColor.copy(alpha = 0.4f), Offset(0f, h - 1.dp.toPx()), Offset(w, h - 1.dp.toPx()), 2.dp.toPx(), cap = StrokeCap.Round)
            return@Canvas
        }
        val lo = minValue ?: 0f
        val range = ((maxValue ?: values.max()) - lo).let { if (it <= 0f) 1f else it }
        val stepX = w / (values.size - 1).toFloat()
        val pad = 3.dp.toPx()
        val curve = values.mapIndexed { index, value ->
            val normalized = ((value - lo) / range).coerceIn(0f, 1f)
            Offset(index * stepX, pad + (h - 2 * pad) * (1f - normalized))
        }
        val line = smoothLinePath(curve)
        val area = smoothLinePath(curve)
        area.lineTo(w, h)
        area.lineTo(0f, h)
        area.close()
        drawPath(area, color = lineColor.copy(alpha = 0.16f), style = Fill)
        drawPath(
            line,
            color = lineColor,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}
