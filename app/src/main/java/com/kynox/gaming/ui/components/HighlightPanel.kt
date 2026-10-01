package com.kynox.gaming.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kynox.gaming.ui.theme.KynoxShapes
import com.kynox.gaming.ui.theme.kynoxColors

/** The one key readout of a screen. A sunken, borderless surface: quieter than a section, not tinted. */
@Composable
fun HighlightPanel(
    modifier: Modifier = Modifier,
    padding: Dp = 16.dp,
    content: @Composable () -> Unit
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(KynoxShapes.section)
            .background(MaterialTheme.kynoxColors.surfaceSunken)
            .padding(padding)
    ) { content() }
}

/** A big value with a small muted unit and label. Use for the primary number only. */
@Composable
fun Readout(
    value: String,
    unit: String,
    label: String,
    modifier: Modifier = Modifier,
    valueStyle: TextStyle = MaterialTheme.typography.displaySmall,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Row {
            Text(value, style = valueStyle, color = valueColor, maxLines = 1, modifier = Modifier.alignByBaseline())
            if (unit.isNotEmpty()) {
                Text(
                    unit,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 3.dp).alignByBaseline()
                )
            }
        }
    }
}
