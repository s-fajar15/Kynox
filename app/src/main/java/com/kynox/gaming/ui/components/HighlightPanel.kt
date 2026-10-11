package com.kynox.gaming.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kynox.gaming.ui.theme.KynoxShapes

/**
 * Kartu ringkasan utama sebuah layar: kartu 28 dp berlatar primaryContainer, teks onPrimaryContainer.
 * Dipakai satu kali per layar untuk nilai kunci (suhu CPU di Beranda, persen baterai di layar Baterai).
 */
@Composable
fun SummaryCard(
    modifier: Modifier = Modifier,
    padding: Dp = 20.dp,
    content: @Composable () -> Unit
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(KynoxShapes.hero)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(padding)
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onPrimaryContainer) {
            content()
        }
    }
}

/** Nama lama; sama dengan [SummaryCard]. */
@Composable
fun HighlightPanel(
    modifier: Modifier = Modifier,
    padding: Dp = 20.dp,
    content: @Composable () -> Unit
) = SummaryCard(modifier, padding, content)

/** Nilai besar dengan satuan kecil redup dan label. Untuk angka utama saja. */
@Composable
fun Readout(
    value: String,
    unit: String,
    label: String,
    modifier: Modifier = Modifier,
    valueStyle: TextStyle = MaterialTheme.typography.displaySmall,
    valueColor: Color = LocalContentColor.current
) {
    val muted = LocalContentColor.current.copy(alpha = 0.8f)
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = muted, maxLines = 1)
        Row {
            Text(value, style = valueStyle, color = valueColor, maxLines = 1, modifier = Modifier.alignByBaseline())
            if (unit.isNotEmpty()) {
                Text(
                    unit,
                    style = MaterialTheme.typography.titleMedium,
                    color = muted,
                    modifier = Modifier.padding(start = 3.dp).alignByBaseline()
                )
            }
        }
    }
}
