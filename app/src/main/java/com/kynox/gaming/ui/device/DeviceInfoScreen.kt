package com.kynox.gaming.ui.device

import com.kynox.gaming.ui.components.VendorLogoTile
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.remember
import com.kynox.gaming.ui.components.DeviceHeroCard
import com.kynox.gaming.ui.components.KLinearProgress
import androidx.compose.foundation.layout.height
import com.kynox.gaming.ui.components.kynoxCard
import com.kynox.gaming.ui.components.VendorStyle
import com.kynox.gaming.ui.components.VendorCatalog
import com.kynox.gaming.ui.components.VendorBadge
import com.kynox.gaming.domain.model.DeviceInfo
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Alignment
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kynox.gaming.AppContainer
import com.kynox.gaming.R
import com.kynox.gaming.ui.components.DetailTopBar
import com.kynox.gaming.ui.components.GenericViewModelFactory
import com.kynox.gaming.ui.components.MetricRow
import com.kynox.gaming.ui.components.SectionCard
import com.kynox.gaming.ui.theme.KynoxShapes

private fun formatBytesGb(bytes: Long): String {
    val gb = bytes / (1024.0 * 1024.0 * 1024.0)
    return "%.2f GB".format(gb)
}

private fun uptimeText(ms: Long): String {
    if (ms <= 0L) return "--"
    val minutes = ms / 60_000L
    val days = minutes / (60 * 24)
    val hours = (minutes / 60) % 24
    val mins = minutes % 60
    return when {
        days > 0 -> "$days hari $hours jam"
        hours > 0 -> "$hours jam $mins menit"
        else -> "$mins menit"
    }
}

@Composable
private fun UsageBar(label: String, detail: String, fraction: Float) {
    Column(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 6.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text(detail, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(6.dp))
        KLinearProgress(fraction.coerceIn(0f, 1f))
    }
}

