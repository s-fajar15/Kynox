package com.kynox.gaming.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import com.kynox.gaming.ui.theme.KynoxIcons
import com.kynox.gaming.ui.theme.StatusWarning
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kynox.gaming.ui.theme.KynoxShapes

/** Padding isi layar daftar: 16 dp di sisi, jarak bawah cukup agar kartu terakhir tidak menempel ke bottom bar. */
val KynoxListPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp)

/** Jarak vertikal antar kartu (12 dp). */
val KynoxCardSpacing = Arrangement.spacedBy(12.dp)

/**
 * Kotak bento: label 13/600 teks sekunder, angka 40/700 + satuan, subteks, dan batang progres 6 dp.
 * [subColor] hanya diisi bila subteks adalah status (baik/waspada/bahaya); selain itu teks sekunder.
 * [progress] null = tanpa batang. Subteks selalu menyisakan satu baris agar dua kotak berdampingan sama tinggi.
 */
@Composable
fun MetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String = "",
    sub: String? = null,
    subColor: Color = Color.Unspecified,
    progress: Float? = null,
    progressColor: Color = MaterialTheme.colorScheme.primary,
    onClick: (() -> Unit)? = null,
    asideLabel: String? = null,
    aside: String? = null,
    asideColor: Color = Color.Unspecified
) {
    Column(
        modifier
            .kynoxCard(KynoxShapes.section)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(16.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth()) {
            Text(
                value,
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                modifier = Modifier.alignByBaseline()
            )
            if (unit.isNotEmpty()) {
                Text(
                    unit,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 4.dp).alignByBaseline()
                )
            }
            if (aside != null) {
                androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End, modifier = Modifier.align(Alignment.CenterVertically)) {
                    if (asideLabel != null) {
                        Text(asideLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                    Text(
                        aside,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (asideColor == Color.Unspecified) MaterialTheme.colorScheme.onSurface else asideColor,
                        maxLines = 1
                    )
                }
            }
        }
        Text(
            sub?.takeIf { it.isNotBlank() } ?: " ",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (subColor == Color.Unspecified) FontWeight.Normal else FontWeight.Medium,
            color = if (subColor == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else subColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (progress != null) {
            Spacer(Modifier.height(12.dp))
            KLinearProgress(progress, fillColor = progressColor)
        }
    }
}

/**
 * Baris saklar M3 standar: judul (dan keterangan singkat opsional) di kiri, saklar di kanan.
 * Seluruh baris bisa diketuk (target >= 48 dp) dan dibaca sebagai satu saklar oleh pembaca layar.
 */
@Composable
fun KynoxSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true
) {
    Row(
        modifier
            .fillMaxWidth()
            .rowDivider()
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .heightIn(min = 56.dp)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/** Label grup di luar kartu (mis. "Performa", "Daya" di layar Lainnya): teks sekunder 13/600, tanpa garis. */
@Composable
fun GroupLabel(title: String, modifier: Modifier = Modifier) {
    SectionHeader(title, modifier = modifier.padding(horizontal = 8.dp), divider = false)
}

/** Bentuk satu baris dalam daftar bergrup: sudut luar 24 dp pada baris pertama/terakhir, sudut dalam kecil. */
fun groupedShape(index: Int, count: Int): Shape = RoundedCornerShape(
    topStart = if (index == 0) 24.dp else 4.dp,
    topEnd = if (index == 0) 24.dp else 4.dp,
    bottomStart = if (index == count - 1) 24.dp else 4.dp,
    bottomEnd = if (index == count - 1) 24.dp else 4.dp
)

/**
 * Daftar panjang (aplikasi, proses) sebagai satu kelompok kartu tonal: setiap baris bagian dari kartu yang sama,
 * dipisah garis tipis. Memakai LazyColumn, jadi tetap efisien untuk ratusan baris. Pemanggil harus membiarkan
 * jarak antar item LazyColumn nol dan memberi jarak sendiri pada item di luar kelompok.
 */
fun <T> LazyListScope.groupedItems(list: List<T>, key: (T) -> Any, content: @Composable (T) -> Unit) {
    itemsIndexed(list, key = { _, item -> key(item) }) { index, item ->
        val last = index == list.lastIndex
        Box(Modifier.fillMaxWidth().clip(groupedShape(index, list.size)).background(MaterialTheme.colorScheme.surface)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .then(if (last) Modifier else Modifier.rowDivider(startInset = 16.dp))
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) { content(item) }
        }
    }
}

/** Kartu peringatan penting yang selalu terlihat (bukan di balik (i)): ikon + judul + penjelasan singkat. */
@Composable
fun NoticeCard(title: String, text: String, modifier: Modifier = Modifier, tone: Color = StatusWarning) {
    Row(
        modifier
            .fillMaxWidth()
            .kynoxCard(KynoxShapes.section, container = tone.copy(alpha = 0.16f).compositeOver(MaterialTheme.colorScheme.surface))
            .padding(16.dp)
    ) {
        Icon(KynoxIcons.Info, contentDescription = "Peringatan", tint = tone, modifier = Modifier.padding(end = 12.dp, top = 2.dp).size(20.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Kolom cari berbentuk pill di atas daftar. */
@Composable
fun KynoxSearchField(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        leadingIcon = { Icon(KynoxIcons.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
        placeholder = { Text(placeholder) },
        shape = KynoxShapes.pill,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedBorderColor = Color.Transparent,
            focusedBorderColor = MaterialTheme.colorScheme.primary
        )
    )
}
