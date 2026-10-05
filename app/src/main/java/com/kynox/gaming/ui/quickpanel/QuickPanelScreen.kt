package com.kynox.gaming.ui.quickpanel

import com.kynox.gaming.ui.components.startActivitySafely
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.kynox.gaming.AppContainer
import com.kynox.gaming.data.settings.ThemeMode
import com.kynox.gaming.domain.model.ProfileType
import com.kynox.gaming.ui.components.DetailTopBar
import com.kynox.gaming.ui.components.IconTile
import com.kynox.gaming.ui.components.kynoxCard
import com.kynox.gaming.ui.theme.KynoxIcons
import com.kynox.gaming.ui.theme.StatusGood
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import com.kynox.gaming.ui.theme.KynoxShapes

/**
 * Panel Cepat: pintasan ke fitur yang paling sering dipakai. Setiap kartu menampilkan keadaan
 * saat ini. Yang membutuhkan izin khusus (Jangan Ganggu, kecerahan) meminta izinnya lewat layar
 * pengaturan sistem; tidak ada yang diubah diam-diam.
 */
@Composable
fun QuickPanelScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onOpenProfiles: () -> Unit,
    onOpenRefreshRate: () -> Unit,
    onOpenThermal: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by container.settingsRepository.settingsFlow.collectAsState(initial = null)

    var tick by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) tick++ }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var profile by remember { mutableStateOf<ProfileType?>(null) }
    var thermalPolicy by remember { mutableStateOf<String?>(null) }
    var thermalSupported by remember { mutableStateOf(false) }
    var refreshHz by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(tick) {
        profile = try { container.profileRepository.activeProfile() } catch (_: Throwable) { null }
        try {
            val summary = container.thermalRepository.policySummary()
            thermalSupported = summary.supported
            thermalPolicy = summary.currentPolicy
        } catch (_: Throwable) {
        }
        refreshHz = try { container.refreshRateRepository.readActiveDisplayRefreshRate() } catch (_: Throwable) { null }
    }

    val notificationManager = remember { context.getSystemService(NotificationManager::class.java) }
    val dndGranted = remember(tick) { notificationManager?.isNotificationPolicyAccessGranted == true }
    val dndOn = remember(tick) {
        dndGranted && notificationManager?.currentInterruptionFilter.let {
            it != null && it != NotificationManager.INTERRUPTION_FILTER_ALL && it != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
        }
    }

    val canWriteSettings = remember(tick) { try { Settings.System.canWrite(context) } catch (_: Throwable) { false } }
    var brightness by remember(tick) { mutableFloatStateOf(readBrightness(context)) }

    val darkOn = settings?.themeMode != ThemeMode.LIGHT
    val performanceOn = profile == ProfileType.PERFORMANCE || profile == ProfileType.GAMING

    Scaffold(topBar = { DetailTopBar(title = "Panel Cepat", onBack = onBack) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth().kynoxCard(KynoxShapes.section).clickable(onClick = onOpenProfiles).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconTile(KynoxIcons.Profiles, size = 52.dp, circle = true)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Mode Performa", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (performanceOn) "Aktif" else "Nonaktif",
                            style = MaterialTheme.typography.titleSmall,
                            color = if (performanceOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Box(
                        Modifier.size(42.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) { Icon(KynoxIcons.PlayArrow, null, tint = MaterialTheme.colorScheme.onSurface) }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    QuickTile("Refresh Rate", refreshHz?.let { "$it Hz" } ?: "--", false, KynoxIcons.RefreshRate, Modifier.weight(1f), onOpenRefreshRate)
                    QuickTile(
                        "Mode Gelap", if (darkOn) "Aktif" else "Nonaktif", darkOn, KynoxIcons.DarkMode, Modifier.weight(1f)
                    ) {
                        scope.launch { container.settingsRepository.setThemeMode(if (darkOn) ThemeMode.LIGHT else ThemeMode.DARK) }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    QuickTile(
                        "Perlindungan Panas",
                        when {
                            !thermalSupported -> "Tidak didukung"
                            thermalPolicy.isNullOrBlank() -> "Aktif"
                            else -> thermalPolicy.orEmpty()
                        },
                        thermalSupported,
                        KynoxIcons.Shield,
                        Modifier.weight(1f),
                        onOpenThermal
                    )
                    QuickTile(
                        "Jangan Ganggu",
                        when {
                            !dndGranted -> "Beri izin"
                            dndOn -> "Aktif"
                            else -> "Nonaktif"
                        },
                        dndOn,
                        KynoxIcons.DoNotDisturb,
                        Modifier.weight(1f)
                    ) {
                        if (!dndGranted) {
                            context.startActivitySafely(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        } else {
                            try {
                                notificationManager?.setInterruptionFilter(
                                    if (dndOn) NotificationManager.INTERRUPTION_FILTER_ALL else NotificationManager.INTERRUPTION_FILTER_PRIORITY
                                )
                            } catch (_: Throwable) {
                            }
                            tick++
                        }
                    }
                }
            }
            item {
                Row(
                    Modifier.fillMaxWidth().kynoxCard(KynoxShapes.section).padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(KynoxIcons.Brightness, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                    if (canWriteSettings) {
                        Slider(
                            value = brightness,
                            onValueChange = {
                                brightness = it
                                writeBrightness(context, it)
                            },
                            modifier = Modifier.weight(1f).padding(horizontal = 10.dp)
                        )
                    } else {
                        Text(
                            "Izinkan Kynox mengubah kecerahan",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f).padding(horizontal = 12.dp).clickable {
                                context.startActivitySafely(
                                    Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}"))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        )
                    }
                    Icon(
                        KynoxIcons.Settings, null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp).clip(CircleShape).clickable {
                            context.startActivitySafely(Intent(Settings.ACTION_DISPLAY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickTile(
    title: String,
    status: String,
    active: Boolean,
    icon: ImageVector,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier.height(92.dp).kynoxCard(KynoxShapes.section).clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconTile(icon, size = 42.dp, circle = true)
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium, maxLines = 2)
            Text(
                status,
                style = MaterialTheme.typography.labelMedium,
                color = if (active) StatusGood else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

private fun readBrightness(context: Context): Float = try {
    Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
} catch (_: Throwable) {
    0.5f
}

private fun writeBrightness(context: Context, value: Float) {
    try {
        val level = (value.coerceIn(0.02f, 1f) * 255f).roundToInt()
        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, level)
    } catch (_: Throwable) {
    }
}
