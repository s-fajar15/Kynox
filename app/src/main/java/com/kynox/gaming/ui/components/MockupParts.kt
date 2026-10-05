package com.kynox.gaming.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kynox.gaming.ui.theme.kynoxColors
import com.kynox.gaming.ui.theme.KynoxIcons
import com.kynox.gaming.ui.theme.KynoxShapes

/** Kotak ikon seperti di mockup: ikon aksen di atas wadah aksen yang lembut. */
@Composable
fun IconTile(icon: ImageVector, modifier: Modifier = Modifier, size: Dp = 40.dp, circle: Boolean = false) {
    Box(
        modifier
            .size(size)
            .clip(if (circle) CircleShape else RoundedCornerShape(size * 0.3f))
            .background(MaterialTheme.kynoxColors.accentContainer),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(size * 0.5f))
    }
}

/** Kartu daftar berisi baris-baris berpemisah (gaya Perangkat / Pengaturan / Tentang di mockup). */
@Composable
fun ListCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().kynoxCard(KynoxShapes.section)) { content() }
}

/** Garis pemisah di dasar sebuah baris. Baris terakhir menimpa border kartu sehingga tidak terlihat ganda. */
@Composable
fun Modifier.rowDivider(): Modifier {
    val color = MaterialTheme.colorScheme.outline
    return this.drawBehind {
        drawLine(color, Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), 1.dp.toPx())
    }
}

@Composable
fun KynoxListRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    subtitleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .rowDivider()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconTile(icon, size = 42.dp)
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotEmpty()) {
                Text(subtitle, style = MaterialTheme.typography.labelMedium, color = subtitleColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (trailing != null) trailing()
        else if (onClick != null) Icon(KynoxIcons.Chevron, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}

/** Garis gelombang halus tanpa sumbu/grid, seperti sparkline di mockup. */
@Composable
fun Sparkline(values: List<Float>, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round)
        if (values.size < 2) {
            drawLine(color.copy(alpha = 0.5f), Offset(0f, h / 2f), Offset(w, h / 2f), 2.dp.toPx(), StrokeCap.Round)
            return@Canvas
        }
        val lo = values.min()
        val hi = values.max()
        val range = (hi - lo).let { if (it < 1f) 1f else it }
        val pad = h * 0.12f
        val step = w / (values.size - 1)
        fun y(v: Float) = pad + (h - 2 * pad) * (1f - (v - lo) / range)
        val path = Path().apply {
            moveTo(0f, y(values[0]))
            for (i in 1 until values.size) {
                val x0 = (i - 1) * step
                val x1 = i * step
                val mid = (x0 + x1) / 2f
                cubicTo(mid, y(values[i - 1]), mid, y(values[i]), x1, y(values[i]))
            }
        }
        drawPath(path, color, style = stroke)
    }
}
