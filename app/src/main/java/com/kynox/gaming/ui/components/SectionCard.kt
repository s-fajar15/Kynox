package com.kynox.gaming.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kynox.gaming.ui.theme.KynoxBrushes
import com.kynox.gaming.ui.theme.KynoxShapes
import com.kynox.gaming.ui.theme.kynoxColors

@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Column(modifier.fillMaxWidth()) {
        SectionHeader(title, modifier = Modifier.padding(horizontal = 4.dp), leadingIcon = leadingIcon, trailing = trailing)
        Spacer(Modifier.height(8.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .kynoxCard()
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) { content() }
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (leadingIcon != null) {
            leadingIcon()
            Spacer(Modifier.width(8.dp))
        } else {
            // Penanda gradien kecil di depan judul bagian: ciri khas tampilan 2.2.
            Box(Modifier.width(4.dp).height(14.dp).clip(KynoxShapes.pill).background(KynoxBrushes.accent))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (trailing != null) {
            Spacer(Modifier.width(10.dp))
            trailing()
        }
    }
}

/**
 * Kartu Kynox 2.2: permukaan bertingkat (lebih terang di atas, menyatu ke dasar) dengan tepi
 * bergradien tipis, bukan kotak datar berbingkai seragam.
 */
@Composable
fun Modifier.kynoxCard(shape: Shape = KynoxShapes.section): Modifier {
    val top = MaterialTheme.kynoxColors.surfaceRaised
    val bottom = MaterialTheme.colorScheme.surface
    val edgeStrong = MaterialTheme.kynoxColors.outlineStrong.copy(alpha = 0.55f)
    val edgeSoft = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    return this
        .clip(shape)
        .background(Brush.verticalGradient(listOf(top, bottom)))
        .border(1.dp, Brush.linearGradient(listOf(edgeStrong, edgeSoft)), shape)
}
