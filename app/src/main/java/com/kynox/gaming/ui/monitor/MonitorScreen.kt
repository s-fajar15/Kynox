package com.kynox.gaming.ui.monitor

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kynox.gaming.AppContainer
import com.kynox.gaming.R
import com.kynox.gaming.domain.model.MonitorSample
import com.kynox.gaming.ui.components.ChartPoint
import com.kynox.gaming.ui.components.GenericViewModelFactory
import com.kynox.gaming.ui.components.KynoxTopBar
import com.kynox.gaming.ui.components.KButton
import com.kynox.gaming.ui.components.KOutlinedButton
import com.kynox.gaming.ui.components.ReportChart
import com.kynox.gaming.ui.components.SectionCard
import com.kynox.gaming.ui.components.formatAxisTime
import com.kynox.gaming.ui.components.paddedRange
import com.kynox.gaming.ui.components.zeroBasedRange
import com.kynox.gaming.ui.theme.KynoxAccentDark
import com.kynox.gaming.ui.theme.KynoxIcons
import com.kynox.gaming.ui.theme.StatusWarning
import com.kynox.gaming.ui.theme.kynoxColors
import com.kynox.gaming.service.MonitorService
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource

private enum class MetricTab(val label: String) { CPU("CPU"), GPU("GPU"), RAM("RAM"), BATTERY("Baterai") }

@Composable
fun MonitorScreen(container: AppContainer) {
    val viewModel: MonitorViewModel = viewModel(factory = GenericViewModelFactory { MonitorViewModel(container.monitorRecorder) })
    val state by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Scaffold(topBar = { KynoxTopBar(title = "Performa") }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 74.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "Pantau kinerja perangkat secara real-time.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
            if (!state.recording) {
                item { MonitorOffCard { scope.launch { container.settingsRepository.setMonitorEnabled(true) }; MonitorService.start(context) } }
            }
            item {
                MetricTabs(selectedTab, state.gpuSupported) { selectedTab = it }
            }
            item {
                PerformanceHero(state, MetricTab.entries[selectedTab])
            }
            item {
                MiniMetrics(state, MetricTab.entries[selectedTab])
            }
            item {
                HistoryCard(state, MetricTab.entries[selectedTab], viewModel)
            }
        }
    }
}

