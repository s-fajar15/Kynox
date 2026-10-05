package com.kynox.gaming.ui.gaming

import com.kynox.gaming.ui.components.kynoxCard
import com.kynox.gaming.ui.components.KynoxListRow
import com.kynox.gaming.ui.components.ListCard
import com.kynox.gaming.ui.components.IconTile
import com.kynox.gaming.domain.model.LiveMetrics
import com.kynox.gaming.core.utils.gpuFreqMhz
import kotlinx.coroutines.delay
import androidx.compose.ui.draw.alpha
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import android.Manifest
import android.content.Context
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kynox.gaming.AppContainer
import com.kynox.gaming.R
import com.kynox.gaming.domain.model.InstalledGame
import com.kynox.gaming.service.GameSessionService
import com.kynox.gaming.ui.components.AppIconImage
import com.kynox.gaming.ui.components.DetailTopBar
import com.kynox.gaming.ui.components.DropdownSelector
import com.kynox.gaming.ui.components.GenericViewModelFactory
import com.kynox.gaming.ui.components.KButton
import com.kynox.gaming.ui.components.KOutlinedButton
import com.kynox.gaming.ui.components.StatusPill
import com.kynox.gaming.ui.theme.KynoxAccentDark
import com.kynox.gaming.ui.theme.KynoxIcons
import com.kynox.gaming.ui.theme.StatusDanger
import com.kynox.gaming.ui.theme.StatusGood
import com.kynox.gaming.ui.theme.KynoxShapes

