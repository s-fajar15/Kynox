package com.kynox.gaming.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kynox.gaming.ui.theme.kynoxColors

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

@Composable
fun KMark(
    modifier: Modifier = Modifier,
    markSize: Dp = 24.dp,
    color: Color = MaterialTheme.colorScheme.primary
) {
    Canvas(modifier.size(markSize)) {
        drawKynoxMark(color, Size(size.width, size.height))
    }
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

@Composable
fun KLinearProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    trackColor: Color = MaterialTheme.kynoxColors.surfaceSunken,
    fillColor: Color = MaterialTheme.colorScheme.primary
) {
    val clamped = progress.coerceIn(0f, 1f)
    Canvas(modifier.fillMaxWidth().height(8.dp)) {
        val radius = CornerRadius(size.height / 2f)
        drawRoundRect(trackColor, cornerRadius = radius)
        if (clamped > 0f) {
            drawRoundRect(
                fillColor,
                size = Size(maxOf(size.height, size.width * clamped), size.height),
                cornerRadius = radius
            )
        }
    }
}

@Composable
fun KDivider(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.outline
) {
    HorizontalDivider(modifier = modifier, thickness = 1.dp, color = color)
}
