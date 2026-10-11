package com.kynox.gaming.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kynox.gaming.AppContainer
import com.kynox.gaming.core.root.RootProvider
import com.kynox.gaming.ui.components.GenericViewModelFactory
import com.kynox.gaming.ui.components.KLinearProgress
import com.kynox.gaming.ui.components.KynoxCardSpacing
import com.kynox.gaming.ui.components.KynoxListPadding
import com.kynox.gaming.ui.components.KynoxTopBar
import com.kynox.gaming.ui.components.MetricTile
import com.kynox.gaming.ui.components.RealtimeLineChart
import com.kynox.gaming.ui.components.SummaryCard
import com.kynox.gaming.ui.components.TonalPill
import com.kynox.gaming.ui.components.fmtDec
import com.kynox.gaming.ui.components.fmtGbValue
import com.kynox.gaming.ui.components.fmtPercent
import com.kynox.gaming.ui.components.kynoxCard
import com.kynox.gaming.ui.theme.KynoxIcons
import com.kynox.gaming.ui.theme.KynoxShapes
import com.kynox.gaming.ui.theme.KynoxStatusBands
import com.kynox.gaming.ui.theme.StatusDanger
import com.kynox.gaming.ui.theme.StatusGood
import com.kynox.gaming.ui.theme.StatusWarning

/**
 * Beranda bentuk bento: judul "Kynox" + status root (teks polos abu), kartu suhu CPU besar, kotak Baterai | RAM,
 * grafik Beban CPU, kotak Penyimpanan | Swap. Semua nilai dibaca dari perangkat; yang tak terbaca tampil "--".
 * Kartu peringatan hanya muncul bila ada masalah dan membuka layar Diagnostics.
 */
