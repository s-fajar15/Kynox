package com.kynox.gaming.ui.monitor

import com.kynox.gaming.ui.components.Sparkline
import com.kynox.gaming.ui.components.KynoxGauge
import com.kynox.gaming.ui.components.kynoxCard
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.semantics.Role
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
import com.kynox.gaming.ui.theme.KynoxStatusBands
import com.kynox.gaming.ui.theme.KynoxBrushes
import com.kynox.gaming.ui.theme.KynoxShapes
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

    Scaffold(topBar = { KynoxTopBar(title = "Performa", subtitle = "Pantau kinerja perangkat secara real-time.") }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
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
            item {
                StoredHistoryCard(container, selectedTab, if (MetricTab.entries[selectedTab] == MetricTab.BATTERY) " W" else "%")
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
        Modifier.fillMaxWidth().kynoxCard(KynoxShapes.pill).padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        MetricTab.entries.forEachIndexed { index, tab ->
            if (tab == MetricTab.GPU && !gpuSupported) return@forEachIndexed
            val active = index == selected
            Box(
                Modifier.weight(1f).clip(KynoxShapes.pill)
                    .then(if (active) Modifier.background(KynoxBrushes.accent) else Modifier)
                    .heightIn(min = 48.dp)
                    .clickable(role = Role.Tab) { onSelected(index) }
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    tab.label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (active) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun metricValue(s: MonitorSample, tab: MetricTab): Float? = when (tab) {
    MetricTab.CPU -> s.cpuUsage
    MetricTab.GPU -> s.gpuUsage
    MetricTab.RAM -> s.ramUsage
    MetricTab.BATTERY -> s.powerWatts
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
    val wave = state.samples.takeLast(60).mapNotNull { metricValue(it, tab) }
    val centerText = if (tab == MetricTab.BATTERY) {
        sample?.powerWatts?.let { "%.1f W".format(it) } ?: "--"
    } else "${value.toInt()}%"
    val caption = if (tab == MetricTab.BATTERY) "Daya (skala 0\u20138 W)" else label
    Column(
        Modifier.fillMaxWidth().kynoxCard(KynoxShapes.hero).padding(horizontal = 18.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        KynoxGauge(
            progress = value / 100f,
            modifier = Modifier.size(212.dp),
            strokeWidth = 16.dp,
            color = KynoxStatusBands.loadColor(if (tab == MetricTab.BATTERY) null else value, MaterialTheme.colorScheme.primary)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(centerText, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
                Text(caption, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(14.dp))
        Sparkline(wave, Modifier.fillMaxWidth().height(56.dp))
    }
}

@Composable
private fun MiniMetrics(state: MonitorUiState, tab: MetricTab) {
    val s = state.samples.lastOrNull()
    val values = when (tab) {
        MetricTab.CPU -> listOf("Frekuensi" to (s?.cpuFreqMhz?.let { "%.2f GHz".format(it / 1000f) } ?: "--"), "Suhu" to (s?.cpuTemp?.let { "%.0f°C".format(it) } ?: "--"), "Inti Aktif" to activeCores(), "Arsitektur" to architectureLabel())
        MetricTab.GPU -> listOf("Frekuensi" to (s?.gpuFreqMhz?.let { "%.0f MHz".format(it) } ?: "--"), "Beban GPU" to (s?.gpuUsage?.let { "%.0f%%".format(it) } ?: "--"), "Beban CPU" to (s?.cpuUsage?.let { "%.0f%%".format(it) } ?: "--"), "Suhu CPU" to (s?.cpuTemp?.let { "%.1f°C".format(it) } ?: "--"))
        MetricTab.RAM -> listOf("Terpakai" to (s?.ramUsage?.let { "%.0f%%".format(it) } ?: "--"), "CPU" to (s?.cpuUsage?.let { "%.0f%%".format(it) } ?: "--"), "Daya" to (s?.powerWatts?.let { "%.1f W".format(it) } ?: "--"), "Suhu CPU" to (s?.cpuTemp?.let { "%.0f°C".format(it) } ?: "--"))
        MetricTab.BATTERY -> listOf("Arus" to (s?.currentMa?.let { "%.0f mA".format(it) } ?: "--"), "Daya" to (s?.powerWatts?.let { "%.1f W".format(it) } ?: "--"), "Suhu" to (s?.batteryTemp?.let { "%.1f°C".format(it) } ?: "--"), "Beban CPU" to (s?.cpuUsage?.let { "%.0f%%".format(it) } ?: "--"))
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MiniMetric(values[0], Modifier.weight(1f))
            MiniMetric(values[1], Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MiniMetric(values[2], Modifier.weight(1f))
            MiniMetric(values[3], Modifier.weight(1f))
        }
    }
}

private fun parseCpuRange(text: String?): Int? {
    if (text.isNullOrBlank()) return null
    var count = 0
    for (part in text.trim().split(',')) {
        val bounds = part.trim().split('-')
        val lo = bounds.firstOrNull()?.toIntOrNull() ?: return null
        val hi = bounds.getOrNull(1)?.toIntOrNull() ?: lo
        count += hi - lo + 1
    }
    return count
}

private fun activeCores(): String {
    val online = try { parseCpuRange(java.io.File("/sys/devices/system/cpu/online").readText()) } catch (_: Throwable) { null }
        ?: Runtime.getRuntime().availableProcessors()
    val total = try { parseCpuRange(java.io.File("/sys/devices/system/cpu/possible").readText()) } catch (_: Throwable) { null }
        ?: online
    return "$online / $total"
}

private fun architectureLabel(): String = when (val abi = android.os.Build.SUPPORTED_ABIS.firstOrNull().orEmpty()) {
    "arm64-v8a" -> "ARM64"
    "armeabi-v7a" -> "ARM32"
    "" -> "--"
    else -> abi
}

@Composable
private fun MiniMetric(value: Pair<String, String>, modifier: Modifier) {
    Column(modifier.kynoxCard(KynoxShapes.section).padding(16.dp)) {
        Text(value.first, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(value.second, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
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
        "Riwayat Penggunaan",
        trailing = {
            Text(
                if (state.paused) "Lanjutkan" else "Jeda",
                style = MaterialTheme.typography.labelMedium,
                color = if (state.paused) StatusWarning else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button) { viewModel.togglePause() }.padding(horizontal = 10.dp, vertical = 14.dp)
            )
        }
    ) {
        if (points.size >= 2) {
            ReportChart(points = points, totalMs = windowMs, yMin = low, yMax = high, lineColor = KynoxAccentDark, height = 150.dp, xLabels = listOf("-${formatAxisTime(windowMs)}", "-${formatAxisTime(windowMs / 2)}", "sekarang"))
        } else {
            Text("Mengumpulkan data…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            listOf(60 to "1m", 300 to "5m", 900 to "15m", 1800 to "30m").forEach { (seconds, label) ->
                val active = seconds == state.windowSeconds
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(8.dp)).clickable(role = Role.Tab) { viewModel.setWindow(seconds) }.padding(vertical = 14.dp, horizontal = 4.dp)
                )
            }
        }
    }
}
