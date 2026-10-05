package com.kynox.gaming.ui.settings

import com.kynox.gaming.ui.components.rowDivider
import com.kynox.gaming.ui.components.KynoxListRow
import com.kynox.gaming.ui.components.ListCard
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
import com.kynox.gaming.data.settings.OVERLAY_SCALE_MIN
import com.kynox.gaming.data.settings.OVERLAY_SCALE_MAX
import com.kynox.gaming.data.settings.OVERLAY_OPACITY_MIN
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.material3.Slider
import com.kynox.gaming.data.settings.THERMAL_WARN_OPTIONS
import com.kynox.gaming.data.settings.THERMAL_WARN_AUTO
import com.kynox.gaming.data.settings.NOTIFY_COOLDOWN_OPTIONS_MIN
import com.kynox.gaming.data.settings.HISTORY_RETENTION_OPTIONS_HOURS
import com.kynox.gaming.data.settings.HISTORY_INTERVAL_OPTIONS_SEC
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.heightIn

@Composable
fun SettingsScreen(
    container: AppContainer,
    onNavigateAbout: () -> Unit = {},
    onNavigateGaming: () -> Unit = {},
    onNavigateGameLibrary: () -> Unit = {},
    onNavigateControl: () -> Unit = {},
    onNavigateQuickPanel: () -> Unit = {},
    onNavigateBackup: () -> Unit = {}
) {
    val viewModel: SettingsViewModel = viewModel(factory = GenericViewModelFactory { SettingsViewModel(container.settingsRepository) })
    val settings by viewModel.settings.collectAsState()
    val context = LocalContext.current
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    var search by remember { mutableStateOf("") }

    Scaffold(topBar = { KynoxTopBar(title = "Pengaturan") }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
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
                        Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 14.dp)) {
                        Text("Interval refresh: ${settings.refreshIntervalMs} ms", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        ChoiceRow(
                            options = listOf(1000L, 1500L, 2000L).map { it to "${it}ms" },
                            selected = settings.refreshIntervalMs,
                            onSelect = viewModel::setRefreshInterval
                        )
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
                SettingGroup("Notifikasi", KynoxIcons.Notifications, matches(search, "notifikasi baterai pengisian suhu peringatan profil jeda")) {
                    ToggleSettingRow("Peringatan suhu", "Beri tahu saat suhu melewati ambang", settings.notifyThermal, KynoxIcons.Thermal) { enabled ->
                        viewModel.setNotifications(enabled, settings.thermalWarnThresholdC, settings.notifyCooldownMin, settings.notifyProfileApplied)
                        if (enabled && Build.VERSION.SDK_INT >= 33) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    if (settings.notifyThermal) {
                        Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 2.dp, bottom = 10.dp)) {
                            Text("Ambang suhu", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            ChoiceRow(
                                options = THERMAL_WARN_OPTIONS.map { it to (if (it == THERMAL_WARN_AUTO) "Auto" else "$it°C") },
                                selected = settings.thermalWarnThresholdC,
                                onSelect = { viewModel.setNotifications(true, it, settings.notifyCooldownMin, settings.notifyProfileApplied) }
                            )
                            Text(
                                if (settings.thermalWarnThresholdC == THERMAL_WARN_AUTO)
                                    "Otomatis: 5°C di bawah titik trip tiap sensor. Berjalan saat Mode Game otomatis atau Automation aktif."
                                else "Berlaku untuk suhu CPU dan baterai selama monitor aktif.",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                    }
                    ToggleSettingRow("Profil diterapkan / dikembalikan", "Saat game atau aturan Automation mengubah profil", settings.notifyProfileApplied, KynoxIcons.Profiles) { enabled ->
                        viewModel.setNotifications(settings.notifyThermal, settings.thermalWarnThresholdC, settings.notifyCooldownMin, enabled)
                    }
                    Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 2.dp, bottom = 12.dp)) {
                        Text("Jeda antar notifikasi sejenis", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        ChoiceRow(
                            options = NOTIFY_COOLDOWN_OPTIONS_MIN.map { it to "$it mnt" },
                            selected = settings.notifyCooldownMin,
                            onSelect = { viewModel.setNotifications(settings.notifyThermal, settings.thermalWarnThresholdC, it, settings.notifyProfileApplied) }
                        )
                    }
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
                        Text("Notifikasi aktif", style = MaterialTheme.typography.labelSmall, color = StatusGood, modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp, top = 4.dp))
                    }
                }
            }
            item {
                SettingGroup("Riwayat monitor", KynoxIcons.Monitor, matches(search, "riwayat monitor simpan penyimpanan sampel baterai")) {
                    ToggleSettingRow("Simpan riwayat", "Simpan sampel ke penyimpanan lokal agar tetap ada setelah aplikasi ditutup", settings.historyEnabled, KynoxIcons.Logs) { enabled ->
                        viewModel.setHistory(enabled, settings.historyIntervalSec, settings.historyRetentionHours)
                    }
                    if (settings.historyEnabled) {
                        Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 2.dp, bottom = 12.dp)) {
                            Text("Selang simpan (makin jarang makin hemat baterai)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            ChoiceRow(
                                options = HISTORY_INTERVAL_OPTIONS_SEC.map { it to (if (it >= 60) "${it / 60} mnt" else "$it dtk") },
                                selected = settings.historyIntervalSec,
                                onSelect = { viewModel.setHistory(true, it, settings.historyRetentionHours) }
                            )
                            Spacer(Modifier.height(10.dp))
                            Text("Lama disimpan (maks. 3 MB, yang tertua dibuang lebih dulu)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            ChoiceRow(
                                options = HISTORY_RETENTION_OPTIONS_HOURS.map { it to "$it jam" },
                                selected = settings.historyRetentionHours,
                                onSelect = { viewModel.setHistory(true, settings.historyIntervalSec, it) }
                            )
                        }
                    }
                }
            }
            item {
                SettingGroup("Cadangan", KynoxIcons.Refresh, matches(search, "cadangan backup pulihkan restore ekspor impor")) {
                    SimpleSettingRow("Cadangan & Pulihkan", "Simpan atau pulihkan pengaturan, aturan, dan profil game", KynoxIcons.Refresh, onNavigateBackup)
                }
            }
            item {
                SettingGroup("Overlay", KynoxIcons.Profiles, matches(search, "overlay fps cpu gpu suhu")) {
                    ToggleSettingRow("FPS", "Tampilkan FPS di atas aplikasi", settings.overlayShowFps, KynoxIcons.Monitor) { viewModel.setOverlayMetric(it, settings.overlayShowCpu, settings.overlayShowGpu, settings.overlayShowBatteryTemp) }
                    ToggleSettingRow("CPU", "Tampilkan penggunaan CPU", settings.overlayShowCpu, KynoxIcons.Cpu) { viewModel.setOverlayMetric(settings.overlayShowFps, it, settings.overlayShowGpu, settings.overlayShowBatteryTemp) }
                    ToggleSettingRow("GPU", "Tampilkan penggunaan GPU", settings.overlayShowGpu, KynoxIcons.Gpu) { viewModel.setOverlayMetric(settings.overlayShowFps, settings.overlayShowCpu, it, settings.overlayShowBatteryTemp) }
                    ToggleSettingRow("Suhu baterai", "Tampilkan suhu baterai", settings.overlayShowBatteryTemp, KynoxIcons.Thermal) { viewModel.setOverlayMetric(settings.overlayShowFps, settings.overlayShowCpu, settings.overlayShowGpu, it) }
                    var scaleDraft by remember(settings.overlayScalePercent) { mutableFloatStateOf(settings.overlayScalePercent.toFloat()) }
                    var opacityDraft by remember(settings.overlayOpacityPercent) { mutableFloatStateOf(settings.overlayOpacityPercent.toFloat()) }
                    Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 12.dp)) {
                        Text("Ukuran overlay: ${scaleDraft.toInt()}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Slider(
                            value = scaleDraft,
                            onValueChange = { scaleDraft = it },
                            onValueChangeFinished = { viewModel.setOverlayStyle(scaleDraft.toInt(), settings.overlayOpacityPercent) },
                            valueRange = OVERLAY_SCALE_MIN.toFloat()..OVERLAY_SCALE_MAX.toFloat(),
                            steps = 13
                        )
                        Text("Opasitas panel: ${opacityDraft.toInt()}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Slider(
                            value = opacityDraft,
                            onValueChange = { opacityDraft = it },
                            onValueChangeFinished = { viewModel.setOverlayStyle(settings.overlayScalePercent, opacityDraft.toInt()) },
                            valueRange = OVERLAY_OPACITY_MIN.toFloat()..100f,
                            steps = 7
                        )
                        Text("Di overlay, ketuk untuk memunculkan tombol \u2212 / + (overlay sesi) atau pakai tombol \u2212 / + di kepalanya (overlay cepat).", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    SimpleSettingRow("Reset posisi overlay", "Kembalikan overlay ke pojok kiri-atas (potret dan landscape) saat ditampilkan berikutnya", KynoxIcons.Refresh) {
                        com.kynox.gaming.service.resetOverlayPositions(context)
                        android.widget.Toast.makeText(context, "Posisi overlay direset", android.widget.Toast.LENGTH_SHORT).show()
                    }
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
                        "Panel Cepat",
                        "Akses cepat ke fitur penting",
                        KynoxIcons.Display,
                        onNavigateQuickPanel
                    )
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
                    Column(Modifier.padding(14.dp)) {
                        Text("Kembalikan konfigurasi Kynox ke nilai awal.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        KButton(onClick = { viewModel.resetAll(); ChargingMonitorService.stopAndDisarm(context) }) { Text("Reset pengaturan") }
                    }
                }
            }
        }
    }
}

