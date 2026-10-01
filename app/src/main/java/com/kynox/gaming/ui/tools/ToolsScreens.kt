package com.kynox.gaming.ui.tools

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.core.content.ContextCompat
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kynox.gaming.AppContainer
import com.kynox.gaming.core.utils.AppResult
import com.kynox.gaming.data.automation.AutomationRule
import com.kynox.gaming.data.network.NetworkSnapshot
import com.kynox.gaming.domain.model.ProfileType
import com.kynox.gaming.ui.components.DetailTopBar
import com.kynox.gaming.ui.components.GenericViewModelFactory
import com.kynox.gaming.ui.theme.KynoxAccentDark
import com.kynox.gaming.ui.theme.KynoxIcons
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.UUID
import kotlin.math.roundToInt

@Composable
private fun ToolScaffold(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    androidx.compose.material3.Scaffold(topBar = { DetailTopBar(title, onBack) }) { p ->
        Column(Modifier.fillMaxSize().padding(p).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
    }
}

@Composable
private fun ToolCard(title: String, subtitle: String? = null, icon: androidx.compose.ui.graphics.vector.ImageVector = KynoxIcons.Info, content: @Composable () -> Unit) {
    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = KynoxAccentDark, modifier = Modifier.size(19.dp)) }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.SemiBold); subtitle?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            }
            content()
        }
    }
}

@Composable
fun AutomationScreen(container: AppContainer, onBack: () -> Unit) {
    var enabled by remember { mutableStateOf(false) }
    var rules by remember { mutableStateOf(emptyList<AutomationRule>()) }
    var pkg by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    var hz by remember { mutableStateOf("120") }
    var profile by remember { mutableStateOf("GAMING") }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { enabled = container.automationRepository.isEnabled(); rules = container.automationRepository.list() }
    ToolScaffold("Automation Rules", onBack) {
        ToolCard("Automation Engine", "Jalankan aturan saat aplikasi tertentu menjadi foreground", KynoxIcons.Profiles) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(if (enabled) "Aktif" else "Tidak aktif", fontWeight = FontWeight.Medium); Text("Memantau foreground melalui service Kynox", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Switch(enabled, onCheckedChange = { value ->
                    enabled = value
                    scope.launch(Dispatchers.IO) { container.automationRepository.setEnabled(value) }
                    if (value) ContextCompat.startForegroundService(context, Intent(context, com.kynox.gaming.service.GameDetectionService::class.java).setAction(com.kynox.gaming.service.GameDetectionService.ACTION_START))
                })
            }
        }
        ToolCard("Aturan baru", "Contoh: Free Fire → 120 Hz + Gaming", KynoxIcons.Apps) {
            OutlinedTextField(pkg, { pkg = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Package aplikasi") }, placeholder = { Text("com.example.app") })
            OutlinedTextField(label, { label = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Nama aplikasi") })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(hz, { hz = it.filter(Char::isDigit) }, Modifier.weight(1f), singleLine = true, label = { Text("Refresh Hz") })
                OutlinedTextField(profile, { profile = it.uppercase() }, Modifier.weight(1f), singleLine = true, label = { Text("Profile") })
            }
            Button(enabled = pkg.isNotBlank(), onClick = {
                val rule = AutomationRule(UUID.randomUUID().toString().take(8), pkg.trim(), label.ifBlank { pkg.trim() }, true, hz.toIntOrNull(), profile.takeIf { it.isNotBlank() })
                scope.launch(Dispatchers.IO) { container.automationRepository.upsert(rule); rules = container.automationRepository.list() }
                pkg = ""; label = ""
            }, modifier = Modifier.fillMaxWidth()) { Text("Simpan aturan") }
        }
        rules.forEach { rule ->
            ToolCard(rule.label, rule.packageName, KynoxIcons.RefreshRate) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("${rule.refreshRateHz ?: 60} Hz  ·  ${rule.profile ?: "tanpa profile"}", style = MaterialTheme.typography.bodySmall) }
                    Text("Hapus", color = MaterialTheme.colorScheme.error, modifier = Modifier.clickable { scope.launch(Dispatchers.IO) { container.automationRepository.delete(rule.id); rules = container.automationRepository.list() } }.padding(6.dp))
                }
            }
        }
        Text("Automation tidak mengubah thermal safety dan hanya menjalankan rule yang kamu buat.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun NetworkMonitorScreen(container: AppContainer, onBack: () -> Unit) {
    var sample by remember { mutableStateOf<NetworkSnapshot?>(null) }
    var history by remember { mutableStateOf(emptyList<NetworkSnapshot>()) }
    LaunchedEffect(Unit) {
        while (true) { sample = container.networkRepository.sample(); history = container.networkRepository.history(); delay(2000) }
    }
    ToolScaffold("Network Monitor", onBack) {
        ToolCard("Koneksi aktif", sample?.transport ?: container.networkRepository.currentTransport(), KynoxIcons.Monitor) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Metric("Download", formatRate(sample?.downBps ?: 0), Modifier.weight(1f))
                Metric("Upload", formatRate(sample?.upBps ?: 0), Modifier.weight(1f))
            }
            Text("Total ↓ ${formatBytes(sample?.rxBytes ?: 0)}   ·   ↑ ${formatBytes(sample?.txBytes ?: 0)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        ToolCard("Network Activity History", "Sampel terakhir disimpan lokal", KynoxIcons.Logs) {
            history.takeLast(18).reversed().forEach { h ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(h.timestamp)), style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(62.dp))
                    Text("↓ ${formatRate(h.downBps)}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Text("↑ ${formatRate(h.upBps)}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Text(h.transport, style = MaterialTheme.typography.labelSmall)
                }
            }
            if (history.isEmpty()) Text("Belum ada data.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier) {
    Column(modifier.background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(15.dp)).padding(13.dp)) { Text(label, style = MaterialTheme.typography.labelSmall); Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
}

private fun formatRate(bps: Long): String = when { bps >= 1_000_000 -> "%.1f MB/s".format(bps / 1_000_000f); bps >= 1_000 -> "%.0f KB/s".format(bps / 1_000f); else -> "$bps B/s" }
private fun formatBytes(bytes: Long): String = when { bytes >= 1_000_000_000 -> "%.2f GB".format(bytes / 1_000_000_000f); bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000f); else -> "%.0f KB".format(bytes / 1_000f) }

