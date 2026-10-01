package com.kynox.gaming.ui.gaming

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

    Scaffold(topBar = { DetailTopBar(title = "Rekam Sesi", onBack = onBack) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                SessionHero(
                    recording = state.isRecording,
                    game = state.selected,
                    onStop = { stopSession(context) }
                )
            }
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
            if (!state.isRecording) {
                item {
                    SectionTitle("Sesi baru", "Pilih aplikasi yang ingin dipantau")
                }
                item {
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .65f), RoundedCornerShape(22.dp))
                            .padding(15.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        if (state.managedGames.isEmpty() && !state.loading) {
                            EmptyGameState()
                        } else {
                            DropdownSelector(
                                label = "Aplikasi",
                                selected = state.selected?.label ?: "Pilih aplikasi",
                                options = state.managedGames.map { it.label },
                                enabled = state.managedGames.isNotEmpty(),
                                onSelected = { label -> state.managedGames.firstOrNull { it.label == label }?.let(viewModel::select) }
                            )
                            SessionMetricGrid()
                            KButton(onClick = { beginRecording() }, enabled = state.selected != null, modifier = Modifier.fillMaxWidth()) {
                                Icon(KynoxIcons.Monitor, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(8.dp))
                                Text("Mulai merekam")
                            }
                        }
                    }
                }
            } else {
                item { LiveMetricCard() }
            }
            if (state.hasReports) {
                item {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f))
                            .clickable(onClick = onOpenReport).padding(15.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                            Icon(KynoxIcons.Logs, null, tint = KynoxAccentDark, modifier = Modifier.size(20.dp))
                        }
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text("Riwayat sesi", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text("Lihat, hapus, dan simpan laporan lengkap", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(KynoxIcons.Chevron, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionHero(recording: Boolean, game: InstalledGame?, onStop: () -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape)
            .background(if (recording) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .7f), shape)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(KynoxAccentDark.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
                Icon(KynoxIcons.Monitor, null, tint = KynoxAccentDark, modifier = Modifier.size(24.dp))
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(if (recording) "Sesi sedang direkam" else "Perekam performa", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    if (recording) "Kynox sedang mengumpulkan metrik secara real-time" else "FPS, CPU, GPU, suhu, frekuensi, dan daya",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            StatusPill(if (recording) "Aktif" else "Siap", if (recording) StatusGood else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (game != null) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp)).background(MaterialTheme.colorScheme.surface.copy(alpha = .7f)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                AppIconImage(game.packageName, 40.dp)
                Column(Modifier.weight(1f).padding(start = 11.dp)) {
                    Text(game.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Text(game.packageName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
        }
        if (recording) {
            KButton(onClick = onStop, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = StatusDanger)) {
                Text("Hentikan rekaman")
            }
        }
    }
}

@Composable
private fun InfoCard(icon: ImageVector, title: String, description: String, action: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f)).padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
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
private fun SectionTitle(title: String, subtitle: String) {
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SessionMetricGrid() {
    val items = listOf("FPS", "CPU", "GPU", "Suhu", "Daya")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(2).forEach { rowItems ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowItems.forEach { label ->
                    MetricPlaceholder(label, Modifier.weight(1f))
                }
                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MetricPlaceholder(label: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(15.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .35f)).padding(12.dp)) {
        Text("—", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LiveMetricCard() {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface).border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .6f), RoundedCornerShape(20.dp)).padding(15.dp)) {
        Text("Pemantauan langsung", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        Text("Nilai real-time tersedia melalui overlay Kynox saat permainan berjalan.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