/** Baris pilihan bergaya chip. Tinggi minimum 48dp supaya mudah disentuh. */
@Composable
private fun <T> ChoiceRow(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        options.forEach { (value, label) ->
            val active = value == selected
            Box(
                Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.kynoxColors.surfaceSunken)
                    .selectable(selected = active, role = Role.RadioButton, onClick = { onSelect(value) }),
                contentAlignment = Alignment.Center
            ) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
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
        leadingIcon = { androidx.compose.material3.Icon(KynoxIcons.Search, null, modifier = Modifier.size(20.dp)) },
        placeholder = { Text("Cari pengaturan…") },
        shape = RoundedCornerShape(18.dp),
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline
        )
    )
}

@Composable
private fun SettingGroup(title: String, icon: ImageVector, visible: Boolean, content: @Composable () -> Unit) {
    if (!visible) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        ListCard { content() }
    }
}

@Composable
private fun SimpleSettingRow(title: String, description: String, icon: ImageVector, onClick: () -> Unit) {
    KynoxListRow(title, description, icon, onClick = onClick)
}

@Composable
private fun ToggleSettingRow(title: String, description: String, checked: Boolean, icon: ImageVector, onChange: (Boolean) -> Unit) {
    KynoxListRow(title, description, icon, trailing = { Switch(checked = checked, onCheckedChange = onChange) })
}

@Composable
private fun PermissionRow(title: String, granted: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(enabled = !granted, onClick = onClick).rowDivider().padding(horizontal = 14.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
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
