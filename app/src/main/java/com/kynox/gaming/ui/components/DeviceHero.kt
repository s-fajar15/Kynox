package com.kynox.gaming.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kynox.gaming.domain.model.DeviceInfo
import com.kynox.gaming.ui.theme.KynoxShapes
import com.kynox.gaming.ui.theme.kynoxColors

/** Ukuran penjualan umum, dipakai untuk membulatkan RAM/penyimpanan ke angka yang dikenal pengguna. */
private val RAM_SIZES_GB = listOf(1, 2, 3, 4, 6, 8, 10, 12, 16, 18, 24, 32)
private val STORAGE_SIZES_GB = listOf(8, 16, 32, 64, 128, 256, 512, 1024, 2048)

private const val GIB = 1024.0 * 1024.0 * 1024.0

/** Membulatkan ke atas ke ukuran standar terdekat; di luar daftar, dibulatkan biasa. */
internal fun roundToSaleSizeGb(bytes: Long, sizes: List<Int>): Int {
    if (bytes <= 0L) return 0
    val gb = bytes / GIB
    return sizes.firstOrNull { it >= gb - 0.25 } ?: Math.round(gb).toInt()
}

internal fun ramLabel(bytes: Long): String? =
    roundToSaleSizeGb(bytes, RAM_SIZES_GB).takeIf { it > 0 }?.let { "$it GB RAM" }

internal fun storageLabel(bytes: Long): String? =
    roundToSaleSizeGb(bytes, STORAGE_SIZES_GB).takeIf { it > 0 }?.let { if (it >= 1024) "${it / 1024} TB" else "$it GB" }

/** Ambil nomor versi kernel dari isi /proc/version (mis. "4.14.356-gabc"), atau potongan awalnya. */
internal fun kernelShort(raw: String?): String {
    if (raw.isNullOrBlank()) return "--"
    return Regex("Linux version (\\S+)").find(raw)?.groupValues?.getOrNull(1) ?: raw.trim().take(40)
}

/** Chip spesifikasi ringkas: latar surface2, sudut 10 dp, teks 13/600. */
@Composable
fun SpecChip(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        modifier = modifier
            .background(MaterialTheme.kynoxColors.surfaceSunken, KynoxShapes.chip)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    )
}

/**
 * Kartu identitas perangkat: logo merek (dari drawable), nama pasar (atau model), pabrikan - model - kode perangkat,
 * lalu chip spesifikasi ringkas. Semua nilai berasal dari data perangkat; yang tidak terbaca dilewati.
 * [compact] memperkecil logo.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DeviceHeroCard(info: DeviceInfo?, modifier: Modifier = Modifier, compact: Boolean = false) {
    val brand = remember(info?.manufacturer, info?.brand) { VendorCatalog.brand(info?.manufacturer, info?.brand) }
    val market = info?.marketName.orEmpty()
    val model = info?.model.orEmpty()
    val codename = info?.device.orEmpty()
    val title = market.ifBlank { model }.ifBlank { "Memuat…" }
    val maker = (info?.manufacturer?.takeIf { it.isNotBlank() } ?: info?.brand?.takeIf { it.isNotBlank() })
        ?.replaceFirstChar { it.uppercase() }
    val subtitle = buildList {
        maker?.let { add(it) }
        // Bila judulnya nama pasar, tampilkan juga modelnya supaya keduanya terlihat.
        if (market.isNotBlank() && model.isNotBlank()) add(model)
        if (codename.isNotBlank() && codename != model) add(codename)
    }.joinToString(" · ")

    val soc = info?.let {
        listOf(it.socManufacturer, it.socModel)
            .filter { v -> v.isNotBlank() && !v.equals("unknown", true) }
            .joinToString(" ")
            .ifBlank { null }
    }
    val chips = buildList {
        info?.let {
            add("Android ${it.androidVersion}")
            soc?.let { s -> add(s) }
            ramLabel(it.totalRamBytes)?.let { r -> add(r) }
            storageLabel(it.storage.totalBytes)?.let { s -> add(s) }
            if (it.display.refreshRateHz > 0f) add("%.0f Hz".format(it.display.refreshRateHz))
        }
    }

    Column(
        modifier.fillMaxWidth().kynoxCard(KynoxShapes.hero).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val logo = if (compact) 64.dp else 84.dp
            VendorLogoTile(brand, Modifier.size(logo), corner = 20.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        if (chips.isNotEmpty()) {
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                chips.forEach { SpecChip(it) }
            }
        }
    }
}
