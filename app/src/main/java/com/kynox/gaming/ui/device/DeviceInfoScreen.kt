package com.kynox.gaming.ui.device

import com.kynox.gaming.ui.components.VendorLogoTile
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

@Composable
private fun VendorHeader(info: DeviceInfo?) {
    val brand = VendorCatalog.brand(info?.manufacturer, info?.brand)
    val chip = VendorCatalog.chipset(info?.socManufacturer, info?.socModel, info?.gpuRenderer)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        VendorCard(
            badge = brand,
            title = info?.brand?.takeIf { it.isNotBlank() }?.replaceFirstChar { it.uppercase() } ?: "…",
            subtitle = info?.model ?: "…",
            modifier = Modifier.weight(1f)
        )
        VendorCard(
            badge = chip,
            title = info?.socManufacturer?.takeIf { it.isNotBlank() && !it.equals("unknown", true) } ?: chip.key.replaceFirstChar { it.uppercase() },
            subtitle = info?.socModel ?: "…",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun VendorCard(badge: VendorStyle, title: String, subtitle: String, modifier: Modifier) {
    Column(
        modifier.kynoxCard(KynoxShapes.section).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        VendorLogoTile(badge, Modifier.fillMaxWidth().height(64.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(1) {
                androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                VendorHeader(info)
                SectionCard(title = stringResource(R.string.section_system)) {
                    MetricRow(stringResource(R.string.field_manufacturer), info?.manufacturer ?: "…")
                    MetricRow(stringResource(R.string.field_brand), info?.brand ?: "…")
                    MetricRow(stringResource(R.string.field_model), info?.model ?: "…")
                    MetricRow(stringResource(R.string.field_device), info?.device ?: "…")
                    MetricRow(stringResource(R.string.field_android_version), info?.androidVersion ?: "…")
                    MetricRow(stringResource(R.string.field_android_sdk), info?.androidSdk?.toString() ?: "…")
                }
                SectionCard(title = stringResource(R.string.section_hardware)) {
                    MetricRow(stringResource(R.string.field_soc_manufacturer), info?.socManufacturer ?: "…")
                    MetricRow(stringResource(R.string.field_soc_model), info?.socModel ?: "…")
                    MetricRow(stringResource(R.string.field_cpu_arch), info?.cpuArchitecture ?: "…")
                    MetricRow(stringResource(R.string.field_cpu_cores), info?.cpuCoreCount?.toString() ?: "…")
                    MetricRow(stringResource(R.string.field_abi_supported), info?.supportedAbis ?: "…")
                    MetricRow(stringResource(R.string.field_gpu), info?.gpuRenderer ?: "…")
                    MetricRow(stringResource(R.string.field_gpu_vendor), info?.gpuVendor ?: "…")
                    MetricRow(stringResource(R.string.field_gles_version), info?.glEsVersion ?: "…")
                    MetricRow(stringResource(R.string.field_ram), info?.totalRamBytes?.let { formatBytesGb(it) } ?: "…")
                    MetricRow(
                        stringResource(R.string.field_swap),
                        run {
                            val swapTotal = info?.swapTotalBytes
                            val swapFree = info?.swapFreeBytes ?: 0L
                            when {
                                swapTotal == null -> "…"
                                swapTotal <= 0 -> stringResource(R.string.value_no)
                                else -> "${formatBytesGb(swapTotal - swapFree)} / ${formatBytesGb(swapTotal)}"
                            }
                        }
                    )
                }
                SectionCard(title = stringResource(R.string.section_display)) {
                    MetricRow(stringResource(R.string.field_resolution), info?.display?.resolution ?: "…")
                    MetricRow(stringResource(R.string.field_refresh_rate), info?.display?.refreshRateHz?.let { "%.0f Hz".format(it) } ?: "…")
                    MetricRow(stringResource(R.string.field_density), info?.display?.densityDpi?.let { "$it dpi" } ?: "…")
                    MetricRow(stringResource(R.string.field_screen_size), info?.display?.sizeInches?.let { "%.2f in".format(it) } ?: "…")
                    MetricRow(stringResource(R.string.field_hdr_support), info?.display?.hdrTypes ?: "…")
                }
                SectionCard(title = stringResource(R.string.section_storage)) {
                    MetricRow(stringResource(R.string.field_storage_total), info?.storage?.totalBytes?.let { formatBytesGb(it) } ?: "…")
                    MetricRow(stringResource(R.string.field_storage_used), info?.storage?.usedBytes?.let { formatBytesGb(it) } ?: "…")
                    MetricRow(stringResource(R.string.field_storage_free), info?.storage?.freeBytes?.let { formatBytesGb(it) } ?: "…")
                }
                SectionCard(title = stringResource(R.string.section_system_integrity)) {
                    MetricRow(stringResource(R.string.field_kernel_version), info?.kernelVersion ?: "…")
                    MetricRow(stringResource(R.string.field_security_patch), info?.securityPatch ?: "…")
                    MetricRow(stringResource(R.string.field_selinux_status), info?.selinuxStatus ?: "…")
                    MetricRow(
                        stringResource(R.string.field_root_status),
                        if (info?.isRooted == true) stringResource(R.string.value_rooted) else stringResource(R.string.value_not_rooted)
                    )
                    MetricRow(stringResource(R.string.field_root_implementation), info?.rootImplementation ?: "…")
                    MetricRow(stringResource(R.string.field_fingerprint), info?.fingerprint ?: "…")
                }
                }
            }
        }
    }
}
