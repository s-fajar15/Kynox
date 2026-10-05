package com.kynox.gaming.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kynox.gaming.ui.theme.KynoxShapes

/** Status = dot + text. The text carries the meaning; colour only reinforces it. */
@Composable
fun StatusPill(text: String, color: Color, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .widthIn(max = 220.dp)
            .background(color.copy(alpha = 0.16f).compositeOver(MaterialTheme.colorScheme.surface), KynoxShapes.chip)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Box(Modifier.size(6.dp).background(color, CircleShape))
        Text(
            text = text.ifBlank { "-" },
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 6.dp)
        )
    }
}

@Composable
fun KynoxStatus(text: String, color: Color, modifier: Modifier = Modifier) = StatusPill(text, color, modifier)

/** Small neutral tag, e.g. "Root", "Beta". Not a status. */
@Composable
fun KynoxBadge(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, KynoxShapes.chip)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}
