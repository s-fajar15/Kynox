package com.kynox.gaming.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kynox.gaming.ui.theme.StatusDanger
import com.kynox.gaming.ui.theme.StatusGood
import com.kynox.gaming.ui.theme.StatusWarning

enum class MetricStatus { GOOD, WARNING, DANGER, NEUTRAL }

fun MetricStatus.color(): Color? = when (this) {
    MetricStatus.GOOD -> StatusGood
    MetricStatus.WARNING -> StatusWarning
    MetricStatus.DANGER -> StatusDanger
    MetricStatus.NEUTRAL -> null
}

/** Label on the left, value on the right. A status dot is added only when the value has a state. */
@Composable
fun MetricRow(label: String, value: String, modifier: Modifier = Modifier, status: MetricStatus? = null) {
    val statusColor = status?.color()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f).padding(end = 12.dp)
        )
        if (statusColor != null) {
            Box(Modifier.size(6.dp).background(statusColor, CircleShape))
            Box(Modifier.padding(start = 8.dp)) {
                Text(value, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.End)
            }
        } else {
            Text(value, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.End)
        }
    }
}

/** Same row, named as in the design system. */
@Composable
fun KynoxValueRow(label: String, value: String, modifier: Modifier = Modifier, status: MetricStatus? = null) =
    MetricRow(label, value, modifier, status)
