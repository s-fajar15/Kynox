package com.kynox.gaming.ui.dashboard

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
import com.kynox.gaming.ui.theme.StatusGood
import com.kynox.gaming.ui.theme.StatusWarning
import com.kynox.gaming.ui.theme.kynoxColors
import androidx.compose.ui.res.stringResource

@Composable
fun DashboardScreen(container: AppContainer) {
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
            subtitle = "Pantau perangkat, di tanganmu.",
            leading = { KMark(markSize = 25.dp) },
            actions = {
                val root = state.rootStatus?.isAvailable == true
                StatusPill(if (root) "Akses root" else "Mode terbatas", if (root) StatusGood else StatusWarning)
            }
        )
    }) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { KLoadingIndicator(markSize = 36.dp) }
            return@Scaffold
        }

        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item { SystemHero(state) }
            item { MetricGrid(state) }
            item { HealthSummary(state) }
        }
    }
}

@Composable
private fun SystemHero(state: DashboardUiState) {
    val temp = state.cpuTempCelsius ?: state.gpuTempCelsius
    val tempColor = KynoxStatusBands.tempColor(temp, KynoxAccentDark)
    val healthy = temp == null || temp < KynoxStatusBands.TEMP_WARN
    Box(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF0D2629), MaterialTheme.colorScheme.surface)))
            .border(1.dp, KynoxAccentDark.copy(alpha = 0.24f), RoundedCornerShape(22.dp))
            .padding(17.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Suhu CPU", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(temp?.let { "%.0f".format(it) } ?: "--", style = MaterialTheme.typography.displaySmall, color = tempColor, fontWeight = FontWeight.SemiBold)
                        Text("°C", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 5.dp, start = 3.dp))
                    }
                    Text(if (healthy) "Normal · pemantauan aktif" else "Beban tinggi · perhatikan suhu", style = MaterialTheme.typography.labelSmall, color = if (healthy) StatusGood else StatusWarning)
                }
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(KynoxAccentDark.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
                    Icon(KynoxIcons.Cpu, null, tint = KynoxAccentDark, modifier = Modifier.size(24.dp))
                }
            }
            Spacer(Modifier.height(13.dp))
            RealtimeLineChart(values = state.cpuUsageHistory, modifier = Modifier.fillMaxWidth().height(42.dp))
        }
    }
}

@Composable
private fun MetricGrid(state: DashboardUiState) {
    val cpu = state.cpu?.overallUsagePercent
    val gpu = state.gpu?.utilizationPercent
    val ram = state.ram?.usedPercent
    val battery = state.battery?.capacityPercent
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricTile("CPU", cpu?.let { "%.0f%%".format(it) } ?: "--", KynoxIcons.Cpu, Modifier.weight(1f))
            MetricTile("GPU", gpu?.let { "%.0f%%".format(it) } ?: "--", KynoxIcons.Gpu, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricTile("RAM", state.ram?.let { "%.1f GB".format(it.usedBytes / 1073741824f) } ?: "--", KynoxIcons.Ram, Modifier.weight(1f))
            MetricTile("Baterai", battery?.let { "$it%" } ?: "--", KynoxIcons.Battery, Modifier.weight(1f))
        }
    }
}

@Composable
private fun MetricTile(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(19.dp)).background(MaterialTheme.colorScheme.surface).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(19.dp)).padding(15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(KynoxAccentDark.copy(alpha = 0.10f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = KynoxAccentDark, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun HealthSummary(state: DashboardUiState) {
    val root = state.rootStatus?.isAvailable == true
    SectionCard("Status sistem", trailing = { Text(if (root) "Siap" else "Terbatas", color = if (root) StatusGood else StatusWarning, style = MaterialTheme.typography.labelMedium) }) {
        StatusLine("Thermal engine", if (state.thermalEngineActive) "Aktif" else "Nonaktif", state.thermalEngineActive)
        StatusLine("GPU telemetry", if (state.gpu?.supported == true) "Tersedia" else "Terbatas", state.gpu?.supported == true)
        StatusLine("Root access", if (root) "Tersedia" else "Tidak tersedia", root)
    }
}

@Composable
private fun StatusLine(label: String, value: String, good: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(if (good) StatusGood else StatusWarning))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(start = 10.dp))
        Text(value, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
