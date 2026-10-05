package com.kynox.gaming.ui.dashboard

import com.kynox.gaming.ui.components.Sparkline
import com.kynox.gaming.ui.components.IconTile
import androidx.compose.foundation.clickable
import com.kynox.gaming.ui.components.kynoxCard
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import com.kynox.gaming.ui.theme.KynoxShapes
import com.kynox.gaming.ui.components.KynoxGauge
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kynox.gaming.AppContainer
import com.kynox.gaming.R
import com.kynox.gaming.core.utils.gpuFreqMhz
import com.kynox.gaming.ui.components.GenericViewModelFactory
import com.kynox.gaming.ui.components.KLoadingIndicator
import com.kynox.gaming.ui.components.KMark
import com.kynox.gaming.ui.components.KLinearProgress
import com.kynox.gaming.ui.components.KynoxTopBar
import com.kynox.gaming.ui.components.RealtimeLineChart
import com.kynox.gaming.ui.components.SectionCard
import com.kynox.gaming.ui.components.StatusPill
import com.kynox.gaming.ui.theme.KynoxAccentDark
import com.kynox.gaming.ui.theme.KynoxIcons
import com.kynox.gaming.ui.theme.KynoxStatusBands
import com.kynox.gaming.ui.theme.StatusDanger
import com.kynox.gaming.ui.theme.StatusGood
import com.kynox.gaming.ui.theme.StatusWarning
import com.kynox.gaming.ui.theme.kynoxColors
import androidx.compose.ui.res.stringResource

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
            subtitle = "Performa perangkat, di tanganmu.",
            leading = { KMark(markSize = 30.dp) },
            actions = {
                val root = state.rootStatus?.isAvailable == true
                StatusPill(if (root) "Root" else "Terbatas", if (root) StatusGood else StatusWarning)
            }
        )
    }) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { KLoadingIndicator(markSize = 36.dp) }
            return@Scaffold
        }

        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { ThermalHero(state) }
            item { LoadCard(state) }
            item { BentoRow(state) }
            item { StatusCard(state, onOpenStatus) }
        }
    }
}

@Composable
private fun ThermalHero(state: DashboardUiState) {
    val temp = state.cpuTempCelsius ?: state.gpuTempCelsius
    val tempLabel = when {
        temp == null -> "Tidak tersedia"
        temp >= KynoxStatusBands.TEMP_CRIT -> "Panas"
        temp >= KynoxStatusBands.TEMP_WARN -> "Hangat"
        else -> "Normal"
    }
    val tempColor = when {
        temp == null -> MaterialTheme.colorScheme.onSurfaceVariant
        temp >= KynoxStatusBands.TEMP_CRIT -> StatusDanger
        temp >= KynoxStatusBands.TEMP_WARN -> StatusWarning
        else -> StatusGood
    }
    // Skala busur: 25\u00B0C (dingin) sampai 80\u00B0C (sangat panas).
    val progress = temp?.let { ((it - 25f) / 55f).coerceIn(0f, 1f) } ?: 0f
    val cpu = state.cpu?.overallUsagePercent
    val gpu = state.gpu?.utilizationPercent
    val ram = state.ram?.let { if (it.totalBytes > 0) it.usedBytes * 100f / it.totalBytes else null }

    Column(
        Modifier.fillMaxWidth().kynoxCard(KynoxShapes.hero).padding(horizontal = 20.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        KynoxGauge(progress = progress, modifier = Modifier.size(216.dp), strokeWidth = 18.dp, color = tempColor) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Suhu CPU", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    temp?.let { "%.0f\u00B0".format(it) } ?: "--",
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                StatusPill(tempLabel, tempColor)
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            LoadStat("CPU", cpu, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
            LoadStat("GPU", gpu, MaterialTheme.kynoxColors.seriesSecondary, Modifier.weight(1f))
            LoadStat("RAM", ram, StatusGood, Modifier.weight(1f))
        }
    }
}

@Composable
private fun LoadStat(label: String, percent: Float?, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text(
                percent?.let { "%.0f%%".format(it) } ?: "--",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = KynoxStatusBands.loadColor(percent, MaterialTheme.colorScheme.onSurface)
            )
        }
        Spacer(Modifier.height(6.dp))
        KLinearProgress((percent ?: 0f) / 100f, fillColor = KynoxStatusBands.loadColor(percent, color))
    }
}

