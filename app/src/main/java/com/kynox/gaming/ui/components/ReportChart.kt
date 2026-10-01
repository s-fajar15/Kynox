package com.kynox.gaming.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kynox.gaming.ui.theme.kynoxColors
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow

data class ChartPoint(val xMs: Long, val y: Float)

private val AxisLabelWidth = 42.dp
private val AxisLabelHeight = 14.dp
private const val GRID_LINES = 5

/**
 * Line chart with a labelled value axis and a time axis, used by the
 * session report. Optionally colours each segment by its own value (used to
 * show FPS drops). Dependency-free, like [RealtimeLineChart].
 *
 * Axis labels are ordinary Text composables laid out next to the Canvas
 * rather than drawn into it, so they follow the theme and font scaling.
 */
@Composable
fun ReportChart(
    points: List<ChartPoint>,
    totalMs: Long,
    yMin: Float,
    yMax: Float,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    segmentColor: ((Float) -> Color)? = null,
    fill: Boolean = segmentColor == null,
    yFormat: (Float) -> String = { formatAxisNumber(it) },
    height: Dp = 140.dp,
    xLabels: List<String>? = null
) {
    val gridColor = MaterialTheme.kynoxColors.grid
    val labelStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 14.sp)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val span = (yMax - yMin).let { if (it <= 0f) 1f else it }
    val duration = if (totalMs <= 0L) 1L else totalMs

    Column(modifier = modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().height(height)) {
            Column(
                modifier = Modifier.fillMaxHeight().width(AxisLabelWidth),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                for (i in 0 until GRID_LINES) {
                    val value = yMax - span * i / (GRID_LINES - 1)
                    Text(
                        text = yFormat(value),
                        style = labelStyle,
                        color = labelColor,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(AxisLabelWidth - 6.dp).height(AxisLabelHeight)
                    )
                }
            }
            Canvas(Modifier.weight(1f).fillMaxHeight()) {
                val inset = AxisLabelHeight.toPx() / 2f
                val top = inset
                val bottom = size.height - inset
                val chartHeight = bottom - top
                val width = size.width

                val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
                for (i in 0 until GRID_LINES) {
                    val y = top + chartHeight * i / (GRID_LINES - 1)
                    drawLine(
                        gridColor, Offset(0f, y), Offset(width, y),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = if (i == GRID_LINES - 1) null else dash
                    )
                }
                if (points.size < 2) return@Canvas

                fun xOf(point: ChartPoint): Float = (point.xMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f) * width
                fun yOf(point: ChartPoint): Float =
                    bottom - ((point.y - yMin) / span).coerceIn(0f, 1f) * chartHeight

                if (fill) {
                    val fillPath = Path()
                    fillPath.moveTo(xOf(points.first()), bottom)
                    points.forEach { fillPath.lineTo(xOf(it), yOf(it)) }
                    fillPath.lineTo(xOf(points.last()), bottom)
                    fillPath.close()
                    drawPath(fillPath, brush = SolidColor(lineColor.copy(alpha = 0.12f)), style = Fill)
                }

                val strokeWidth = 2.dp.toPx()
                if (segmentColor == null) {
                    val linePath = Path()
                    points.forEachIndexed { index, point ->
                        if (index == 0) linePath.moveTo(xOf(point), yOf(point)) else linePath.lineTo(xOf(point), yOf(point))
                    }
                    drawPath(linePath, color = lineColor, style = Stroke(width = strokeWidth))
                } else {
                    for (i in 1 until points.size) {
                        val from = points[i - 1]
                        val to = points[i]
                        drawLine(
                            color = segmentColor(to.y),
                            start = Offset(xOf(from), yOf(from)),
                            end = Offset(xOf(to), yOf(to)),
                            strokeWidth = strokeWidth,
                            cap = StrokeCap.Round
                        )
                    }
                }
                val tip = points.last()
                drawCircle(
                    color = if (segmentColor == null) lineColor else segmentColor(tip.y),
                    radius = 3.dp.toPx(),
                    center = Offset(xOf(tip), yOf(tip))
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = AxisLabelWidth, top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val labels = xLabels?.takeIf { it.size == 3 }
                ?: listOf(formatAxisTime(0L), formatAxisTime(duration / 2), formatAxisTime(duration))
            labels.forEach { Text(it, style = labelStyle, color = labelColor) }
        }
    }
}

/** Value range that starts at zero and ends on a round number, for charts like FPS. */
fun zeroBasedRange(values: List<Float>): Pair<Float, Float> {
    val peak = values.maxOrNull() ?: 1f
    if (peak <= 0f) return 0f to 1f
    val rawStep = peak / (GRID_LINES - 1)
    val magnitude = 10.0.pow(floor(kotlin.math.log10(rawStep.toDouble()))).toFloat()
    val step = listOf(1f, 2f, 2.5f, 5f, 10f).map { it * magnitude }.first { it >= rawStep }
    return 0f to step * (GRID_LINES - 1)
}

/** Value range hugging the data with a little headroom, for charts like temperature and frequency. */
fun paddedRange(values: List<Float>, floorAtZero: Boolean = true): Pair<Float, Float> {
    val low = values.minOrNull() ?: 0f
    val high = values.maxOrNull() ?: 1f
    val span = max(high - low, max(1f, abs(high) * 0.02f))
    val pad = span * 0.15f
    val bottom = (low - pad).let { if (floorAtZero && it < 0f) 0f else it }
    return bottom to (high + pad)
}

fun formatAxisNumber(value: Float): String =
    if (abs(value) >= 100f || value == floor(value)) "%.0f".format(value) else "%.1f".format(value)

fun formatAxisTime(ms: Long): String {
    val totalSeconds = ceil(ms / 1000.0).toLong()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes == 0L) "${seconds}s" else "${minutes}m${seconds}s"
}
