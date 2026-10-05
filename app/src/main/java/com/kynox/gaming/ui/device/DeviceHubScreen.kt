package com.kynox.gaming.ui.device

import com.kynox.gaming.ui.components.VendorLogoTile
import androidx.compose.foundation.layout.width
import com.kynox.gaming.ui.components.VendorStyle
import com.kynox.gaming.ui.components.VendorCatalog
import com.kynox.gaming.ui.components.VendorBadge
import com.kynox.gaming.ui.components.SectionHeader
import com.kynox.gaming.ui.components.KynoxListRow
import com.kynox.gaming.ui.components.ListCard
import com.kynox.gaming.ui.components.IconTile
import com.kynox.gaming.ui.components.kynoxCard
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kynox.gaming.AppContainer
import com.kynox.gaming.R
import com.kynox.gaming.ui.components.GenericViewModelFactory
import com.kynox.gaming.ui.components.KynoxTopBar
import com.kynox.gaming.ui.components.SectionCard
import com.kynox.gaming.ui.navigation.Dest
import com.kynox.gaming.ui.theme.KynoxAccentDark
import com.kynox.gaming.ui.theme.KynoxIcons
import com.kynox.gaming.ui.theme.kynoxColors
import androidx.compose.ui.res.stringResource
import com.kynox.gaming.ui.theme.KynoxShapes

private data class QuickDeviceItem(val title: String, val value: String, val icon: ImageVector)

private data class LinkItem(val title: String, val description: String, val icon: ImageVector, val dest: Dest)