@Composable
private fun LoadCard(state: DashboardUiState) {
    val cpu = state.cpu?.overallUsagePercent
    SectionCard(
        title = "Beban CPU",
        trailing = {
            Text(
                cpu?.let { "%.0f%%".format(it) } ?: "--",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    ) {
        RealtimeLineChart(values = state.cpuUsageHistory, height = 84.dp)
    }
}

@Composable
private fun BentoRow(state: DashboardUiState) {
    val battery = state.battery
    val ram = state.ram
    val gb = 1073741824f
    val ramPercent = ram?.let { if (it.totalBytes > 0) it.usedBytes * 100f / it.totalBytes else null }
    val charging = battery?.let { chargingLabel(it.chargingStatus) } == "Mengisi"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        BentoTile(
            label = "Baterai",
            value = battery?.capacityPercent?.toString() ?: "--",
            unit = "%",
            sub = listOfNotNull(
                battery?.let { chargingLabel(it.chargingStatus) },
                battery?.powerWatts?.let { "%.1f W".format(it) }
            ).joinToString(" \u00B7 ").ifEmpty { null },
            progress = (battery?.capacityPercent ?: 0) / 100f,
            progressColor = if (charging) StatusGood else MaterialTheme.colorScheme.primary,
            icon = KynoxIcons.Battery,
            modifier = Modifier.weight(1f)
        )
        BentoTile(
            label = "RAM",
            value = ram?.let { "%.1f".format(it.usedBytes / gb) } ?: "--",
            unit = " GB",
            sub = ram?.let { "dari %.0f GB".format(it.totalBytes / gb) },
            progress = (ramPercent ?: 0f) / 100f,
            progressColor = KynoxStatusBands.loadColor(ramPercent, MaterialTheme.kynoxColors.seriesSecondary),
            icon = KynoxIcons.Ram,
            modifier = Modifier.weight(1f)
        )
    }
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
private fun BentoTile(
    label: String,
    value: String,
    unit: String,
    sub: String?,
    progress: Float,
    progressColor: androidx.compose.ui.graphics.Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier
) {
    Column(modifier.heightIn(min = 148.dp).kynoxCard(KynoxShapes.section).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon, size = 32.dp)
            Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 10.dp))
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(unit, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 2.dp, bottom = 6.dp))
        }
        Text(
            sub ?: " ",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Spacer(Modifier.height(10.dp))
        KLinearProgress(progress, fillColor = progressColor)
    }
}

@Composable
private fun StatusCard(state: DashboardUiState, onClick: () -> Unit) {
    val root = state.rootStatus?.isAvailable == true
    val allGood = root && state.thermalEngineActive && state.gpu?.supported == true
    val color = if (allGood) StatusGood else StatusWarning
    Row(
        Modifier.fillMaxWidth().kynoxCard(KynoxShapes.pill).clickable(onClick = onClick).padding(horizontal = 22.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(color))
                Text("Status Sistem", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 10.dp))
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(color))
                Text(
                    if (allGood) "Semua berjalan normal" else "Sebagian fitur terbatas",
                    style = MaterialTheme.typography.bodyMedium,
                    color = color,
                    modifier = Modifier.padding(start = 10.dp)
                )
            }
        }
        Icon(KynoxIcons.Chevron, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PerformanceSection(state: DashboardUiState) {
    SectionCard("Performa real-time") {
        val cpu = state.cpu?.overallUsagePercent
        val gpu = state.gpu?.utilizationPercent
        Text("CPU", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(cpu?.let { "%.0f%%".format(it) } ?: "--", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.width(64.dp))
            KLinearProgress(cpu?.div(100f) ?: 0f, Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Text("GPU", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(gpu?.let { "%.0f%%".format(it) } ?: "--", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.width(64.dp))
            KLinearProgress(gpu?.div(100f) ?: 0f, Modifier.weight(1f), fillColor = com.kynox.gaming.ui.theme.KynoxSeriesGpuDark)
        }
        Spacer(Modifier.height(12.dp))
        RealtimeLineChart(values = state.cpuUsageHistory)
    }
}

@Composable
private fun BatterySection(state: DashboardUiState) {
    val battery = state.battery
    SectionCard("Baterai", trailing = { Text(battery?.capacityPercent?.let { "$it%" } ?: "--", style = MaterialTheme.typography.labelMedium, color = KynoxAccentDark) }) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            CompactValue("Tegangan", battery?.voltageMilliVolts?.let { "${it} mV" } ?: "--")
            CompactValue("Arus", battery?.currentMicroAmps?.let { "${it / 1000} mA" } ?: "--")
            CompactValue("Daya", battery?.powerWatts?.let { "%.1f W".format(it) } ?: "--")
        }
        Spacer(Modifier.height(14.dp))
        RealtimeLineChart(values = state.batteryCurrentHistory)
    }
}

@Composable
private fun CompactValue(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}
