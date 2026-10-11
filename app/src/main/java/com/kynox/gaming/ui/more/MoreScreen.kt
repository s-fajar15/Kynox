package com.kynox.gaming.ui.more

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kynox.gaming.AppContainer
import com.kynox.gaming.R
import com.kynox.gaming.core.root.RootProvider
import com.kynox.gaming.domain.model.ProfileType
import com.kynox.gaming.ui.components.GenericViewModelFactory
import com.kynox.gaming.ui.components.GroupLabel
import com.kynox.gaming.ui.components.KynoxCardSpacing
import com.kynox.gaming.ui.components.KynoxListPadding
import com.kynox.gaming.ui.components.KynoxNavRow
import com.kynox.gaming.ui.components.KynoxTopBar
import com.kynox.gaming.ui.components.ListCard
import com.kynox.gaming.ui.navigation.Dest
import com.kynox.gaming.ui.theme.KynoxIcons
import com.kynox.gaming.ui.theme.KynoxStatusBands
import com.kynox.gaming.ui.theme.StatusDanger
import com.kynox.gaming.ui.theme.StatusGood
import com.kynox.gaming.ui.theme.StatusWarning

private class MoreItem(
    val title: String,
    val icon: ImageVector,
    val dest: Dest,
    val value: String? = null,
    val valueColor: Color = Color.Unspecified
)

/**
 * Tab Lainnya: SEMUA fitur, dikelompokkan. Setiap baris = ikon tile + judul + nilai/status singkat (teks berwarna,
 * tanpa pil) + chevron. Nilai hanya dibaca dari perangkat; yang belum terbaca dikosongkan atau "--".
 */
