package com.kynox.gaming.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kynox.gaming.ui.theme.KynoxShapes

/**
 * Kartu bagian: kartu tonal 24 dp (tanpa bingkai), judul kecil 13/600 teks sekunder + garis tipis,
 * lalu isi (biasanya baris label-nilai). [info] (opsional) adalah penjelasan yang dibuka lewat ikon (i).
 */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    info: String? = null,
    /** Matikan (false) untuk kartu yang isinya berakhir pada tombol/teks, bukan baris berpemisah: tanpa ini 1 dp dasar tombol terpotong. */
    trimTrailingDivider: Boolean = true,
    content: @Composable () -> Unit
) {
    Column(
        modifier
            .fillMaxWidth()
            .kynoxCard(KynoxShapes.section)
            .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 8.dp)
    ) {
        SectionHeader(title, leadingIcon = leadingIcon, trailing = trailing, info = info)
        Column(Modifier.fillMaxWidth().then(if (trimTrailingDivider) Modifier.hideTrailingDivider() else Modifier)) { content() }
    }
}

/**
 * Judul bagian. Dengan [divider] = true (bawaan) ada garis tipis di bawahnya (di dalam kartu);
 * dengan false ia menjadi label bagian polos di luar kartu (mis. grup di layar Lainnya).
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    info: String? = null,
    divider: Boolean = true
) {
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
            if (leadingIcon != null) {
                leadingIcon()
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (trailing != null) {
                Spacer(Modifier.width(8.dp))
                trailing()
            }
            if (info != null) InfoButton(info, title = title)
        }
        if (divider) Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
    }
}

/**
 * Kartu tonal: permukaan [container] (bawaan surface) tanpa bingkai, tanpa bayangan. Kedalaman datang
 * dari selisih warna permukaan terhadap latar, bukan dari garis tepi atau kotak bertumpuk.
 */
@Composable
fun Modifier.kynoxCard(
    shape: Shape = KynoxShapes.section,
    container: Color = MaterialTheme.colorScheme.surface
): Modifier = this
    .clip(shape)
    .background(container)