@Composable
fun SelinuxMonitorScreen(container: AppContainer, onBack: () -> Unit) {
    var status by remember { mutableStateOf("Membaca…") }
    var denials by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    fun reload() { scope.launch(Dispatchers.IO) { status = container.rootExecutor.execute("getenforce").stdout.trim().ifBlank { "Tidak tersedia" }; denials = container.rootExecutor.execute("dmesg 2>/dev/null | grep -i 'avc: denied' | tail -n 40").stdout.trim() } }
    LaunchedEffect(Unit) { reload() }
    ToolScaffold("SELinux Monitor", onBack) {
        ToolCard("SELinux", "Status enforcement perangkat", KynoxIcons.Root) { Text(status, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); OutlinedButton(onClick = ::reload) { Text("Refresh") } }
        ToolCard("AVC denials", "40 event terakhir yang bisa dibaca root", KynoxIcons.Logs) { Text(if (denials.isBlank()) "Tidak ada denial terbaca." else denials, style = MaterialTheme.typography.bodySmall) }
        Text("Kynox hanya membaca status dan log SELinux; tidak mengubah enforcement policy.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun KynoxSnapshotScreen(container: AppContainer, onBack: () -> Unit) {
    var text by remember { mutableStateOf("Belum ada snapshot.") }
    var savedPath by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    suspend fun capture() {
        val d = container.deviceInfoRepository.load(); val b = container.batteryRepository.readInfo(); val c = container.cpuRepository.readSnapshot(); val g = container.gpuRepository.readState(); val t = container.thermalRepository.readZones(); val root = container.rootRepository.current()
        val json = JSONObject().apply {
            put("timestamp", System.currentTimeMillis()); put("device", JSONObject().apply { put("model", d.model); put("android", d.androidVersion); put("kernel", d.kernelVersion); put("selinux", d.selinuxStatus); put("root", root.isAvailable) }); put("display", d.display.resolution + " @ " + d.display.refreshRateHz + "Hz"); put("battery", JSONObject().apply { put("percent", b.capacityPercent ?: -1); put("temperatureC", b.temperatureCelsius); put("voltageV", b.voltageMilliVolts); put("currentMa", b.currentMicroAmps) }); put("cpuCores", c.cores.size); put("gpu", if (g.supported) "supported" else "unavailable"); put("thermalZones", t.size)
        }
        val file = File(context.filesDir, "kynox_snapshots").apply { mkdirs() }.resolve("snapshot_${System.currentTimeMillis()}.json"); file.writeText(json.toString(2)); savedPath = file.absolutePath; text = json.toString(2)
    }
    ToolScaffold("Kynox Snapshot", onBack) {
        ToolCard("Device Snapshot", "Simpan kondisi perangkat saat ini sebagai JSON", KynoxIcons.Device) { Button(onClick = { scope.launch(Dispatchers.IO) { capture() } }, modifier = Modifier.fillMaxWidth()) { Text("Ambil snapshot") } }
        ToolCard("Snapshot terbaru", savedPath ?: "Belum tersimpan", KynoxIcons.Info) { Text(text, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
fun SessionCompareScreen(container: AppContainer, onBack: () -> Unit) {
    var reports by remember { mutableStateOf(emptyList<com.kynox.gaming.domain.model.SessionReport>()) }
    var a by remember { mutableIntStateOf(0) }; var b by remember { mutableIntStateOf(1) }
    LaunchedEffect(Unit) { reports = container.gameSessionRepository.listReports() }
    ToolScaffold("Session Compare", onBack) {
        if (reports.size < 2) { ToolCard("Belum cukup sesi", "Rekam minimal dua sesi untuk membandingkan.", KynoxIcons.Session) { Text("Tersedia ${reports.size} sesi.") }; return@ToolScaffold }
        val left = reports[a.coerceIn(reports.indices)]; val right = reports[b.coerceIn(reports.indices)]
        CompareSelector("Sesi A", left.gameLabel, { a = (a + 1) % reports.size })
        CompareSelector("Sesi B", right.gameLabel, { b = (b + 1) % reports.size })
        ToolCard("Perbandingan", "Metrik utama", KynoxIcons.Monitor) {
            CompareRow("FPS rata-rata", left.avgFps?.let { "%.1f".format(it) }, right.avgFps?.let { "%.1f".format(it) })
            CompareRow("FPS minimum", left.minFps?.let { "%.1f".format(it) }, right.minFps?.let { "%.1f".format(it) })
            CompareRow("CPU temp", left.avgCpuTemp?.let { "%.1f°C".format(it) }, right.avgCpuTemp?.let { "%.1f°C".format(it) })
            CompareRow("Power", left.avgPowerWatts?.let { "%.1f W".format(it) }, right.avgPowerWatts?.let { "%.1f W".format(it) })
            CompareRow("Durasi", formatDuration(left.durationMs), formatDuration(right.durationMs))
        }
    }
}

@Composable private fun CompareSelector(title: String, value: String, onNext: () -> Unit) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(value, fontWeight = FontWeight.SemiBold) }; OutlinedButton(onClick = onNext) { Text("Ganti") } } }
@Composable private fun CompareRow(label: String, a: String?, b: String?) { Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) { Text(label, Modifier.weight(1.2f), style = MaterialTheme.typography.bodySmall); Text(a ?: "—", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall); Text(b ?: "—", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall) } }
private fun formatDuration(ms: Long): String { val s = ms / 1000; return "%02d:%02d".format(s / 60, s % 60) }

@Composable
fun CommandConsoleScreen(container: AppContainer, onBack: () -> Unit) {
    var command by remember { mutableStateOf("") }; var output by remember { mutableStateOf("$ id\nKynox root console siap.") }; var running by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    ToolScaffold("Command Console", onBack) {
        ToolCard("Root Console", "Perintah dijalankan melalui su. Gunakan hanya command yang kamu pahami.", KynoxIcons.Root) {
            OutlinedTextField(command, { command = it }, Modifier.fillMaxWidth(), minLines = 2, label = { Text("Command") }, placeholder = { Text("dumpsys display") })
            Button(enabled = command.isNotBlank() && !running, onClick = { running = true; scope.launch(Dispatchers.IO) { val r = container.rootExecutor.execute(command.trim(), 10000); output = buildString { append(r.stdout); if (r.stderr.isNotBlank()) { append("\n\n[stderr]\n"); append(r.stderr) } }; running = false } }, modifier = Modifier.fillMaxWidth()) { Text(if (running) "Menjalankan…" else "Jalankan") }
        }
        ToolCard("Output", icon = KynoxIcons.Root) { Text(output, style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth().background(Color.Black.copy(alpha = .08f), RoundedCornerShape(12.dp)).padding(12.dp)) }
    }
}

@Composable
fun DiagnosticsScreen(container: AppContainer, onBack: () -> Unit) {
    var lines by remember { mutableStateOf(listOf("Menjalankan pemeriksaan…")) }
    LaunchedEffect(Unit) {
        val root = container.rootRepository.current(); val device = container.deviceInfoRepository.load(); val zones = container.thermalRepository.readZones(); val battery = container.batteryRepository.readInfo(); val display = container.refreshRateRepository.supportedRefreshRates()
        lines = listOf(
            checkLine("Root access", root.isAvailable, root.provider.name),
            checkLine("SELinux", device.selinuxStatus.equals("Enforcing", true), device.selinuxStatus),
            checkLine("Display modes", display.isNotEmpty(), display.joinToString(", ") + " Hz"),
            checkLine("Thermal sensors", zones.isNotEmpty(), "${zones.size} zone"),
            checkLine("Battery telemetry", (battery.capacityPercent ?: -1) >= 0, "${battery.capacityPercent ?: -1}%"),
            checkLine("Android", Build.VERSION.SDK_INT >= 26, "API ${Build.VERSION.SDK_INT}"),
            checkLine("GPU telemetry", !device.gpuRenderer.contains("unknown", true), device.gpuRenderer)
        )
    }
    ToolScaffold("Kynox Diagnostics", onBack) {
        ToolCard("System check", "Pemeriksaan read-only", KynoxIcons.Info) {
            lines.forEach { line -> Text(line, style = MaterialTheme.typography.bodySmall); HorizontalDivider() }
            OutlinedButton(onClick = { lines = listOf("Tekan kembali lalu buka Diagnostics lagi untuk pemeriksaan baru.") }) { Text("Selesai") }
        }
        Text("Diagnostics tidak mengubah konfigurasi sistem.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
private fun checkLine(name: String, ok: Boolean, detail: String): String = "${if (ok) "✓" else "!"}  $name  ·  $detail"
