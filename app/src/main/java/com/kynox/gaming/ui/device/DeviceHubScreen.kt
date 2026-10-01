package com.kynox.gaming.ui.device

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

private data class QuickDeviceItem(val title: String, val value: String, val icon: ImageVector)

@Composable
fun DeviceHubScreen(container: AppContainer, onNavigate: (Dest) -> Unit) {
    val vm: DeviceInfoViewModel = viewModel(factory = GenericViewModelFactory { DeviceInfoViewModel(container.deviceInfoRepository) })
    val info by vm.info.collectAsState()

    Scaffold(topBar = { KynoxTopBar(title = "Perangkat") }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 74.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                DeviceIdentityCard(info?.model ?: "Memuat…", info?.androidVersion ?: "Android", info?.display?.resolution ?: "")
            }
            item {
                val ram = info?.totalRamBytes?.let { "%.0f GB".format(it / 1073741824f) } ?: "--"
                val storage = info?.storage?.totalBytes?.let { "%.0f GB".format(it / 1073741824f) } ?: "--"
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DeviceTile("CPU", info?.socModel ?: "--", KynoxIcons.Cpu, Modifier.weight(1f))
                        DeviceTile("GPU", info?.gpuRenderer ?: "--", KynoxIcons.Gpu, Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DeviceTile("RAM", ram, KynoxIcons.Ram, Modifier.weight(1f))
                        DeviceTile("Storage", storage, KynoxIcons.Display, Modifier.weight(1f))
                    }
                }
            }
            item {
                SectionCard("Informasi sistem") {
                    DeviceLink("Informasi Perangkat", "Model, build, kernel, root", KynoxIcons.Info) { onNavigate(Dest.DeviceInfo) }
                    DeviceLink("Suhu & Termal", "CPU, GPU, baterai, sensor", KynoxIcons.Thermal) { onNavigate(Dest.Thermal) }
                    DeviceLink("Kesehatan Baterai", "Kondisi dan siklus baterai", KynoxIcons.Battery) { onNavigate(Dest.Battery) }
                    DeviceLink("Refresh Rate", "Atur refresh rate per aplikasi", KynoxIcons.RefreshRate) { onNavigate(Dest.RefreshRate) }
                    DeviceLink("Proses Berjalan", "Lihat proses aktif", KynoxIcons.Processes) { onNavigate(Dest.Processes) }
                }
            }
            item {
                SectionCard("Sesi & analisis") {
                    DeviceLink("Rekam Sesi", "Rekam FPS, CPU, GPU, suhu, dan daya", KynoxIcons.Session) { onNavigate(Dest.Session) }
                    DeviceLink("Riwayat Sesi", "Laporan rekaman yang tersimpan", KynoxIcons.Logs) { onNavigate(Dest.SessionHistory) }
                    DeviceLink("Bandingkan Sesi", "Bandingkan dua laporan berdampingan", KynoxIcons.Monitor) { onNavigate(Dest.SessionCompare) }
                }
            }
            item {
                SectionCard("Kynox Tools") {
                    DeviceLink("Automation Rules", "Aturan otomatis per aplikasi", KynoxIcons.Profiles) { onNavigate(Dest.Automation) }
                    DeviceLink("Network Monitor", "Traffic dan riwayat jaringan", KynoxIcons.Monitor) { onNavigate(Dest.Network) }
                    DeviceLink("SELinux Monitor", "Status enforcement dan AVC", KynoxIcons.Root) { onNavigate(Dest.Selinux) }
                    DeviceLink("Kynox Snapshot", "Simpan kondisi sistem saat ini", KynoxIcons.Device) { onNavigate(Dest.Snapshot) }
                    DeviceLink("Command Console", "Jalankan command melalui root", KynoxIcons.Root) { onNavigate(Dest.CommandConsole) }
                    DeviceLink("Kynox Diagnostics", "Pemeriksaan kesehatan sistem", KynoxIcons.Info) { onNavigate(Dest.Diagnostics) }
                }
            }
            item {
                SectionCard("Kontrol & sistem") {
                    DeviceLink("Kontrol Sistem", "Baterai dan fungsi sistem Kynox", KynoxIcons.Control) { onNavigate(Dest.Control) }
                    DeviceLink("Profil Performa", "Seimbang, intensif, hemat daya", KynoxIcons.Profiles) { onNavigate(Dest.Profiles) }
                    DeviceLink("Manajer CPU", "Core, frekuensi, governor", KynoxIcons.Cpu) { onNavigate(Dest.Cpu) }
                    DeviceLink("Manajer GPU", "Frekuensi dan telemetry", KynoxIcons.Gpu) { onNavigate(Dest.Gpu) }
                    DeviceLink("Manajer Root", "Status akses dan komponen root", KynoxIcons.Root) { onNavigate(Dest.RootManager) }
                    DeviceLink("Debloat", "Kelola aplikasi sistem", KynoxIcons.Debloat) { onNavigate(Dest.Debloat) }
                    DeviceLink("Log", "Riwayat perubahan sistem", KynoxIcons.Logs) { onNavigate(Dest.Logs) }
                }
            }
        }
    }
}

@Composable
private fun DeviceIdentityCard(model: String, android: String, resolution: String) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .75f), RoundedCornerShape(22.dp)).padding(17.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(KynoxIcons.Device, null, tint = KynoxAccentDark, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.size(13.dp))
        Column(Modifier.weight(1f)) {
            Text(model, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("$android  ·  $resolution", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DeviceTile(title: String, value: String, icon: ImageVector, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface).border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .7f), RoundedCornerShape(18.dp)).padding(13.dp)) {
        Icon(icon, null, tint = KynoxAccentDark, modifier = Modifier.size(19.dp))
        Spacer(Modifier.height(8.dp))
        Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun DeviceLink(title: String, description: String, icon: ImageVector, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).clickable(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(MaterialTheme.kynoxColors.surfaceSunken), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.size(11.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(KynoxIcons.Chevron, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(19.dp))
    }
}
