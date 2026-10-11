package com.kynox.gaming.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kynox.gaming.ui.theme.StatusDanger
import com.kynox.gaming.ui.theme.StatusGood
import com.kynox.gaming.ui.theme.StatusWarning

enum class MetricStatus { GOOD, WARNING, DANGER, NEUTRAL }

/** Warna status mengikuti tema aktif (lihat KynoxPalette). */
fun MetricStatus.color(): Color? = when (this) {
    MetricStatus.GOOD -> StatusGood
    MetricStatus.WARNING -> StatusWarning
    MetricStatus.DANGER -> StatusDanger
    MetricStatus.NEUTRAL -> null
}

private fun MetricStatus.spoken(): String? = when (this) {
    MetricStatus.GOOD -> "Baik"
    MetricStatus.WARNING -> "Waspada"
    MetricStatus.DANGER -> "Bahaya"
    MetricStatus.NEUTRAL -> null
}

/**
 * Baris label-nilai: label teks sekunder di kiri, nilai tebal di kanan, pemisah tipis di bawah.
 * Nilai bermasalah diwarnai lewat [status] (waspada/bahaya); status juga dibacakan ke pembaca layar,
 * jadi maknanya tidak hanya warna.
 */
@Composable
fun MetricRow(label: String, value: String, modifier: Modifier = Modifier, status: MetricStatus? = null) {
    val statusColor = status?.color()
    val spoken = status?.spoken()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .rowDivider()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.45f).padding(end = 12.dp)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
            color = statusColor ?: MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier
                .weight(0.55f)
                .then(if (spoken != null) Modifier.semantics { stateDescription = spoken } else Modifier)
        )
    }
}

/** Baris yang sama, dengan nama sistem desain. */
@Composable
fun KynoxValueRow(label: String, value: String, modifier: Modifier = Modifier, status: MetricStatus? = null) =
    MetricRow(label, value, modifier, status)