@Composable
private fun MonitorOffCard(onEnable: () -> Unit) {
    SectionCard("Monitor tidak aktif") {
        Text("Aktifkan monitor agar grafik dan statistik terus diperbarui di latar belakang.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        KButton(onClick = onEnable) { Text("Aktifkan monitor") }
    }
}

@Composable
private fun MetricTabs(selected: Int, gpuSupported: Boolean, onSelected: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.kynoxColors.surfaceSunken).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        MetricTab.entries.forEachIndexed { index, tab ->
            if (tab == MetricTab.GPU && !gpuSupported) return@forEachIndexed
            val active = index == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                    .background(if (active) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable { onSelected(index) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(tab.label, style = MaterialTheme.typography.labelMedium, color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PerformanceHero(state: MonitorUiState, tab: MetricTab) {
    val sample = state.samples.lastOrNull()
    val value = when (tab) {
        MetricTab.CPU -> sample?.cpuUsage
        MetricTab.GPU -> sample?.gpuUsage
        MetricTab.RAM -> sample?.ramUsage
        MetricTab.BATTERY -> sample?.powerWatts?.let { (it / 8f * 100f).coerceIn(0f, 100f) }
    } ?: 0f
    val label = when (tab) {
        MetricTab.CPU -> "CPU Usage"
        MetricTab.GPU -> "GPU Usage"
        MetricTab.RAM -> "RAM Usage"
        MetricTab.BATTERY -> "Daya"
    }
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .75f), RoundedCornerShape(24.dp)).padding(22.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(156.dp)) {
                CircularProgressIndicator(progress = (value / 100f).coerceIn(0f, 1f), modifier = Modifier.fillMaxSize(), strokeWidth = 10.dp, color = KynoxAccentDark, trackColor = MaterialTheme.kynoxColors.surfaceSunken)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${value.toInt()}%", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(14.dp))
            Text("Aktivitas saat ini", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MiniMetrics(state: MonitorUiState, tab: MetricTab) {
    val s = state.samples.lastOrNull()
    val values = when (tab) {
        MetricTab.CPU -> listOf("Frekuensi" to (s?.cpuFreqMhz?.let { "%.0f MHz".format(it) } ?: "--"), "Suhu" to (s?.cpuTemp?.let { "%.1f°C".format(it) } ?: "--"), "RAM" to (s?.ramUsage?.let { "%.0f%%".format(it) } ?: "--"), "Status" to "Aktif")
        MetricTab.GPU -> listOf("Frekuensi" to (s?.gpuFreqMhz?.let { "%.0f MHz".format(it) } ?: "--"), "Suhu" to (s?.cpuTemp?.let { "%.1f°C".format(it) } ?: "--"), "CPU" to (s?.cpuUsage?.let { "%.0f%%".format(it) } ?: "--"), "Status" to "Aktif")
        MetricTab.RAM -> listOf("Terpakai" to (s?.ramUsage?.let { "%.0f%%".format(it) } ?: "--"), "CPU" to (s?.cpuUsage?.let { "%.0f%%".format(it) } ?: "--"), "Daya" to (s?.powerWatts?.let { "%.1f W".format(it) } ?: "--"), "Status" to "Aktif")
        MetricTab.BATTERY -> listOf("Arus" to (s?.currentMa?.let { "%.0f mA".format(it) } ?: "--"), "Daya" to (s?.powerWatts?.let { "%.1f W".format(it) } ?: "--"), "Suhu" to (s?.batteryTemp?.let { "%.1f°C".format(it) } ?: "--"), "Status" to "Aktif")
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniMetric(values[0], Modifier.weight(1f))
            MiniMetric(values[1], Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniMetric(values[2], Modifier.weight(1f))
            MiniMetric(values[3], Modifier.weight(1f))
        }
    }
}

@Composable
private fun MiniMetric(value: Pair<String, String>, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .7f), RoundedCornerShape(16.dp)).padding(13.dp)) {
        Text(value.first, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(3.dp))
        Text(value.second, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun HistoryCard(state: MonitorUiState, tab: MetricTab, viewModel: MonitorViewModel) {
    val windowMs = state.windowSeconds * 1000L
    val last = state.samples.lastOrNull()?.tMs ?: 0L
    val start = last - windowMs
    val samples = state.samples.filter { it.tMs >= start }
    val points = samples.mapNotNull { s ->
        val value = when (tab) {
            MetricTab.CPU -> s.cpuUsage
            MetricTab.GPU -> s.gpuUsage
            MetricTab.RAM -> s.ramUsage
            MetricTab.BATTERY -> s.powerWatts
        }
        value?.let { ChartPoint(s.tMs - start, it) }
    }
    val values = points.map { it.y }
    val (low, high) = when (tab) {
        MetricTab.CPU, MetricTab.GPU, MetricTab.RAM -> 0f to 100f
        MetricTab.BATTERY -> zeroBasedRange(values)
    }
    SectionCard(
        "Riwayat penggunaan",
        trailing = { Text(if (state.paused) "Dijeda" else "Live", style = MaterialTheme.typography.labelSmall, color = if (state.paused) StatusWarning else KynoxAccentDark) }
    ) {
        if (points.size >= 2) {
            ReportChart(points = points, totalMs = windowMs, yMin = low, yMax = high, lineColor = KynoxAccentDark, height = 150.dp, xLabels = listOf("-${formatAxisTime(windowMs)}", "-${formatAxisTime(windowMs / 2)}", "sekarang"))
        } else {
            Text("Mengumpulkan data…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(30 to "30s", 60 to "1m", 300 to "5m", 1800 to "30m").forEach { (seconds, label) ->
                val active = seconds == state.windowSeconds
                Box(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.kynoxColors.surfaceSunken).clickable { viewModel.setWindow(seconds) }.padding(vertical = 7.dp), contentAlignment = Alignment.Center) {
                    Text(label, style = MaterialTheme.typography.labelSmall, color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        KOutlinedButton(onClick = { viewModel.togglePause() }, modifier = Modifier.fillMaxWidth()) { Text(if (state.paused) "Lanjutkan" else "Jeda pemantauan") }
    }
}
