package com.kynox.gaming.ui.cleaner

import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kynox.gaming.AppContainer
import com.kynox.gaming.data.cleaner.CleanMode
import com.kynox.gaming.data.cleaner.CleanResult
import com.kynox.gaming.data.cleaner.MemorySnapshot
import com.kynox.gaming.ui.components.ConfirmDialog
import com.kynox.gaming.ui.components.DetailTopBar
import com.kynox.gaming.ui.components.IconTile
import com.kynox.gaming.ui.components.KButton
import com.kynox.gaming.ui.components.KOutlinedButton
import com.kynox.gaming.ui.components.KynoxGauge
import com.kynox.gaming.ui.components.kynoxCard
import com.kynox.gaming.ui.theme.KynoxIcons
import com.kynox.gaming.ui.theme.StatusDanger
import com.kynox.gaming.ui.theme.StatusGood
import kotlinx.coroutines.launch
import com.kynox.gaming.ui.theme.KynoxShapes

private enum class CleanAction { RAM, CACHE, BOTH }

@Composable
fun CleanerScreen(container: AppContainer, onBack: () -> Unit) {
    val repository = container.cleanerRepository
    val scope = rememberCoroutineScope()
    val settings by container.settingsRepository.settingsFlow.collectAsState(initial = null)

    var memory by remember { mutableStateOf<MemorySnapshot?>(null) }
    var busy by remember { mutableStateOf(false) }
    var lastRam by remember { mutableStateOf<CleanResult?>(null) }
    var lastCache by remember { mutableStateOf<CleanResult?>(null) }
    var pending by remember { mutableStateOf<CleanAction?>(null) }
    var mode by remember { mutableStateOf(CleanMode.SAFE) }
    var rootAvailable by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        memory = repository.memory()
        rootAvailable = repository.isRootAvailable()
    }

    fun run(action: CleanAction) {
        if (busy) return
        busy = true
        failed = false
        scope.launch {
            try {
                if (action == CleanAction.RAM || action == CleanAction.BOTH) lastRam = repository.cleanRam(mode)
                if (action == CleanAction.CACHE || action == CleanAction.BOTH) lastCache = repository.cleanCache()
            } catch (t: Exception) {
                failed = true
            } finally {
                memory = repository.memory()
                busy = false
            }
        }
    }

    fun request(action: CleanAction) {
        // Mode agresif memaksa henti aplikasi: selalu minta konfirmasi, apa pun pengaturan konfirmasinya.
        val forceConfirm = mode == CleanMode.AGGRESSIVE && action != CleanAction.CACHE
        if (settings?.requireConfirmation == false && !forceConfirm) run(action) else pending = action
    }

    Scaffold(topBar = { DetailTopBar(title = "Bersihkan RAM & Cache", onBack = onBack) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = com.kynox.gaming.ui.components.KynoxListPadding,
            verticalArrangement = com.kynox.gaming.ui.components.KynoxCardSpacing
        ) {
            item {
                com.kynox.gaming.ui.components.SummaryCard {
                    val snapshot = memory
                    val onHero = MaterialTheme.colorScheme.onPrimaryContainer
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("RAM terpakai", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = onHero)
                    }
                    Row(Modifier.padding(top = 4.dp)) {
                        Text(
                            snapshot?.let { "%.0f".format(it.usedPercent) } ?: "--",
                            style = MaterialTheme.typography.displayLarge,
                            maxLines = 1,
                            modifier = Modifier.alignByBaseline()
                        )
                        Text(
                            "%",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(start = 4.dp).alignByBaseline()
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    com.kynox.gaming.ui.components.KLinearProgress(
                        (snapshot?.usedPercent ?: 0f) / 100f,
                        trackColor = onHero.copy(alpha = 0.18f),
                        fillColor = onHero
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        snapshot?.let { "${formatBytes(it.usedBytes)} dari ${formatBytes(it.totalBytes)} · tersedia ${formatBytes(it.availableBytes)}" } ?: "Memuat…",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            item {
                KButton(onClick = { request(CleanAction.BOTH) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text(if (busy) "Membersihkan…" else "Bersihkan semua")
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Mode pembersihan RAM",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    ModeOption("Aman", "Hentikan proses yang aman dihentikan", mode == CleanMode.SAFE, true) { mode = CleanMode.SAFE }
                    ModeOption(
                        "Agresif",
                        if (rootAvailable) "Paksa henti semua aplikasi yang berjalan" else "Butuh root",
                        mode == CleanMode.AGGRESSIVE,
                        rootAvailable
                    ) { mode = CleanMode.AGGRESSIVE }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ActionCard("Bersihkan RAM", "Hentikan proses latar", KynoxIcons.Ram, !busy, Modifier.weight(1f)) { request(CleanAction.RAM) }
                    ActionCard("Bersihkan Cache", "Hapus cache aplikasi", KynoxIcons.Debloat, !busy, Modifier.weight(1f)) { request(CleanAction.CACHE) }
                }
            }
            if (failed) {
                item {
                    Text(
                        "Pembersihan gagal dijalankan. Hasil di bawah (jika ada) berasal dari proses sebelumnya.",
                        style = MaterialTheme.typography.bodySmall,
                        color = StatusDanger,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
            val ram = lastRam
            val cache = lastCache
            if (ram != null || cache != null) {
                item {
                    Column(Modifier.fillMaxWidth().kynoxCard(KynoxShapes.section).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Hasil terakhir", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        ram?.appsStopped?.let { ResultLine("Aplikasi dihentikan", "$it") }
                        val beforeBytes = ram?.availableBefore
                        val afterBytes = ram?.availableAfter
                        if (beforeBytes != null && afterBytes != null) {
                            ResultLine("Memori tersedia", "${formatBytes(beforeBytes)} \u2192 ${formatBytes(afterBytes)}")
                        }
                        ram?.ramFreedBytes?.let { freed ->
                            if (freed > 0L) {
                                ResultLine("RAM dibebaskan", "+${formatBytes(freed)}")
                            } else {
                                Text(
                                    "Tidak ada RAM tambahan yang bisa dibebaskan. Sistem sudah memakai memori seefisien mungkin.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        cache?.cacheFreedBytes?.let { ResultLine("Cache dihapus", formatBytes(it)) }
                        listOfNotNull(ram?.note, cache?.note).distinct().forEach {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item {
                Text(
                    "Android mengelola RAM sendiri. Membersihkan RAM hanya menghentikan proses latar, jadi aplikasi yang dibuka berikutnya bisa terasa sedikit lebih lambat saat dimuat ulang.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }

    pending?.let { action ->
        ConfirmDialog(
            title = "Bersihkan sekarang?",
            message = when (action) {
                CleanAction.RAM -> ramMessage(mode)
                CleanAction.CACHE -> "Cache aplikasi akan dihapus. Data pribadi dan login tidak ikut terhapus."
                CleanAction.BOTH -> ramMessage(mode) + " Cache aplikasi juga dihapus; data pribadi dan login tidak ikut terhapus."
            },
            confirmLabel = "Bersihkan",
            onConfirm = { pending = null; run(action) },
            onDismiss = { pending = null }
        )
    }
}

private fun ramMessage(mode: CleanMode): String = when (mode) {
    CleanMode.SAFE -> "Proses latar yang aman dihentikan akan ditutup."
    CleanMode.AGGRESSIVE ->
        "Semua aplikasi pihak ketiga yang sedang berjalan akan dipaksa berhenti, kecuali Kynox, launcher, keyboard, " +
            "dan layanan aksesibilitas. Notifikasi dari aplikasi itu tertunda sampai dibuka lagi."
}

@Composable
private fun ModeOption(title: String, description: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val shape = KynoxShapes.section
    val container = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    val titleColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant
        selected -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        Modifier.fillMaxWidth()
            .clip(shape)
            .background(container)
            .selectable(selected = selected, enabled = enabled, role = androidx.compose.ui.semantics.Role.RadioButton, onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = titleColor
            )
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (selected) Text("Dipilih", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
private fun ActionCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Column(modifier.kynoxCard(KynoxShapes.section).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon, size = 38.dp)
            Column(Modifier.padding(start = 10.dp)) {
                Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
        KOutlinedButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text("Jalankan") }
    }
}

@Composable
private fun ResultLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = StatusGood)
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1073741824L -> "%.1f GB".format(bytes / 1073741824f)
    bytes >= 1048576L -> "%.0f MB".format(bytes / 1048576f)
    else -> "%d KB".format(bytes / 1024L)
}
