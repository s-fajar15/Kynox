package com.kynox.gaming.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.kynox.gaming.ui.theme.KynoxIcons
import com.kynox.gaming.ui.theme.KynoxShapes

/**
 * Penjelasan yang tidak perlu terus terlihat dipindah ke sini: ikon (i) yang membuka dialog.
 * Layar hanya menampilkan label dan nilai; baca penjelasan hanya bila perlu. Target sentuh 48 dp.
 */
@Composable
fun InfoButton(text: String, modifier: Modifier = Modifier, title: String? = null) {
    var open by remember { mutableStateOf(false) }
    Box(
        modifier
            .size(48.dp)
            .clip(KynoxShapes.circle)
            .clickable(role = Role.Button) { open = true },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            KynoxIcons.Info,
            contentDescription = if (title != null) "Info: $title" else "Info",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title ?: "Info", style = MaterialTheme.typography.titleMedium) },
            text = { Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Tutup") } },
            shape = KynoxShapes.dialog
        )
    }
}
