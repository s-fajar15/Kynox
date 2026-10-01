package com.kynox.gaming.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.unit.dp
import com.kynox.gaming.ui.theme.kynoxColors

/**
 * Minimal dependency-free line chart used for the dashboard's realtime
 * graphs (PRD section 4). No external charting library is pulled in.
 */
@Composable
fun RealtimeLineChart(
    values: List<Float>,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    height: androidx.compose.ui.unit.Dp = 64.dp
) {
    val gridColor = MaterialTheme.kynoxColors.grid
    Canvas(modifier = modifier.fillMaxWidth().height(height)) {
        val w = size.width
        val h = size.height

        val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
        drawLine(gridColor, Offset(0f, h), Offset(w, h), strokeWidth = 1.dp.toPx())
        drawLine(gridColor, Offset(0f, h / 2f), Offset(w, h / 2f), strokeWidth = 1.dp.toPx(), pathEffect = dash)

        if (values.size < 2) return@Canvas

        val maxV = values.max().let { if (it <= 0f) 1f else it }
        val minV = 0f
        val range = (maxV - minV).let { if (it == 0f) 1f else it }
        val stepX = w / (values.size - 1).toFloat()

        val linePath = Path()
        val fillPath = Path()
        values.forEachIndexed { index, value ->
            val x = index * stepX
            val normalized = ((value - minV) / range).coerceIn(0f, 1f)
            val y = h - (normalized * h)
            if (index == 0) {
                linePath.moveTo(x, y)
                fillPath.moveTo(x, h)
                fillPath.lineTo(x, y)
            } else {
                linePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }
        fillPath.lineTo(w, h)
        fillPath.close()

        drawPath(fillPath, brush = androidx.compose.ui.graphics.SolidColor(lineColor.copy(alpha = 0.12f)), style = Fill)
        drawPath(linePath, color = lineColor, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
        val lastValue = ((values.last() - minV) / range).coerceIn(0f, 1f)
        drawCircle(lineColor, radius = 3.dp.toPx(), center = Offset(w, h - lastValue * h))
    }
}