@Composable
fun DeviceHubScreen(container: AppContainer, onNavigate: (Dest) -> Unit) {
    val vm: DeviceInfoViewModel = viewModel(factory = GenericViewModelFactory { DeviceInfoViewModel(container.deviceInfoRepository) })
    val info by vm.info.collectAsState()
    val gb = 1073741824f

    Scaffold(topBar = { KynoxTopBar(title = "Perangkat", subtitle = "Informasi lengkap perangkatmu.") }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                DeviceIdentityCard(
                    info?.model ?: "Memuat…", info?.androidVersion ?: "Android", info?.display?.resolution ?: "",
                    VendorCatalog.brand(info?.manufacturer, info?.brand)
                )
            }
            item {
                val ram = info?.totalRamBytes?.let { "%.0f GB".format(it / gb) } ?: "--"
                val storage = info?.storage?.totalBytes?.let { "%.0f GB".format(it / gb) } ?: "--"
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DeviceTile(
                            "CPU", info?.socModel ?: "--", info?.cpuCoreCount?.takeIf { it > 0 }?.let { "$it Core" }, KynoxIcons.Cpu, Modifier.weight(1f),
                            leading = { VendorBadge(VendorCatalog.chipset(info?.socManufacturer, info?.socModel, info?.gpuRenderer), size = 40.dp) }
                        )
                        DeviceTile("GPU", info?.gpuRenderer ?: "--", null, KynoxIcons.Gpu, Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DeviceTile("RAM", ram, null, KynoxIcons.Ram, Modifier.weight(1f))
                        DeviceTile("Storage", storage, info?.storage?.usedBytes?.let { "%.0f GB terpakai".format(it / gb) }, KynoxIcons.Display, Modifier.weight(1f))
                    }
                }
            }
            item {
                DeviceGroup(
                    "Informasi sistem",
                    listOf(
                        LinkItem("Informasi Perangkat", "Model, build, kernel, root", KynoxIcons.Info, Dest.DeviceInfo),
                        LinkItem("Suhu & Termal", "CPU, GPU, baterai, sensor", KynoxIcons.Thermal, Dest.Thermal),
                        LinkItem("Kesehatan Baterai", "Kondisi dan siklus baterai", KynoxIcons.Battery, Dest.Battery),
                        LinkItem("Refresh Rate", "Atur refresh rate per aplikasi", KynoxIcons.RefreshRate, Dest.RefreshRate),
                        LinkItem("Proses Berjalan", "Lihat proses aktif", KynoxIcons.Processes, Dest.Processes)
                    ),
                    onNavigate
                )
            }
            item {
                DeviceGroup(
                    "Sesi & analisis",
                    listOf(
                        LinkItem("Rekam Sesi", "Rekam FPS, CPU, GPU, suhu, dan daya", KynoxIcons.Session, Dest.Session),
                        LinkItem("Riwayat Sesi", "Laporan rekaman yang tersimpan", KynoxIcons.Logs, Dest.SessionHistory),
                        LinkItem("Bandingkan Sesi", "Bandingkan dua laporan berdampingan", KynoxIcons.Monitor, Dest.SessionCompare)
                    ),
                    onNavigate
                )
            }
            item {
                DeviceGroup(
                    "Kynox Tools",
                    listOf(
                        LinkItem("Automation Rules", "Aturan otomatis per aplikasi", KynoxIcons.Profiles, Dest.Automation),
                        LinkItem("Network Monitor", "Traffic dan riwayat jaringan", KynoxIcons.Monitor, Dest.Network),
                        LinkItem("SELinux Monitor", "Status enforcement dan AVC", KynoxIcons.Root, Dest.Selinux),
                        LinkItem("Kynox Snapshot", "Simpan kondisi sistem saat ini", KynoxIcons.Device, Dest.Snapshot),
                        LinkItem("Command Console", "Jalankan command melalui root", KynoxIcons.Root, Dest.CommandConsole),
                        LinkItem("Kynox Diagnostics", "Pemeriksaan kesehatan sistem", KynoxIcons.Info, Dest.Diagnostics)
                    ),
                    onNavigate
                )
            }
            item {
                DeviceGroup(
                    "Kontrol & sistem",
                    listOf(
                        LinkItem("Bersihkan RAM & Cache", "Bebaskan memori dan hapus cache", KynoxIcons.Debloat, Dest.Cleaner),
                        LinkItem("Kontrol Sistem", "Baterai dan fungsi sistem Kynox", KynoxIcons.Control, Dest.Control),
                        LinkItem("Profil Performa", "Seimbang, intensif, hemat daya", KynoxIcons.Profiles, Dest.Profiles),
                        LinkItem("Manajer CPU", "Core, frekuensi, governor", KynoxIcons.Cpu, Dest.Cpu),
                        LinkItem("Manajer GPU", "Frekuensi dan telemetry", KynoxIcons.Gpu, Dest.Gpu),
                        LinkItem("Manajer Root", "Status akses dan komponen root", KynoxIcons.Root, Dest.RootManager),
                        LinkItem("Debloat", "Kelola aplikasi sistem", KynoxIcons.Debloat, Dest.Debloat),
                        LinkItem("Log", "Riwayat perubahan sistem", KynoxIcons.Logs, Dest.Logs)
                    ),
                    onNavigate
                )
            }
        }
    }
}

@Composable
private fun DeviceGroup(title: String, links: List<LinkItem>, onNavigate: (Dest) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionHeader(title, modifier = Modifier.padding(horizontal = 4.dp))
        ListCard {
            links.forEach { link ->
                KynoxListRow(link.title, link.description, link.icon, onClick = { onNavigate(link.dest) })
            }
        }
    }
}

@Composable
private fun DeviceIdentityCard(model: String, android: String, resolution: String, brand: VendorStyle) {
    Row(
        Modifier.fillMaxWidth().kynoxCard(KynoxShapes.hero).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        VendorLogoTile(brand, Modifier.width(112.dp).height(76.dp))
        Spacer(Modifier.size(16.dp))
        Column(Modifier.weight(1f)) {
            Text(model, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 2)
            Text(
                if (resolution.isBlank()) "Android $android" else "Android $android  ·  $resolution",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DeviceTile(
    title: String, value: String, sub: String?, icon: ImageVector, modifier: Modifier,
    leading: (@Composable () -> Unit)? = null
) {
    Row(
        modifier.height(96.dp).kynoxCard(KynoxShapes.section).padding(13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) leading() else IconTile(icon, size = 40.dp)
        Spacer(Modifier.size(11.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            if (sub != null) Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}