@Composable
fun SessionScreen(container: AppContainer, onBack: () -> Unit, onOpenReport: () -> Unit) {
    val viewModel: SessionViewModel = viewModel(
        factory = GenericViewModelFactory {
            SessionViewModel(container.gameLibraryRepository, container.gameSessionRepository, container.overlayPermissionRepository)
        }
    )
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        startSession(context, state.selected)
    }

    fun beginRecording() {
        val selected = state.selected ?: return
        viewModel.prepareOverlay {
            if (Build.VERSION.SDK_INT >= 33) notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            else startSession(context, selected)
        }
    }

    val nowMs by produceState(initialValue = System.currentTimeMillis(), key1 = state.isRecording) {
        while (state.isRecording) {
            value = System.currentTimeMillis()
            delay(1000)
        }
    }

    Scaffold(topBar = { DetailTopBar(title = "Rekam Sesi", onBack = onBack) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (state.isRecording) {
                item {
                    RecordingCard(
                        game = state.selected,
                        elapsedMs = if (state.startedAtMs > 0L) (nowMs - state.startedAtMs).coerceAtLeast(0L) else 0L
                    )
                }
                item { LiveGrid(state.live) }
                item {
                    KButton(
                        onClick = { stopSession(context) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = StatusDanger)
                    ) { Text("Hentikan rekaman") }
                }
            } else {
                item { ReadyCard() }
                item {
                    GamePickerCard(
                        games = state.managedGames,
                        selected = state.selected,
                        loading = state.loading,
                        onSelect = viewModel::select
                    )
                }
                item { RecordedMetrics() }
                if (!state.overlayGranted) {
                    item {
                        InfoCard(
                            icon = KynoxIcons.Apps,
                            title = "Overlay diperlukan",
                            description = "Kynox memakai overlay kecil untuk menampilkan FPS saat sesi berjalan. Data tetap direkam di latar belakang.",
                            action = { openOverlaySettings(context) }
                        )
                    }
                }
                item {
                    KButton(
                        onClick = { beginRecording() },
                        enabled = state.selected != null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(StatusDanger))
                        Spacer(Modifier.size(10.dp))
                        Text("Mulai merekam")
                    }
                }
            }
            if (state.hasReports) {
                item {
                    ListCard {
                        KynoxListRow(
                            "Riwayat sesi", "Lihat, hapus, dan simpan laporan lengkap", KynoxIcons.Logs,
                            onClick = onOpenReport
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadyCard() {
    Row(
        Modifier.fillMaxWidth().kynoxCard(KynoxShapes.hero).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconTile(KynoxIcons.Monitor, size = 56.dp)
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text("Perekam performa", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Rekam FPS, CPU, GPU, suhu, frekuensi, dan daya selama kamu bermain.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        StatusPill("Siap", MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun GamePickerCard(
    games: List<InstalledGame>,
    selected: InstalledGame?,
    loading: Boolean,
    onSelect: (InstalledGame) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "Aplikasi yang dipantau",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        if (games.isEmpty() && !loading) {
            Column(Modifier.fillMaxWidth().kynoxCard(KynoxShapes.section).padding(18.dp)) { EmptyGameState() }
            return@Column
        }
        var open by remember { mutableStateOf(false) }
        Box {
            Row(
                Modifier.fillMaxWidth().kynoxCard(KynoxShapes.section).clickable(enabled = games.isNotEmpty()) { open = true }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (selected != null) {
                    AppIconImage(selected.packageName, 44.dp)
                } else {
                    IconTile(KynoxIcons.Apps, size = 44.dp)
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(selected?.label ?: "Pilih aplikasi", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text(
                        selected?.packageName ?: "Ketuk untuk memilih",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                Icon(KynoxIcons.Chevron, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                games.forEach { game ->
                    DropdownMenuItem(
                        text = { Text(game.label) },
                        leadingIcon = { AppIconImage(game.packageName, 28.dp) },
                        onClick = { open = false; onSelect(game) }
                    )
                }
            }
        }
    }
}

@Composable
private fun RecordedMetrics() {
    val items = listOf(
        "FPS" to KynoxIcons.Monitor,
        "CPU" to KynoxIcons.Cpu,
        "GPU" to KynoxIcons.Gpu,
        "Suhu" to KynoxIcons.Thermal,
        "Daya" to KynoxIcons.Battery
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "Yang direkam",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items.chunked(2).forEach { rowItems ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    rowItems.forEach { (label, icon) ->
                        Row(
                            Modifier.weight(1f).kynoxCard(KynoxShapes.section).padding(horizontal = 12.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconTile(icon, size = 32.dp, circle = true)
                            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 10.dp))
                        }
                    }
                    if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun RecordingCard(game: InstalledGame?, elapsedMs: Long) {
    val pulse = rememberInfiniteTransition(label = "rec")
    val alpha by pulse.animateFloat(
        initialValue = 1f, targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "recAlpha"
    )
    Column(
        Modifier.fillMaxWidth().kynoxCard(KynoxShapes.hero).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).alpha(alpha).clip(CircleShape).background(StatusDanger))
            Text(
                "Sedang merekam",
                style = MaterialTheme.typography.titleSmall,
                color = StatusDanger,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            formatClock(elapsedMs),
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        if (game != null) {
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppIconImage(game.packageName, 40.dp)
                Column(Modifier.weight(1f).padding(start = 11.dp)) {
                    Text(game.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text(game.packageName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun LiveGrid(live: LiveMetrics) {
    val items = listOf(
        Triple("FPS", live.fps?.let { "%.0f".format(it) } ?: "--", KynoxIcons.Monitor),
        Triple("Suhu CPU", live.cpuTempCelsius?.let { "%.0f°C".format(it) } ?: "--", KynoxIcons.Thermal),
        Triple("GPU", live.gpuFreqRaw?.let { "${gpuFreqMhz(it)} MHz" } ?: "--", KynoxIcons.Gpu),
        Triple("Daya", live.powerWatts?.let { "%.1f W".format(it) } ?: "--", KynoxIcons.Battery)
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.chunked(2).forEach { rowItems ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowItems.forEach { (label, value, icon) ->
                    Row(
                        Modifier.weight(1f).kynoxCard(KynoxShapes.section).padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconTile(icon, size = 38.dp)
                        Column(Modifier.padding(start = 11.dp)) {
                            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

private fun formatClock(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val sec = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
}

@Composable
private fun InfoCard(icon: ImageVector, title: String, description: String, action: () -> Unit) {
    Column(Modifier.fillMaxWidth().kynoxCard(KynoxShapes.section).padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = KynoxAccentDark, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(9.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        }
        Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        KOutlinedButton(onClick = action) { Text("Buka izin overlay") }
    }
}

@Composable
private fun EmptyGameState() {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(KynoxIcons.Apps, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(30.dp))
        Spacer(Modifier.height(8.dp))
        Text("Belum ada aplikasi yang dikelola", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text("Tambahkan aplikasi dari pustaka game terlebih dahulu.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun startSession(context: Context, game: InstalledGame?) {
    if (game == null) return
    val intent = Intent(context, GameSessionService::class.java).apply {
        action = GameSessionService.ACTION_START
        putExtra(GameSessionService.EXTRA_PACKAGE, game.packageName)
        putExtra(GameSessionService.EXTRA_LABEL, game.label)
    }
    if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
}

private fun stopSession(context: Context) {
    context.startService(Intent(context, GameSessionService::class.java).apply { action = GameSessionService.ACTION_STOP })
}

private fun openOverlaySettings(context: Context) {
    context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
}
