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
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
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
import com.kynox.gaming.ui.components.ConfirmDialog
import com.kynox.gaming.ui.components.DetailTopBar
import com.kynox.gaming.ui.components.DropdownSelector
import com.kynox.gaming.ui.components.KButton
import com.kynox.gaming.ui.components.KOutlinedButton
import com.kynox.gaming.ui.components.StatusPill
import com.kynox.gaming.ui.components.kynoxCard
import com.kynox.gaming.ui.components.GenericViewModelFactory
import com.kynox.gaming.ui.theme.KynoxAccentDark
import com.kynox.gaming.ui.theme.KynoxIcons
import com.kynox.gaming.ui.theme.StatusDanger
import com.kynox.gaming.ui.theme.StatusGood
import com.kynox.gaming.ui.theme.StatusWarning
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    Box(Modifier.fillMaxWidth().kynoxCard()) {
        Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = KynoxAccentDark, modifier = Modifier.size(19.dp)) }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.SemiBold); subtitle?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            }
            content()
        }
    }
}

private val PACKAGE_NAME_REGEX = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")

@Composable
fun AutomationScreen(container: AppContainer, onBack: () -> Unit) {
    var enabled by remember { mutableStateOf(false) }
    var rules by remember { mutableStateOf(emptyList<AutomationRule>()) }
    var supportedHz by remember { mutableStateOf(emptyList<Int>()) }
    var pkg by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    var hz by remember { mutableStateOf<Int?>(null) }
    var profile by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<AutomationRule?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        enabled = container.automationRepository.isEnabled()
        rules = container.automationRepository.list()
        supportedHz = withContext(Dispatchers.IO) { container.refreshRateRepository.supportedRefreshRates() }.distinct().sorted()
    }

    pendingDelete?.let { target ->
        ConfirmDialog(
            title = "Hapus aturan?",
            message = "Aturan untuk ${target.label} akan dihapus. Aplikasi itu tidak lagi memicu perubahan otomatis.",
            confirmLabel = "Hapus",
            onConfirm = {
                pendingDelete = null
                scope.launch(Dispatchers.IO) {
                    container.automationRepository.delete(target.id)
                    rules = container.automationRepository.list()
                }
            },
            onDismiss = { pendingDelete = null }
        )
    }

    val packageValid = PACKAGE_NAME_REGEX.matches(pkg.trim())
    val duplicate = rules.any { it.packageName == pkg.trim() }
    val hasAction = hz != null || profile != null
    val noAction = "Tidak diubah"
    val hzOptions = listOf(noAction) + supportedHz.map { "$it Hz" }
    val noProfile = "Tanpa profil"
    val profileOptions = listOf(noProfile) + ProfileType.values().map { it.name }

    ToolScaffold("Automation Rules", onBack) {
        ToolCard("Automation Engine", "Jalankan aturan saat aplikasi dibuka, dan kembalikan profil serta refresh rate sebelumnya saat aplikasi ditutup", KynoxIcons.Profiles) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    StatusPill(if (enabled) "Aktif" else "Tidak aktif", if (enabled) StatusGood else MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Text("Memantau aplikasi di depan lewat service Kynox (butuh root).", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(enabled, onCheckedChange = { value ->
                    enabled = value
                    scope.launch {
                        // Simpan dulu, baru nyalakan service, supaya service tidak melihat status lama lalu berhenti.
                        withContext(Dispatchers.IO) { container.automationRepository.setEnabled(value) }
                        if (value) ContextCompat.startForegroundService(context, Intent(context, com.kynox.gaming.service.GameDetectionService::class.java).setAction(com.kynox.gaming.service.GameDetectionService.ACTION_START))
                    }
                })
            }
        }
        ToolCard("Aturan baru", "Pilih aplikasi, lalu tentukan aksi saat aplikasi itu dibuka", KynoxIcons.Apps) {
            OutlinedTextField(
                pkg, { pkg = it.trim() }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("Package aplikasi") }, placeholder = { Text("com.example.app") },
                isError = pkg.isNotEmpty() && (!packageValid || duplicate),
                supportingText = {
                    when {
                        pkg.isNotEmpty() && !packageValid -> Text("Format package tidak valid.")
                        duplicate -> Text("Aplikasi ini sudah punya aturan. Hapus dulu aturan lamanya.")
                        else -> Unit
                    }
                }
            )
            OutlinedTextField(label, { label = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Nama aplikasi (opsional)") })
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Refresh rate", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                DropdownSelector(
                    label = "", selected = hz?.let { "$it Hz" } ?: noAction, options = hzOptions, enabled = supportedHz.isNotEmpty(),
                    onSelected = { picked -> hz = if (picked == noAction) null else picked.removeSuffix(" Hz").toIntOrNull() }
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Profil performa", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                DropdownSelector(
                    label = "", selected = profile ?: noProfile, options = profileOptions, enabled = true,
                    onSelected = { picked -> profile = if (picked == noProfile) null else picked }
                )
            }
            if (!hasAction) Text("Pilih minimal satu aksi (refresh rate atau profil).", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            KButton(enabled = packageValid && !duplicate && hasAction, onClick = {
                val rule = AutomationRule(UUID.randomUUID().toString().take(8), pkg.trim(), label.ifBlank { pkg.trim() }, true, hz, profile)
                scope.launch(Dispatchers.IO) { container.automationRepository.upsert(rule); rules = container.automationRepository.list() }
                pkg = ""; label = ""; hz = null; profile = null
            }, modifier = Modifier.fillMaxWidth()) { Text("Simpan aturan") }
        }
        if (rules.isEmpty()) {
            Text("Belum ada aturan.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        rules.forEach { rule ->
            ToolCard(rule.label, rule.packageName, KynoxIcons.RefreshRate) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Saat dibuka: " + listOfNotNull(rule.refreshRateHz?.let { "$it Hz" }, rule.profile?.let { "profil $it" }).joinToString(" + ").ifEmpty { "tanpa aksi" },
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(4.dp))
                        StatusPill(if (rule.enabled) "Aturan aktif" else "Aturan dimatikan", if (rule.enabled) StatusGood else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(rule.enabled, onCheckedChange = { value ->
                        scope.launch(Dispatchers.IO) {
                            container.automationRepository.upsert(rule.copy(enabled = value))
                            rules = container.automationRepository.list()
                        }
                    })
                }
                KOutlinedButton(onClick = { pendingDelete = rule }, modifier = Modifier.fillMaxWidth()) { Text("Hapus aturan", color = StatusDanger) }
            }
        }
        Text("Automation tidak mengubah thermal safety dan hanya menjalankan aturan yang kamu buat. Profil yang diterapkan tidak dikembalikan otomatis saat aplikasi ditutup.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun NetworkMonitorScreen(container: AppContainer, onBack: () -> Unit) {
    var sample by remember { mutableStateOf<NetworkSnapshot?>(null) }
    var history by remember { mutableStateOf(emptyList<NetworkSnapshot>()) }
    LaunchedEffect(Unit) {
        while (true) { com.kynox.gaming.core.utils.AppVisibility.awaitForeground(); sample = container.networkRepository.sample(); history = container.networkRepository.history(); delay(2000) }
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
    var status by remember { mutableStateOf<String?>(null) }
    var denials by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun reload() {
        scope.launch {
            loading = true
            try {
                val (s1, d1) = withContext(Dispatchers.IO) {
                    val st = container.rootExecutor.execute("getenforce")
                    val dn = container.rootExecutor.execute("dmesg 2>/dev/null | grep -i 'avc: denied' | tail -n 40")
                    (if (st.isSuccess) st.stdout.trim().ifBlank { null } else null) to dn.stdout.trim()
                }
                status = s1; denials = d1; failed = s1 == null
            } catch (t: Throwable) {
                status = null; denials = ""; failed = true
            }
            loading = false
        }
    }
    LaunchedEffect(Unit) { reload() }
    ToolScaffold("SELinux Monitor", onBack) {
        ToolCard("SELinux", "Status enforcement perangkat", KynoxIcons.Root) {
            val current = status
            when {
                loading -> Text("Membaca…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                current == null -> {
                    StatusPill("Tidak terbaca", StatusWarning)
                    Text(if (failed) "Status tidak bisa dibaca. Pastikan akses root diberikan, lalu coba lagi." else "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> {
                    StatusPill(current, if (current.equals("Enforcing", true)) StatusGood else StatusWarning)
                    if (current.equals("Permissive", true)) {
                        Text("Permissive: pelanggaran kebijakan hanya dicatat, tidak diblokir.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            KOutlinedButton(onClick = { reload() }, enabled = !loading) { Text(if (loading) "Memeriksa…" else "Refresh") }
        }
        ToolCard("AVC denials", "40 event terakhir yang bisa dibaca root", KynoxIcons.Logs) {
            if (loading) {
                Text("Membaca…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (denials.isBlank()) {
                Text("Tidak ada denial terbaca.", style = MaterialTheme.typography.bodySmall)
            } else {
                SelectionContainer {
                    Text(denials.take(MAX_CONSOLE_CHARS), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                }
            }
        }
        Text("Kynox hanya membaca status dan log SELinux; tidak mengubah enforcement policy.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private const val MAX_CONSOLE_CHARS = 20_000

@Composable
fun KynoxSnapshotScreen(container: AppContainer, onBack: () -> Unit) {
    var text by remember { mutableStateOf<String?>(null) }
    var savedName by remember { mutableStateOf<String?>(null) }
    var capturing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dir = remember { File(context.filesDir, "kynox_snapshots") }

    // Tampilkan snapshot terakhir yang sudah tersimpan, jika ada.
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val latest = dir.listFiles { f -> f.extension == "json" }?.maxByOrNull { it.lastModified() }
            if (latest != null) {
                runCatching { latest.readText() }.getOrNull()?.let { text = it; savedName = latest.name }
            }
        }
    }

    suspend fun capture() {
        val d = container.deviceInfoRepository.load(); val b = container.batteryRepository.readInfo(); val c = container.cpuRepository.readSnapshot(); val g = container.gpuRepository.readState(); val t = container.thermalRepository.readZones(); val root = container.rootRepository.current()
        val json = JSONObject().apply {
            put("timestamp", System.currentTimeMillis()); put("device", JSONObject().apply { put("model", d.model); put("android", d.androidVersion); put("kernel", d.kernelVersion); put("selinux", d.selinuxStatus); put("root", root.isAvailable) }); put("display", d.display.resolution + " @ " + d.display.refreshRateHz + "Hz"); put("battery", JSONObject().apply { put("percent", b.capacityPercent ?: -1); put("temperatureC", b.temperatureCelsius); put("voltageV", b.voltageMilliVolts); put("currentMa", b.currentMicroAmps) }); put("cpuCores", c.cores.size); put("gpu", if (g.supported) "supported" else "unavailable"); put("thermalZones", t.size)
        }
        dir.mkdirs()
        val file = dir.resolve("snapshot_${System.currentTimeMillis()}.json")
        file.writeText(json.toString(2))
        savedName = file.name
        text = json.toString(2)
    }
    ToolScaffold("Kynox Snapshot", onBack) {
        ToolCard("Device Snapshot", "Simpan kondisi perangkat saat ini sebagai JSON", KynoxIcons.Device) {
            KButton(enabled = !capturing, onClick = {
                capturing = true; error = null
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) { capture() }
                    } catch (t: Throwable) {
                        error = "Snapshot gagal diambil. Coba lagi."
                    }
                    capturing = false
                }
            }, modifier = Modifier.fillMaxWidth()) { Text(if (capturing) "Mengambil…" else "Ambil snapshot") }
            error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = StatusDanger) }
        }
        ToolCard("Snapshot terbaru", savedName ?: "Belum tersimpan", KynoxIcons.Info) {
            val shown = text
            if (shown == null) {
                Text("Belum ada snapshot.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                SelectionContainer { Text(shown, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace) }
            }
        }
        Text("Snapshot disimpan di penyimpanan privat Kynox dan tidak dikirim ke mana pun.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

private enum class ConsoleKind { COMMAND, OUTPUT, ERROR, INFO }
private data class ConsoleLine(val kind: ConsoleKind, val text: String)


@Composable
fun CommandConsoleScreen(container: AppContainer, onBack: () -> Unit) {
    var command by remember { mutableStateOf("") }
    var lines by remember { mutableStateOf(listOf(ConsoleLine(ConsoleKind.INFO, "Konsol root siap. Output dan error ditampilkan terpisah."))) }
    var running by remember { mutableStateOf(false) }
    var pendingRisky by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun runCommand(cmd: String) {
        running = true
        lines = (lines + ConsoleLine(ConsoleKind.COMMAND, "$ $cmd")).takeLast(200)
        scope.launch {
            val added = mutableListOf<ConsoleLine>()
            try {
                val r = withContext(Dispatchers.IO) { container.rootExecutor.execute(cmd, 10000) }
                if (r.stdout.isNotBlank()) added += ConsoleLine(ConsoleKind.OUTPUT, r.stdout.trimEnd().take(MAX_CONSOLE_CHARS))
                if (r.stderr.isNotBlank()) added += ConsoleLine(ConsoleKind.ERROR, r.stderr.trimEnd().take(MAX_CONSOLE_CHARS))
                if (r.timedOut) added += ConsoleLine(ConsoleKind.ERROR, "Waktu habis (10 detik). Perintah dihentikan.")
                else added += ConsoleLine(ConsoleKind.INFO, "exit ${r.exitCode} · ${r.durationMs} ms")
            } catch (t: Throwable) {
                added += ConsoleLine(ConsoleKind.ERROR, "Perintah gagal dijalankan.")
            }
            lines = (lines + added).takeLast(200)
            running = false
        }
    }

    fun submit() {
        val cmd = command.trim()
        if (cmd.isEmpty() || running) return
        when {
            cmd.length > 2000 -> lines = lines + ConsoleLine(ConsoleKind.ERROR, "Perintah terlalu panjang (maks 2000 karakter).")
            classifyCommand(cmd) == CommandRisk.BLOCKED ->
                lines = (lines + ConsoleLine(ConsoleKind.COMMAND, "$ $cmd") + ConsoleLine(ConsoleKind.ERROR, "Diblokir: perintah ini bisa merusak perangkat dan tidak dijalankan dari konsol.")).takeLast(200)
            classifyCommand(cmd) == CommandRisk.RISKY -> pendingRisky = cmd
            else -> { command = ""; runCommand(cmd) }
        }
    }

    pendingRisky?.let { cmd ->
        ConfirmDialog(
            title = "Jalankan perintah berisiko?",
            message = "Perintah ini dapat mengubah atau menghapus data sistem:\n\n$cmd\n\nDijalankan sebagai root dan tidak bisa dibatalkan.",
            confirmLabel = "Jalankan",
            onConfirm = { pendingRisky = null; command = ""; runCommand(cmd) },
            onDismiss = { pendingRisky = null }
        )
    }

    ToolScaffold("Command Console", onBack) {
        ToolCard("Root Console", "Perintah dijalankan melalui su. Gunakan hanya command yang kamu pahami.", KynoxIcons.Root) {
            OutlinedTextField(command, { command = it }, Modifier.fillMaxWidth(), minLines = 2, label = { Text("Command") }, placeholder = { Text("dumpsys display") })
            KButton(enabled = command.isNotBlank() && !running, onClick = { submit() }, modifier = Modifier.fillMaxWidth()) { Text(if (running) "Menjalankan…" else "Jalankan") }
        }
        ToolCard("Output", icon = KynoxIcons.Root) {
            SelectionContainer {
                Column(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    lines.forEach { line ->
                        Text(
                            line.text,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = when (line.kind) {
                                ConsoleKind.COMMAND -> MaterialTheme.colorScheme.primary
                                ConsoleKind.OUTPUT -> MaterialTheme.colorScheme.onSurface
                                ConsoleKind.ERROR -> StatusDanger
                                ConsoleKind.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            fontWeight = if (line.kind == ConsoleKind.COMMAND) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }
            KOutlinedButton(onClick = { lines = listOf(ConsoleLine(ConsoleKind.INFO, "Output dibersihkan.")) }, enabled = !running) { Text("Bersihkan output") }
        }
    }
}

private data class DiagItem(val name: String, val ok: Boolean, val detail: String)

@Composable
fun DiagnosticsScreen(container: AppContainer, onBack: () -> Unit) {
    var items by remember { mutableStateOf(emptyList<DiagItem>()) }
    var running by remember { mutableStateOf(true) }
    var runId by remember { mutableIntStateOf(0) }
    LaunchedEffect(runId) {
        running = true
        items = try {
            withContext(Dispatchers.IO) {
                val root = container.rootRepository.current(); val device = container.deviceInfoRepository.load(); val zones = container.thermalRepository.readZones(); val battery = container.batteryRepository.readInfo(); val display = container.refreshRateRepository.supportedRefreshRates()
                listOf(
                    DiagItem("Root access", root.isAvailable, root.provider.name),
                    DiagItem("SELinux", device.selinuxStatus.equals("Enforcing", true), device.selinuxStatus),
                    DiagItem("Display modes", display.isNotEmpty(), display.joinToString(", ") + " Hz"),
                    DiagItem("Thermal sensors", zones.isNotEmpty(), "${zones.size} zone"),
                    DiagItem("Battery telemetry", (battery.capacityPercent ?: -1) >= 0, "${battery.capacityPercent ?: -1}%"),
                    DiagItem("Android", Build.VERSION.SDK_INT >= 26, "API ${Build.VERSION.SDK_INT}"),
                    DiagItem("GPU telemetry", !device.gpuRenderer.contains("unknown", true), device.gpuRenderer)
                )
            }
        } catch (t: Throwable) {
            listOf(DiagItem("Pemeriksaan", false, "Gagal dijalankan. Coba periksa ulang."))
        }
        running = false
    }
    val failing = items.count { !it.ok }
    ToolScaffold("Kynox Diagnostics", onBack) {
        ToolCard("System check", "Pemeriksaan read-only", KynoxIcons.Info) {
            if (running) {
                Text("Menjalankan pemeriksaan…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                StatusPill(
                    if (failing == 0) "Semua pemeriksaan lolos" else "$failing dari ${items.size} perlu perhatian",
                    if (failing == 0) StatusGood else StatusWarning
                )
                items.forEach { item ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (item.ok) "✓" else "!", color = if (item.ok) StatusGood else StatusWarning, fontWeight = FontWeight.Bold, modifier = Modifier.width(22.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text(item.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    HorizontalDivider()
                }
            }
            KOutlinedButton(onClick = { runId++ }, enabled = !running) { Text(if (running) "Memeriksa…" else "Periksa ulang") }
        }
        Text("Diagnostics tidak mengubah konfigurasi sistem.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