@Composable
fun DashboardScreen(container: AppContainer, onOpenStatus: () -> Unit = {}) {
    val vm: DashboardViewModel = viewModel(factory = GenericViewModelFactory {
        DashboardViewModel(
            container.batteryRepository,
            container.cpuRepository,
            container.gpuRepository,
            container.thermalRepository,
            container.deviceInfoRepository,
            container.settingsRepository,
            container.rootRepository
        )
    })
    val state by vm.uiState.collectAsState()

    Scaffold(topBar = {
        KynoxTopBar(
            title = "Kynox",
            actions = {
                val label = rootLabel(state)
                if (label.isNotEmpty()) {
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (state.rootStatus?.isAvailable == false) StatusWarning else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        )
    }) { padding ->
        // Tidak ada layar penuh "memuat": kartu langsung tampil dengan "--" lalu terisi saat data masuk.
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = KynoxListPadding,
            verticalArrangement = KynoxCardSpacing
        ) {
            val problems = problemList(state)
            if (problems.isNotEmpty()) {
                item(key = "attention", contentType = "attention") { AttentionCard(problems, onOpenStatus) }
            }
            item(key = "thermal", contentType = "thermal") { ThermalHero(state) }
            item(key = "battery_ram", contentType = "bento") { BatteryRamRow(state) }
            item(key = "load", contentType = "chart") { LoadCard(state) }
            item(key = "storage_swap", contentType = "bento") { StorageSwapRow(state) }
        }
    }
}

private fun rootLabel(state: DashboardUiState): String {
    val root = state.rootStatus ?: return ""
    if (!root.isAvailable) return "Tanpa root"
    return when (root.provider) {
        RootProvider.MAGISK -> "Root · Magisk"
        RootProvider.KERNELSU -> "Root · KernelSU"
        RootProvider.APATCH -> "Root · APatch"
        else -> "Root"
    }
}

/** Masalah yang perlu terlihat langsung. Kosong = tidak ada kartu peringatan. */
private fun problemList(state: DashboardUiState): List<String> {
    if (state.loading && state.rootStatus == null) return emptyList()
    return buildList {
        if (state.rootStatus?.isAvailable == false) add("Akses root tidak tersedia, sebagian fitur terbatas")
        if (!state.loading && !state.thermalEngineActive) add("Pengaman termal sedang dimatikan")
        if (!state.loading && state.gpu?.supported == false) add("Kontrol GPU tidak didukung kernel ini")
    }
}

@Composable
private fun AttentionCard(problems: List<String>, onClick: () -> Unit) {
    val tone = StatusWarning
    Row(
        Modifier
            .fillMaxWidth()
            .kynoxCard(KynoxShapes.section, container = tone.copy(alpha = 0.16f).compositeOver(MaterialTheme.colorScheme.surface))
            .clickable(onClick = onClick)
            .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(KynoxIcons.Info, contentDescription = "Perlu perhatian", tint = tone, modifier = Modifier.padding(end = 12.dp))
        Column(Modifier.weight(1f)) {
            Text("Perlu perhatian", style = MaterialTheme.typography.titleSmall)
            problems.forEach {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Icon(KynoxIcons.Chevron, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ThermalHero(state: DashboardUiState) {
    val temp = state.cpuTempCelsius ?: state.gpuTempCelsius
    val statusLabel = when {
        temp == null -> "Tidak tersedia"
        temp >= KynoxStatusBands.TEMP_CRIT -> "Panas"
        temp >= KynoxStatusBands.TEMP_WARN -> "Hangat"
        else -> "Normal"
    }
    val onHero = MaterialTheme.colorScheme.onPrimaryContainer
    val hero = MaterialTheme.colorScheme.primaryContainer
    // Normal = pill tonal terbalik; hangat/panas memakai warna status (teks tetap menyebut keadaannya).
    val pillContainer = when {
        temp == null -> onHero.copy(alpha = 0.7f)
        temp >= KynoxStatusBands.TEMP_CRIT -> StatusDanger
        temp >= KynoxStatusBands.TEMP_WARN -> StatusWarning
        else -> onHero
    }
    val cpu = state.cpu?.overallUsagePercent
    val gpu = state.gpu?.utilizationPercent
    val gpuUnsupported = !state.loading && state.gpu?.supported == false

    SummaryCard(padding = 20.dp) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Suhu CPU",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            TonalPill(statusLabel, container = pillContainer, content = hero)
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Row {
                Text(
                    temp?.let { "%.0f".format(it) } ?: "--",
                    style = MaterialTheme.typography.displayLarge,
                    maxLines = 1,
                    modifier = Modifier.alignByBaseline()
                )
                Text(
                    "°C",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = 4.dp).alignByBaseline()
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            HeroLoad("CPU", cpu, fmtPercent(cpu), Modifier.weight(1f))
            HeroLoad(
                "GPU", gpu,
                if (gpuUnsupported) "Tidak didukung" else fmtPercent(gpu),
                Modifier.weight(1f)
            )
        }
    }
}

/** Label di kiri, nilai di kanan, batang di bawahnya. Warna mengikuti kartu (onPrimaryContainer). */
@Composable
private fun HeroLoad(label: String, percent: Float?, valueText: String, modifier: Modifier) {
    val onHero = MaterialTheme.colorScheme.onPrimaryContainer
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text(valueText, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.End, maxLines = 1)
        }
        Spacer(Modifier.height(8.dp))
        KLinearProgress((percent ?: 0f) / 100f, trackColor = onHero.copy(alpha = 0.18f), fillColor = onHero)
    }
}

/** Suhu baterai: 40 °C ke atas waspada, 45 °C ke atas bahaya. Angkanya tetap tampil, jadi bukan hanya warna. */
private fun batteryTempColor(c: Float?): Color = when {
    c == null -> Color.Unspecified
    c >= 45f -> StatusDanger
    c >= 40f -> StatusWarning
    else -> Color.Unspecified
}

private fun chargingLabel(status: String): String? = when {
    // "Tidak mengisi" harus dicek sebelum "mengisi", karena yang pertama memuat yang kedua.
    status.contains("Tidak mengisi", ignoreCase = true) || status.contains("Not charging", ignoreCase = true) -> "Tidak mengisi"
    status.contains("Mengisi", ignoreCase = true) || status.contains("Charging", ignoreCase = true) -> "Mengisi"
    status.contains("Penuh", ignoreCase = true) || status.contains("Full", ignoreCase = true) -> "Penuh"
    status.contains("terpakai", ignoreCase = true) || status.contains("Discharging", ignoreCase = true) -> "Tidak mengisi"
    else -> null
}

@Composable
private fun BatteryRamRow(state: DashboardUiState) {
    val battery = state.battery
    val ram = state.ram
    val ramPercent = ram?.let { if (it.totalBytes > 0) it.usedBytes * 100f / it.totalBytes else null }
    val charging = battery?.let { chargingLabel(it.chargingStatus) } == "Mengisi"
    val batterySub = listOfNotNull(
        battery?.let { chargingLabel(it.chargingStatus) },
        battery?.powerWatts?.let { fmtDec(it) + " W" }
    ).joinToString(" · ").ifEmpty { null }

    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MetricTile(
            label = "Baterai",
            value = battery?.capacityPercent?.toString() ?: "--",
            unit = "%",
            sub = batterySub,
            subColor = if (charging) StatusGood else Color.Unspecified,
            asideLabel = "Suhu",
            aside = battery?.temperatureCelsius?.let { fmtDec(it, 0) + " °C" } ?: "--",
            asideColor = batteryTempColor(battery?.temperatureCelsius),
            progress = (battery?.capacityPercent ?: 0) / 100f,
            modifier = Modifier.weight(1f).fillMaxSize()
        )
        MetricTile(
            label = "RAM",
            value = ram?.let { fmtGbValue(it.usedBytes) } ?: "--",
            unit = "GB",
            sub = ram?.let { "dari ${fmtGbValue(it.totalBytes, 0)} GB · ${fmtPercent(ramPercent)}" },
            progress = (ramPercent ?: 0f) / 100f,
            progressColor = KynoxStatusBands.loadColor(ramPercent, MaterialTheme.colorScheme.primary),
            modifier = Modifier.weight(1f).fillMaxSize()
        )
    }
}

@Composable
private fun LoadCard(state: DashboardUiState) {
    val cpu = state.cpu?.overallUsagePercent
    Column(
        Modifier
            .fillMaxWidth()
            .kynoxCard(KynoxShapes.section)
            .padding(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Beban CPU",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Text(
                fmtPercent(cpu),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1
            )
        }
        Spacer(Modifier.height(12.dp))
        RealtimeLineChart(values = state.cpuUsageHistory, height = 96.dp, maxValue = 100f)
    }
}

@Composable
private fun StorageSwapRow(state: DashboardUiState) {
    val storage = state.storage?.takeIf { it.totalBytes > 0 }
    val storagePercent = storage?.let { it.usedBytes * 100f / it.totalBytes }
    val hasSwap = state.swapTotalBytes > 0
    val swapFraction = if (hasSwap) state.swapUsedBytes.toFloat() / state.swapTotalBytes else 0f

    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MetricTile(
            label = "Penyimpanan",
            value = storage?.let { fmtGbValue(it.usedBytes, 0) + " GB" } ?: "--",
            sub = storage?.let { "dari ${fmtGbValue(it.totalBytes, 0)} GB" },
            progress = (storagePercent ?: 0f) / 100f,
            progressColor = KynoxStatusBands.loadColor(storagePercent, MaterialTheme.colorScheme.primary),
            modifier = Modifier.weight(1f).fillMaxSize()
        )
        MetricTile(
            label = "Swap (zram)",
            value = if (hasSwap) fmtGbValue(state.swapUsedBytes) + " GB" else "--",
            sub = if (hasSwap) "dari ${fmtGbValue(state.swapTotalBytes, 0)} GB" else if (state.loading) null else "Tidak aktif",
            progress = swapFraction,
            modifier = Modifier.weight(1f).fillMaxSize()
        )
    }
}
