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

        drawLine(gridColor.copy(alpha = 0.9f), Offset(0f, h), Offset(w, h), strokeWidth = 1.dp.toPx())

        if (values.size < 2) return@Canvas

        val maxV = values.max().let { if (it <= 0f) 1f else it }
        val range = maxV.let { if (it == 0f) 1f else it }
        val stepX = w / (values.size - 1).toFloat()
        val pad = 3.dp.toPx()
        val curve = values.mapIndexed { index, value ->
            val normalized = (value / range).coerceIn(0f, 1f)
            Offset(index * stepX, pad + (h - 2 * pad) * (1f - normalized))
        }

        val areaPath = smoothLinePath(curve)
        areaPath.lineTo(w, h)
        areaPath.lineTo(0f, h)
        areaPath.close()
        drawPath(areaPath, color = lineColor.copy(alpha = 0.14f), style = Fill)
        drawPath(
            smoothLinePath(curve),
            color = lineColor,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
        )
        drawCircle(lineColor, radius = 3.5.dp.toPx(), center = curve.last())
    }
}
