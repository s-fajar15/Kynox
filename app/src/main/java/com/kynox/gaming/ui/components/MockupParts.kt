package com.kynox.gaming.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kynox.gaming.ui.theme.KynoxIcons
import com.kynox.gaming.ui.theme.KynoxShapes

/** Kotak ikon: sudut 12 dp, latar primaryContainer, ikon onPrimaryContainer. */
@Composable
fun IconTile(icon: ImageVector, modifier: Modifier = Modifier, size: Dp = 40.dp, circle: Boolean = false) {
    val shape = if (circle) KynoxShapes.circle else KynoxShapes.iconTile
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(size * 0.55f))
    }
}

/** Kartu daftar tonal berisi baris-baris berpemisah (gaya Lainnya / Pengaturan / Tentang). */
@Composable
fun ListCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().kynoxCard(KynoxShapes.section).padding(vertical = 4.dp)) {
        Column(Modifier.fillMaxWidth().hideTrailingDivider()) { content() }
    }
}

/**
 * Garis pemisah di dasar sebuah baris. [startInset] menggeser awal garis (mis. sejajar dengan judul, melewati ikon).
 * Baris terakhir di dalam [SectionCard]/[ListCard] disembunyikan oleh [hideTrailingDivider].
 */
@Composable
fun Modifier.rowDivider(startInset: Dp = 0.dp): Modifier {
    val color = MaterialTheme.colorScheme.outlineVariant
    return this.drawBehind {
        val y = size.height - 0.5f
        drawLine(color, Offset(startInset.toPx(), y), Offset(size.width, y), 1.dp.toPx())
    }
}

/** Menyembunyikan 1 dp terbawah isi (garis pemisah baris terakhir) supaya tidak ada garis ganda di dasar kartu. */
fun Modifier.hideTrailingDivider(): Modifier = this
    .clipToBounds()
    .layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val trim = 1.dp.roundToPx()
        layout(placeable.width, (placeable.height - trim).coerceAtLeast(0)) { placeable.placeRelative(0, 0) }
    }

@Composable
private fun NavRowLayout(
    title: String,
    icon: ImageVector,
    modifier: Modifier,
    value: String?,
    valueColor: Color,
    info: String?,
    onClick: (() -> Unit)?,
    trailing: (@Composable () -> Unit)?
) {
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .rowDivider(startInset = 60.dp)
            .heightIn(min = 56.dp)
            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconTile(icon, size = 36.dp)
        Spacer(Modifier.width(12.dp))
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (!value.isNullOrEmpty()) {
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = if (valueColor == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp).widthIn(max = 160.dp)
            )
        }
        if (!info.isNullOrEmpty()) InfoButton(info, title = title)
        if (trailing != null) trailing()
        else if (onClick != null) {
            Icon(
                KynoxIcons.Chevron,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp).size(20.dp)
            )
        }
    }
}


/**
 * Baris navigasi layar Lainnya: ikon tile 36 dp + judul + nilai/status singkat (teks berwarna, tanpa pil) + chevron.
 * [value] kosong berarti tidak ada nilai; [valueColor] hanya diisi bila nilainya status (baik/waspada/bahaya).
 */
@Composable
fun KynoxNavRow(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    value: String? = null,
    valueColor: Color = Color.Unspecified,
    onClick: (() -> Unit)? = null
) {
    NavRowLayout(
        title = title, icon = icon, modifier = modifier, value = value, valueColor = valueColor,
        info = null, onClick = onClick, trailing = null
    )
}

/**
 * Baris daftar (signature lama dipertahankan). Subjudul berwarna biasa dianggap penjelasan dan dibuka lewat (i);
 * subjudul yang diberi warna khusus ([subtitleColor] bukan warna bawaan) dianggap NILAI dan tampil di kanan.
 */
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
    val isValue = subtitleColor != MaterialTheme.colorScheme.onSurfaceVariant
    NavRowLayout(
        title = title,
        icon = icon,
        modifier = modifier,
        value = if (isValue) subtitle else null,
        valueColor = subtitleColor,
        info = if (!isValue) subtitle.ifEmpty { null } else null,
        onClick = onClick,
        trailing = trailing
    )
}

/** Garis sederhana + area tipis di bawahnya. Tanpa sumbu, tanpa penanda, tanpa glow. */
@Composable
fun Sparkline(values: List<Float>, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        if (values.size < 2) {
            drawLine(color.copy(alpha = 0.5f), Offset(0f, h / 2f), Offset(w, h / 2f), 2.dp.toPx(), cap = StrokeCap.Round)
            return@Canvas
        }
        val lo = values.min()
        val hi = values.max()
        val range = (hi - lo).let { if (it < 1f) 1f else it }
        val pad = h * 0.12f
        val step = w / (values.size - 1)
        fun y(v: Float) = pad + (h - 2 * pad) * (1f - (v - lo) / range)
        val line = Path().apply {
            moveTo(0f, y(values[0]))
            for (i in 1 until values.size) lineTo(i * step, y(values[i]))
        }
        val area = Path().apply {
            addPath(line)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(area, color.copy(alpha = 0.14f), style = Fill)
        drawPath(line, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
