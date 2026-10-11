package com.kynox.gaming.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kynox.gaming.ui.theme.KynoxIcons
import com.kynox.gaming.ui.theme.KynoxShapes
import com.kynox.gaming.ui.theme.StatusDanger
import com.kynox.gaming.ui.theme.StatusDisabled
import com.kynox.gaming.ui.theme.StatusGood

/**
 * Penanda status berupa teks dengan latar tipis berwarna status (tanpa titik). Teksnya yang membawa makna;
 * warna hanya memperkuat. Hanya dipakai bila ada status yang perlu dibaca; bukan hiasan.
 */
@Composable
fun StatusPill(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text = text.ifBlank { "-" },
        color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.labelMedium,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .widthIn(max = 220.dp)
            .background(color.copy(alpha = 0.18f).compositeOver(MaterialTheme.colorScheme.surface), KynoxShapes.pill)
            .padding(horizontal = 12.dp, vertical = 5.dp)
    )
}

@Composable
fun KynoxStatus(text: String, color: Color, modifier: Modifier = Modifier) = StatusPill(text, color, modifier)

/** Pill tonal netral untuk nilai kunci di kartu ringkasan (mis. "Normal", "Mengisi daya (AC)", "Standar"). */
@Composable
fun TonalPill(
    text: String,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceVariant,
    content: Color = MaterialTheme.colorScheme.onSurface
) {
    Text(
        text = text.ifBlank { "-" },
        color = content,
        style = MaterialTheme.typography.labelMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .widthIn(max = 220.dp)
            .background(container, KynoxShapes.pill)
            .padding(horizontal = 14.dp, vertical = 6.dp)
    )
}

/** Tag kecil netral, mis. "Root", "Beta". Bukan status. */
@Composable
fun KynoxBadge(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, KynoxShapes.chip)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

/** Label standar untuk fitur yang tidak disediakan kernel/perangkat ini. */
const val UNSUPPORTED_LABEL = "Kernel tidak mendukung"

/** Penanda kecil untuk header kartu: fitur ini tidak tersedia di kernel ini. */
@Composable
fun UnsupportedPill(modifier: Modifier = Modifier) =
    StatusPill(UNSUPPORTED_LABEL, StatusDisabled, modifier)

/** Isi kartu untuk fitur yang tidak didukung: penanda jelas + alasan teknisnya. */
@Composable
fun UnsupportedNotice(reason: String?, modifier: Modifier = Modifier) {
    Column(modifier) {
        UnsupportedPill()
        if (!reason.isNullOrBlank()) {
            Spacer(Modifier.padding(top = 6.dp))
            Text(
                text = reason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Banner pesan hasil aksi (gagal/berhasil) yang bisa ditutup. Pesan yang tidak tampil = kegagalan diam-diam. */
@Composable
fun MessageBanner(message: String, onDismiss: () -> Unit, isError: Boolean = true, modifier: Modifier = Modifier) {
    val tone = if (isError) StatusDanger else StatusGood
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .background(tone.copy(alpha = 0.16f).compositeOver(MaterialTheme.colorScheme.surface), KynoxShapes.section)
            .padding(start = 14.dp, top = 4.dp, bottom = 4.dp, end = 4.dp)
    ) {
        // Ikon berbeda (peringatan vs centang) supaya jenis pesan tidak hanya dibedakan oleh warna.
        Icon(
            if (isError) KynoxIcons.Info else KynoxIcons.CheckOk,
            contentDescription = if (isError) "Galat" else "Berhasil",
            tint = tone,
            modifier = Modifier.padding(end = 10.dp).size(20.dp)
        )
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f).padding(vertical = 8.dp)
        )
        Text(
            text = "Tutup",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier
                .heightIn(min = 48.dp)
                .clickable(onClick = onDismiss)
                .padding(horizontal = 12.dp, vertical = 14.dp)
        )
    }
}