@Composable
fun MoreScreen(container: AppContainer, onNavigate: (Dest) -> Unit) {
    val vm: MoreViewModel = viewModel(factory = GenericViewModelFactory {
        MoreViewModel(
            container.batteryRepository,
            container.cpuRepository,
            container.gpuRepository,
            container.thermalRepository,
            container.profileRepository,
            container.hostsRepository,
            container.rootRepository,
            container.gamingModeRepository
        )
    })
    val state by vm.state.collectAsState()

    val profileLabel = when (state.profile) {
        ProfileType.BALANCED -> stringResource(R.string.profile_balanced_label)
        ProfileType.PERFORMANCE -> stringResource(R.string.profile_performance_label)
        ProfileType.GAMING -> stringResource(R.string.profile_gaming_label)
        ProfileType.POWERSAVE -> stringResource(R.string.profile_powersave_label)
        ProfileType.CUSTOM -> stringResource(R.string.profile_custom_label)
        null -> if (state.profileLoaded) "Belum ada" else null
    }

    val temp = state.cpuTempCelsius
    val thermalValue = when {
        temp == null -> null
        temp >= KynoxStatusBands.TEMP_CRIT -> "Panas"
        temp >= KynoxStatusBands.TEMP_WARN -> "Hangat"
        else -> "Normal"
    }
    val thermalColor = when {
        temp == null -> Color.Unspecified
        temp >= KynoxStatusBands.TEMP_CRIT -> StatusDanger
        temp >= KynoxStatusBands.TEMP_WARN -> StatusWarning
        else -> StatusGood
    }

    val hosts = state.hosts
    val hostsValue: String?
    val hostsColor: Color
    when {
        hosts == null -> { hostsValue = null; hostsColor = Color.Unspecified }
        !hosts.rootAvailable -> { hostsValue = "Perlu root"; hostsColor = StatusWarning }
        hosts.markedForRemoval -> { hostsValue = "Dihapus saat reboot"; hostsColor = StatusWarning }
        hosts.needsReboot -> { hostsValue = "Perlu reboot"; hostsColor = StatusWarning }
        hosts.installed && hosts.mounted -> { hostsValue = "Aktif"; hostsColor = StatusGood }
        hosts.installed -> { hostsValue = "Terpasang"; hostsColor = Color.Unspecified }
        else -> { hostsValue = "Belum dipasang"; hostsColor = Color.Unspecified }
    }

    val root = state.root
    val rootValue: String?
    val rootColor: Color
    when {
        root == null -> { rootValue = null; rootColor = Color.Unspecified }
        !root.isAvailable -> { rootValue = "Tidak ada"; rootColor = StatusWarning }
        else -> {
            rootValue = when (root.provider) {
                RootProvider.MAGISK -> "Magisk"
                RootProvider.KERNELSU -> "KernelSU"
                RootProvider.APATCH -> "APatch"
                else -> "Aktif"
            }
            rootColor = Color.Unspecified
        }
    }

    val modeActive = state.performanceModeActive

    val groups: List<Pair<String, List<MoreItem>>> = listOf(
        "Performa" to listOf(
            MoreItem("Profil performa", KynoxIcons.Profiles, Dest.Profiles, profileLabel),
            MoreItem("CPU", KynoxIcons.Cpu, Dest.Cpu, state.cpuGovernor),
            MoreItem(
                "GPU", KynoxIcons.Gpu, Dest.Gpu,
                state.gpuGovernor ?: if (state.gpuSupported == false) "Tidak didukung" else null
            ),
            MoreItem("Thermal", KynoxIcons.Thermal, Dest.Thermal, thermalValue, thermalColor),
            MoreItem("Refresh rate per aplikasi", KynoxIcons.RefreshRate, Dest.RefreshRate)
        ),
        "Daya" to listOf(
            MoreItem("Baterai dan pengisian", KynoxIcons.Charging, Dest.Battery, state.batteryPercent?.let { "$it%" })
        ),
        "Sistem" to listOf(
            MoreItem("Blokir iklan (hosts)", KynoxIcons.Shield, Dest.AdBlock, hostsValue, hostsColor),
            MoreItem("Debloat", KynoxIcons.Trash, Dest.Debloat),
            MoreItem("Bersihkan RAM dan cache", KynoxIcons.Cleaner, Dest.Cleaner),
            MoreItem("Panel cepat", KynoxIcons.Layers, Dest.QuickPanel),
            MoreItem("Proses", KynoxIcons.Processes, Dest.Processes),
            MoreItem("Root", KynoxIcons.Root, Dest.RootManager, rootValue, rootColor),
            MoreItem("Kontrol sistem", KynoxIcons.Control, Dest.Control)
        ),
        "Mode performa dan rekam sesi" to listOf(
            MoreItem(
                "Mode performa", KynoxIcons.Display, Dest.Gaming,
                when (modeActive) { true -> "Aktif"; false -> "Nonaktif"; null -> null },
                if (modeActive == true) StatusGood else Color.Unspecified
            ),
            MoreItem("Deteksi otomatis", KynoxIcons.Search, Dest.Gaming),
            MoreItem("Overlay performa", KynoxIcons.Layers, Dest.Gaming),
            MoreItem("Rekam sesi", KynoxIcons.Session, Dest.Session),
            MoreItem("Laporan dan riwayat sesi", KynoxIcons.Report, Dest.SessionHistory),
            MoreItem("Pustaka game", KynoxIcons.Library, Dest.GameLibrary)
        ),
        "Alat" to listOf(
            MoreItem("Automation rules", KynoxIcons.Profiles, Dest.Automation),
            MoreItem("Network monitor", KynoxIcons.Network, Dest.Network),
            MoreItem("SELinux monitor", KynoxIcons.Security, Dest.Selinux),
            MoreItem("Snapshot", KynoxIcons.Snapshot, Dest.Snapshot),
            MoreItem("Session compare", KynoxIcons.Compare, Dest.SessionCompare),
            MoreItem("Command console", KynoxIcons.Root, Dest.CommandConsole),
            MoreItem("Diagnostics", KynoxIcons.CheckOk, Dest.Diagnostics)
        ),
        "Aplikasi" to listOf(
            MoreItem("Log", KynoxIcons.Logs, Dest.Logs),
            MoreItem("Cadangan dan pulihkan", KynoxIcons.Backup, Dest.Backup),
            MoreItem("Pengaturan", KynoxIcons.Settings, Dest.Settings),
            MoreItem("Tentang", KynoxIcons.About, Dest.About)
        )
    )

    Scaffold(topBar = { KynoxTopBar(title = stringResource(R.string.nav_more)) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = KynoxListPadding,
            verticalArrangement = KynoxCardSpacing
        ) {
            groups.forEach { (title, items) ->
                item(key = "group_$title", contentType = "group") {
                    Column {
                        GroupLabel(title)
                        ListCard {
                            items.forEach { item ->
                                KynoxNavRow(
                                    title = item.title,
                                    icon = item.icon,
                                    value = item.value,
                                    valueColor = item.valueColor,
                                    onClick = { onNavigate(item.dest) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