/** Kartu chipset: logo vendor SoC, nama, dan ringkasan CPU/GPU. */
@Composable
private fun ChipsetCard(info: DeviceInfo?) {
    val chip = remember(info?.socManufacturer, info?.socModel, info?.gpuRenderer) {
        VendorCatalog.chipset(info?.socManufacturer, info?.socModel, info?.gpuRenderer)
    }
    val soc = info?.let {
        listOf(it.socManufacturer, it.socModel).filter { v -> v.isNotBlank() && !v.equals("unknown", true) }.joinToString(" ")
    }.orEmpty().ifBlank { info?.hardware.orEmpty() }.ifBlank { "--" }
    Row(
        Modifier.fillMaxWidth().kynoxCard(KynoxShapes.section).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        VendorLogoTile(chip, Modifier.width(112.dp).height(64.dp), corner = 16.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(soc, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val line = listOfNotNull(
                info?.cpuCoreCount?.takeIf { it > 0 }?.let { "$it core" },
                info?.cpuArchitecture?.takeIf { it.isNotBlank() && it != "Unknown" }
            ).joinToString(" \u00B7 ")
            if (line.isNotBlank()) Text(line, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            info?.gpuRenderer?.takeIf { it.isNotBlank() && it != "Unknown" }?.let {
                Text("GPU  $it", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun DeviceInfoScreen(container: AppContainer, onBack: () -> Unit) {
    val viewModel: DeviceInfoViewModel = viewModel(
        factory = GenericViewModelFactory { DeviceInfoViewModel(container.deviceInfoRepository) }
    )
    val info by viewModel.info.collectAsState()

    Scaffold(topBar = { DetailTopBar(title = stringResource(R.string.device_info_title), onBack = onBack) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = com.kynox.gaming.ui.components.KynoxListPadding,
            verticalArrangement = com.kynox.gaming.ui.components.KynoxCardSpacing
        ) {
            item(key = "hero", contentType = "hero") { DeviceHeroCard(info) }
            item(key = "chipset", contentType = "chip") { ChipsetCard(info) }
            item(key = "sections", contentType = "sections") {
                androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionCard(title = stringResource(R.string.section_system)) {
                    MetricRow(stringResource(R.string.field_manufacturer), info?.manufacturer ?: "--")
                    MetricRow(stringResource(R.string.field_brand), info?.brand ?: "--")
                    info?.marketName?.takeIf { it.isNotBlank() }?.let { MetricRow("Nama pasar", it) }
                    MetricRow(stringResource(R.string.field_model), info?.model ?: "--")
                    MetricRow(stringResource(R.string.field_device), info?.device ?: "--")
                    MetricRow(stringResource(R.string.field_android_version), info?.androidVersion ?: "--")
                    MetricRow(stringResource(R.string.field_android_sdk), info?.androidSdk?.toString() ?: "--")
                    info?.buildDisplay?.takeIf { it.isNotBlank() }?.let { MetricRow("Versi build", it) }
                }
                SectionCard(title = stringResource(R.string.section_hardware)) {
                    info?.board?.takeIf { it.isNotBlank() }?.let { MetricRow("Board", it) }
                    info?.hardware?.takeIf { it.isNotBlank() }?.let { MetricRow("Hardware", it) }
                    MetricRow(stringResource(R.string.field_soc_manufacturer), info?.socManufacturer ?: "--")
                    MetricRow(stringResource(R.string.field_soc_model), info?.socModel ?: "--")
                    MetricRow(stringResource(R.string.field_cpu_arch), info?.cpuArchitecture ?: "--")
                    MetricRow(stringResource(R.string.field_cpu_cores), info?.cpuCoreCount?.toString() ?: "--")
                    MetricRow(stringResource(R.string.field_abi_supported), info?.supportedAbis ?: "--")
                    MetricRow(stringResource(R.string.field_gpu), info?.gpuRenderer ?: "--")
                    MetricRow(stringResource(R.string.field_gpu_vendor), info?.gpuVendor ?: "--")
                    MetricRow(stringResource(R.string.field_gles_version), info?.glEsVersion ?: "--")
                    MetricRow(stringResource(R.string.field_ram), info?.totalRamBytes?.let { formatBytesGb(it) } ?: "--")
                    MetricRow(
                        stringResource(R.string.field_swap),
                        run {
                            val swapTotal = info?.swapTotalBytes
                            val swapFree = info?.swapFreeBytes ?: 0L
                            when {
                                swapTotal == null -> "--"
                                swapTotal <= 0 -> stringResource(R.string.value_no)
                                else -> "${formatBytesGb(swapTotal - swapFree)} / ${formatBytesGb(swapTotal)}"
                            }
                        }
                    )
                }
                SectionCard(title = stringResource(R.string.section_display)) {
                    MetricRow(stringResource(R.string.field_resolution), info?.display?.resolution ?: "--")
                    MetricRow(stringResource(R.string.field_refresh_rate), info?.display?.refreshRateHz?.let { "%.0f Hz".format(it) } ?: "--")
                    MetricRow(stringResource(R.string.field_density), info?.display?.densityDpi?.let { "$it dpi" } ?: "--")
                    MetricRow(stringResource(R.string.field_screen_size), info?.display?.sizeInches?.let { "%.2f in".format(it) } ?: "--")
                    MetricRow(stringResource(R.string.field_hdr_support), info?.display?.hdrTypes ?: "--")
                }
                SectionCard(title = stringResource(R.string.section_storage)) {
                    info?.storage?.takeIf { it.totalBytes > 0 }?.let {
                        UsageBar("Terpakai", "${formatBytesGb(it.usedBytes)} / ${formatBytesGb(it.totalBytes)}", it.usedBytes.toFloat() / it.totalBytes)
                    }
                    MetricRow(stringResource(R.string.field_storage_total), info?.storage?.totalBytes?.let { formatBytesGb(it) } ?: "--")
                    MetricRow(stringResource(R.string.field_storage_used), info?.storage?.usedBytes?.let { formatBytesGb(it) } ?: "--")
                    MetricRow(stringResource(R.string.field_storage_free), info?.storage?.freeBytes?.let { formatBytesGb(it) } ?: "--")
                }
                SectionCard(title = stringResource(R.string.section_system_integrity)) {
                    MetricRow(stringResource(R.string.field_kernel_version), info?.kernelVersion ?: "--")
                    MetricRow(stringResource(R.string.field_security_patch), info?.securityPatch ?: "--")
                    MetricRow(stringResource(R.string.field_selinux_status), info?.selinuxStatus ?: "--")
                    MetricRow(
                        stringResource(R.string.field_root_status),
                        if (info?.isRooted == true) stringResource(R.string.value_rooted) else stringResource(R.string.value_not_rooted)
                    )
                    MetricRow(stringResource(R.string.field_root_implementation), info?.rootImplementation ?: "--")
                    MetricRow(stringResource(R.string.field_fingerprint), info?.fingerprint ?: "--")
                    MetricRow("Waktu aktif", uptimeText(android.os.SystemClock.elapsedRealtime()))
                }
                }
            }
        }
    }
}
