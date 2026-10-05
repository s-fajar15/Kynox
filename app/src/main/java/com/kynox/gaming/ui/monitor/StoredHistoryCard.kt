package com.kynox.gaming.ui.monitor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kynox.gaming.AppContainer
import com.kynox.gaming.data.monitor.HistoryStats
import com.kynox.gaming.data.monitor.MonitorHistoryCodec
import com.kynox.gaming.data.monitor.MonitorHistoryStore
import com.kynox.gaming.data.settings.AppSettings
import com.kynox.gaming.domain.model.MonitorSample
import com.kynox.gaming.ui.components.ChartPoint
import com.kynox.gaming.ui.components.ConfirmDialog
import com.kynox.gaming.ui.components.GenericViewModelFactory
import com.kynox.gaming.ui.components.KOutlinedButton
import com.kynox.gaming.ui.components.ReportChart
import com.kynox.gaming.ui.components.SectionCard
import com.kynox.gaming.ui.components.zeroBasedRange
import com.kynox.gaming.ui.theme.KynoxAccentDark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val MAX_CHART_POINTS = 240

data class StoredHistoryState(
    val rangeHours: Int = 1,
    val samples: List<MonitorSample> = emptyList(),
    val stats: HistoryStats = HistoryStats(0, 0L, null),
    val loading: Boolean = true
)

/** Membaca riwayat tersimpan dari disk di thread IO; layar tidak pernah membaca berkas langsung. */
class StoredHistoryViewModel(private val store: MonitorHistoryStore) : ViewModel() {
    private val _state = MutableStateFlow(StoredHistoryState())
    val state: StateFlow<StoredHistoryState> = _state.asStateFlow()

    fun load(rangeHours: Int) {
        _state.value = _state.value.copy(rangeHours = rangeHours, loading = true)
        viewModelScope.launch(Dispatchers.IO) {
            val since = System.currentTimeMillis() - rangeHours * 3_600_000L
            val samples = store.read(since)
            val stats = store.stats()
            _state.value = StoredHistoryState(rangeHours, samples, stats, loading = false)
        }
    }

    fun clear() {
        viewModelScope.launch(Dispatchers.IO) {
            store.clear()
            _state.value = StoredHistoryState(_state.value.rangeHours, emptyList(), HistoryStats(0, 0L, null), loading = false)
        }
    }
}

private fun value(s: MonitorSample, tab: Int): Float? = when (tab) {
    0 -> s.cpuUsage
    1 -> s.gpuUsage
    2 -> s.ramUsage
    else -> s.powerWatts
}

/** Ringkasan min / rata-rata / maks dari semua sampel di rentang, bukan hanya titik yang digambar. */
internal fun summarize(values: List<Float>): Triple<Float, Float, Float>? {
    if (values.isEmpty()) return null
    return Triple(values.min(), values.average().toFloat(), values.max())
}

@Composable
internal fun StoredHistoryCard(container: AppContainer, tabIndex: Int, unit: String) {
    val vm: StoredHistoryViewModel = viewModel(factory = GenericViewModelFactory { StoredHistoryViewModel(container.monitorRecorder.history) })
    val state by vm.state.collectAsState()
    val settings by container.settingsRepository.settingsFlow.collectAsState(initial = AppSettings())
    var confirmClear by remember { mutableStateOf(false) }
    val ranges = listOf(1 to "1j", 6 to "6j", 24 to "24j", 72 to "72j")
        .filter { it.first <= settings.historyRetentionHours || it.first == 1 }

    LaunchedEffect(Unit) { vm.load(state.rangeHours) }

    if (confirmClear) {
        ConfirmDialog(
            title = "Hapus riwayat tersimpan?",
            message = "Semua sampel yang tersimpan di perangkat akan dihapus. Grafik langsung 30 menit terakhir tidak terpengaruh.",
            onConfirm = { vm.clear(); confirmClear = false },
            onDismiss = { confirmClear = false }
        )
    }

    SectionCard("Riwayat Tersimpan") {
        if (!settings.historyEnabled) {
            Text(
                "Penyimpanan riwayat dimatikan. Aktifkan di Pengaturan › Riwayat monitor.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ranges.forEach { (hours, label) ->
                val active = hours == state.rangeHours
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(role = Role.Tab) { vm.load(hours) }
                        .padding(horizontal = 12.dp, vertical = 12.dp)
                )
            }
        }

        val values = state.samples.mapNotNull { value(it, tabIndex) }
        val summary = summarize(values)
        if (state.loading) {
            Text("Memuat…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else if (summary == null || state.samples.size < 2) {
            Text(
                "Belum ada data di rentang ini. Sampel tersimpan setiap ${settings.historyIntervalSec} detik selama monitor aktif.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            val shown = MonitorHistoryCodec.evenlySpaced(state.samples, MAX_CHART_POINTS)
            val start = shown.first().tMs
            val total = (shown.last().tMs - start).coerceAtLeast(1L)
            val points = shown.mapNotNull { s -> value(s, tabIndex)?.let { ChartPoint(s.tMs - start, it) } }
            val (low, high) = if (tabIndex == 3) zeroBasedRange(points.map { it.y }) else 0f to 100f
            ReportChart(
                points = points, totalMs = total, yMin = low, yMax = high,
                lineColor = KynoxAccentDark, height = 140.dp,
                xLabels = listOf(clock(shown.first().tMs), clock(shown[shown.size / 2].tMs), clock(shown.last().tMs))
            )
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Stat("Min", "%.1f%s".format(summary.first, unit))
                Stat("Rata-rata", "%.1f%s".format(summary.second, unit))
                Stat("Maks", "%.1f%s".format(summary.third, unit))
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "${state.stats.count} sampel · ${formatBytes(state.stats.sizeBytes)}" +
                (state.stats.oldestMs?.let { " · sejak ${dateTime(it)}" } ?: ""),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))
        KOutlinedButton(onClick = { confirmClear = true }, enabled = state.stats.count > 0) { Text("Hapus riwayat") }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}

private fun clock(ms: Long): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))
private fun dateTime(ms: Long): String = SimpleDateFormat("d MMM HH:mm", Locale("id", "ID")).format(Date(ms))

internal fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.0f KB".format(bytes / 1024f)
    else -> "%.1f MB".format(bytes / 1024f / 1024f)
}
