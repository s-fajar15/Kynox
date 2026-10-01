package com.kynox.gaming.ui.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kynox.gaming.AppContainer
import com.kynox.gaming.R
import com.kynox.gaming.data.settings.ThemeMode
import com.kynox.gaming.service.ChargingMonitorService
import com.kynox.gaming.service.MonitorService
import com.kynox.gaming.ui.components.GenericViewModelFactory
import com.kynox.gaming.ui.components.KButton
import com.kynox.gaming.ui.components.KynoxTopBar
import com.kynox.gaming.ui.theme.KynoxAccentDark
import com.kynox.gaming.ui.theme.KynoxIcons
import com.kynox.gaming.ui.theme.StatusGood
import com.kynox.gaming.ui.theme.kynoxColors

@Composable
fun SettingsScreen(
    container: AppContainer,
    onNavigateAbout: () -> Unit = {},
    onNavigateGaming: () -> Unit = {},
    onNavigateGameLibrary: () -> Unit = {},
    onNavigateControl: () -> Unit = {}
) {
    val viewModel: SettingsViewModel = viewModel(factory = GenericViewModelFactory { SettingsViewModel(container.settingsRepository) })
    val settings by viewModel.settings.collectAsState()
    val context = LocalContext.current
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    var search by remember { mutableStateOf("") }

    Scaffold(topBar = { KynoxTopBar(title = "Pengaturan") }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 74.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SearchBar(search, onSearch = { search = it })
            }
            if (settings.safeModeActive) {
                item { CompactInfo("Mode aman aktif", "Perubahan boot otomatis ditahan sementara.") { viewModel.exitSafeMode() } }
            }
            item {
                SettingGroup(
                    title = "Tampilan",
                    icon = KynoxIcons.Display,
                    visible = matches(search, "tampilan tema refresh rate"),
                    content = {
                        val label = when (settings.themeMode) {
                            ThemeMode.DARK -> "Gelap"
                            ThemeMode.LIGHT -> "Terang"
                            ThemeMode.SYSTEM -> "Sistem"
                        }
                        SimpleSettingRow("Tema", "Mode $label", KynoxIcons.Display) {
                            val next = when (settings.themeMode) {
                                ThemeMode.DARK -> ThemeMode.LIGHT
                                ThemeMode.LIGHT -> ThemeMode.SYSTEM
                                ThemeMode.SYSTEM -> ThemeMode.DARK
                            }
                            viewModel.setThemeMode(next)
                        }
                    }
                )
            }
            item {
                SettingGroup(
                    title = "Pemantauan",
                    icon = KynoxIcons.Monitor,
                    visible = matches(search, "monitor pemantauan interval"),
                    content = {
                        ToggleSettingRow("Monitor selalu aktif", "Mencatat data di latar belakang", settings.monitorEnabled, KynoxIcons.Monitor) { enabled ->
                            viewModel.setMonitorEnabled(enabled)
                            if (enabled) {
                                if (Build.VERSION.SDK_INT >= 33) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                MonitorService.start(context)
                            } else MonitorService.stop(context)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Interval refresh: ${settings.refreshIntervalMs} ms", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            listOf(1000L, 1500L, 2000L).forEach { ms ->
                                val active = ms == settings.refreshIntervalMs
                                Box(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.kynoxColors.surfaceSunken).clickable { viewModel.setRefreshInterval(ms) }.padding(vertical = 9.dp), contentAlignment = Alignment.Center) {
                                    Text("${ms}ms", style = MaterialTheme.typography.labelMedium, color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                )
            }
            item {
                SettingGroup("Keamanan & Privasi", KynoxIcons.Root, matches(search, "keamanan privasi root logging")) {
                    ToggleSettingRow("Wajib konfirmasi", "Konfirmasi sebelum perubahan sistem", settings.requireConfirmation, KynoxIcons.Root, viewModel::setRequireConfirmation)
                    ToggleSettingRow("Logging", "Simpan riwayat perubahan", settings.loggingEnabled, KynoxIcons.Logs, viewModel::setLoggingEnabled)
                    ToggleSettingRow("Terapkan saat boot", "Pulihkan konfigurasi setelah restart", settings.applyOnBoot, KynoxIcons.Refresh, viewModel::setApplyOnBoot)
                }
            }
            item {
                SettingGroup("Notifikasi", KynoxIcons.Notifications, matches(search, "notifikasi baterai pengisian")) {
                    ToggleSettingRow("Notifikasi pengisian", "Peringatan saat pengisian berlangsung", settings.chargingNotification, KynoxIcons.Battery) { enabled ->
                        viewModel.setChargingNotification(enabled)
                        if (enabled) {
                            if (Build.VERSION.SDK_INT >= 33) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            ChargingMonitorService.enable(context)
                        } else {
                            ChargingMonitorService.stopAndDisarm(context)
                        }
                    }
                    if (settings.chargingNotification) {
                        Spacer(Modifier.height(6.dp))
                        Text("Notifikasi aktif", style = MaterialTheme.typography.labelSmall, color = StatusGood)
                    }
                }
            }
            item {
                SettingGroup("Overlay", KynoxIcons.Profiles, matches(search, "overlay fps cpu gpu suhu")) {
                    ToggleSettingRow("FPS", "Tampilkan FPS di atas aplikasi", settings.overlayShowFps, KynoxIcons.Monitor) { viewModel.setOverlayMetric(it, settings.overlayShowCpu, settings.overlayShowGpu, settings.overlayShowBatteryTemp) }
                    ToggleSettingRow("CPU", "Tampilkan penggunaan CPU", settings.overlayShowCpu, KynoxIcons.Cpu) { viewModel.setOverlayMetric(settings.overlayShowFps, it, settings.overlayShowGpu, settings.overlayShowBatteryTemp) }
                    ToggleSettingRow("GPU", "Tampilkan penggunaan GPU", settings.overlayShowGpu, KynoxIcons.Gpu) { viewModel.setOverlayMetric(settings.overlayShowFps, settings.overlayShowCpu, it, settings.overlayShowBatteryTemp) }
                    ToggleSettingRow("Suhu baterai", "Tampilkan suhu baterai", settings.overlayShowBatteryTemp, KynoxIcons.Thermal) { viewModel.setOverlayMetric(settings.overlayShowFps, settings.overlayShowCpu, settings.overlayShowGpu, it) }
                }
            }
            item {
                SettingGroup("Akses Kynox", KynoxIcons.Info, matches(search, "akses izin notifikasi overlay")) {
                    PermissionRow("Notifikasi", Build.VERSION.SDK_INT < 33 || androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        if (Build.VERSION.SDK_INT >= 33) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    PermissionRow("Tampil di atas aplikasi", Settings.canDrawOverlays(context)) {
                        context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
                    }
                }
            }
            item {
                SettingGroup(
                    "Fitur sistem",
                    KynoxIcons.Control,
                    matches(search, "kontrol sistem game mode gaming pustaka aplikasi game"),
                ) {
                    SimpleSettingRow(
                        "Kontrol sistem",
                        "Kontrol baterai dan fungsi sistem Kynox",
                        KynoxIcons.Control,
                        onNavigateControl
                    )
                    SimpleSettingRow(
                        "Mode Game",
                        "Kelola profil performa dan deteksi otomatis",
                        KynoxIcons.Profiles,
                        onNavigateGaming
                    )
                    SimpleSettingRow(
                        "Pustaka aplikasi",
                        "Kelola aplikasi yang dipantau Kynox",
                        KynoxIcons.Apps,
                        onNavigateGameLibrary
                    )
                }
            }
            item {
                SettingGroup("Tentang Kynox", KynoxIcons.Info, matches(search, "tentang kynox tentang aplikasi versi pengembang")) {
                    SimpleSettingRow("Tentang aplikasi", "Versi, pengembang, status root, dan informasi Kynox", KynoxIcons.Info, onNavigateAbout)
                }
            }
            item {
                SettingGroup("Reset", KynoxIcons.Refresh, matches(search, "reset pengaturan")) {
                    Text("Kembalikan konfigurasi Kynox ke nilai awal.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    KButton(onClick = { viewModel.resetAll(); ChargingMonitorService.stopAndDisarm(context) }) { Text("Reset pengaturan") }
                }
            }
        }
    }
}

private fun matches(query: String, text: String): Boolean = query.isBlank() || text.contains(query.trim(), ignoreCase = true)

@Composable
private fun SearchBar(value: String, onSearch: (String) -> Unit) {
    androidx.compose.material3.OutlinedTextField(
        value = value,
        onValueChange = onSearch,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        leadingIcon = { androidx.compose.material3.Icon(KynoxIcons.Search, null, modifier = Modifier.size(19.dp)) },
        placeholder = { Text("Cari pengaturan…") },
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
private fun SettingGroup(title: String, icon: ImageVector, visible: Boolean, content: @Composable () -> Unit) {
    if (!visible) return
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = KynoxAccentDark, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(7.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface).border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .7f), RoundedCornerShape(20.dp)).padding(horizontal = 14.dp, vertical = 7.dp)) {
            content()
        }
    }
}

@Composable
private fun SimpleSettingRow(title: String, description: String, icon: ImageVector, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.bodyMedium); Text(description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Text("›", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ToggleSettingRow(title: String, description: String, checked: Boolean, icon: ImageVector, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.bodyMedium); Text(description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun PermissionRow(title: String, granted: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(enabled = !granted, onClick = onClick).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.bodyMedium); Text(if (granted) "Akses diberikan" else "Akses belum diberikan", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Text(if (granted) "Aktif" else "Izinkan", style = MaterialTheme.typography.labelMedium, color = if (granted) StatusGood else MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun CompactInfo(title: String, description: String, onAction: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.primaryContainer).padding(14.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 3.dp, bottom = 8.dp))
        KButton(onClick = onAction) { Text("Keluar dari mode aman") }
    }
}
