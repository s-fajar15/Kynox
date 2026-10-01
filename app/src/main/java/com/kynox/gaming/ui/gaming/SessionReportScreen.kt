package com.kynox.gaming.ui.gaming

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import android.widget.Toast
import com.kynox.gaming.AppContainer
import com.kynox.gaming.R
import com.kynox.gaming.core.utils.gpuFreqMhz
import com.kynox.gaming.data.gaming.SessionExporter
import com.kynox.gaming.ui.components.MetricRow
import com.kynox.gaming.domain.model.MINOR_DROP_RATIO
import com.kynox.gaming.domain.model.SEVERE_DROP_RATIO
import com.kynox.gaming.domain.model.SessionReport
import com.kynox.gaming.domain.model.SessionSample
import com.kynox.gaming.ui.components.AppIconImage
import com.kynox.gaming.ui.components.HighlightPanel
import com.kynox.gaming.ui.components.ChartPoint
import com.kynox.gaming.ui.components.DetailTopBar
import com.kynox.gaming.ui.components.Readout
import com.kynox.gaming.ui.components.ReportChart
import com.kynox.gaming.ui.components.SectionCard
import com.kynox.gaming.ui.components.captureComposableToBitmap
import com.kynox.gaming.ui.components.exportBitmapToGallery
import com.kynox.gaming.ui.components.formatAxisNumber
import com.kynox.gaming.ui.components.formatAxisTime
import com.kynox.gaming.ui.components.paddedRange
import com.kynox.gaming.ui.components.zeroBasedRange
import com.kynox.gaming.ui.theme.KynoxTheme
import com.kynox.gaming.ui.theme.StatusDanger
import com.kynox.gaming.ui.theme.StatusGood
import com.kynox.gaming.ui.theme.StatusWarning
import kotlin.math.max
import kotlinx.coroutines.launch

private enum class ExportKind { PNG, PDF, CSV }

private val REPORT_BACKGROUND = android.graphics.Color.rgb(0xF8, 0xFA, 0xFC) // matches KynoxSurfaceLight/Background

@Composable
fun SessionReportScreen(container: AppContainer, sessionId: Long, onBack: () -> Unit) {
    var report by remember { mutableStateOf<SessionReport?>(null) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(sessionId) {
        report = container.gameSessionRepository.loadReport(sessionId)
        loaded = true
    }

    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    val savingLabel = stringResource(R.string.report_saving_export)
    val failedLabel = stringResource(R.string.report_export_failed)
    val imageSavedLabel = stringResource(R.string.report_image_saved)
    val pdfSavedLabel = stringResource(R.string.report_pdf_saved)
    val csvSavedLabel = stringResource(R.string.report_csv_saved)
    var menuOpen by remember { mutableStateOf(false) }

    fun export(kind: ExportKind) {
        val toExport = report ?: return
        if (saving) return
        saving = true
        Toast.makeText(context, savingLabel, Toast.LENGTH_SHORT).show()
        scope.launch {
            val prefix = "kynox_session_${toExport.startedAtMs}"
            val ok = try {
                if (kind == ExportKind.CSV) {
                    SessionExporter.exportCsv(context, container.rootExecutor, toExport)
                } else {
                    val widthPx = context.resources.displayMetrics.widthPixels
                    val bitmap = captureComposableToBitmap(view, widthPx, REPORT_BACKGROUND) {
                        SessionFullReportCapture(toExport)
                    }
                    when {
                        bitmap == null -> false
                        kind == ExportKind.PNG -> exportBitmapToGallery(context, container.rootExecutor, bitmap, prefix)
                        else -> SessionExporter.exportPdf(context, container.rootExecutor, bitmap, prefix)
                    }
                }
            } catch (t: Throwable) {
                false
            }
            val message = when {
                !ok -> failedLabel
                kind == ExportKind.PNG -> imageSavedLabel
                kind == ExportKind.PDF -> pdfSavedLabel
                else -> csvSavedLabel
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            saving = false
        }
    }

    Scaffold(
        topBar = {
            DetailTopBar(title = stringResource(R.string.report_title), onBack = onBack) {
                Box {
                    IconButton(enabled = report != null && !saving, onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.report_export))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.report_export_png)) },
                            onClick = { menuOpen = false; export(ExportKind.PNG) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.report_export_pdf)) },
                            onClick = { menuOpen = false; export(ExportKind.PDF) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.report_export_csv)) },
                            onClick = { menuOpen = false; export(ExportKind.CSV) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        val current = report
        if (current == null) {
            if (loaded) {
                Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.report_no_data),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp)
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(28.dp)) {
                        ReportContent(current)
                    }
                }
            }
        }
    }
}

/**
 * The same [ReportContent] the screen scrolls through, rendered once
 * top-to-bottom instead of a page at a time -- this is what "save as
 * image" saves, since a session's charts are the point people want to
 * keep, not just the headline numbers.
 * It wraps its own [KynoxTheme] because the off-screen view it is drawn
 * into for capture starts a fresh composition that does not inherit the
 * real screen's theme.
 */
@Composable
private fun SessionFullReportCapture(report: SessionReport) {
    KynoxTheme(useDarkTheme = false) {
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            ReportContent(report)
        }
    }
}

@Composable
private fun ReportContent(report: SessionReport) {
    val totalMs = max(report.durationMs, report.samples.maxOfOrNull { it.elapsedMs } ?: 0L)

    ReportHeader(report)
    VerdictPanel(report)
    HighlightsSection(report)
    ThermalPerformancePanel(report)

    if (report.fpsSupported) {
        DropMapSection(report, totalMs)
        FpsChartSection(report, totalMs)
        HistogramSection(report)
    }

    ChartSection(
        title = stringResource(R.string.report_chart_cpu_temp),
        side = minAvgMaxLabel(report.minCpuTemp, report.avgCpuTemp, report.maxCpuTemp, "\u00B0C"),
        points = report.samples.points { it.cpuTempCelsius },
        totalMs = totalMs,
        lineColor = StatusWarning
    )
    ChartSection(
        title = stringResource(R.string.report_chart_battery_temp),
        side = minAvgMaxLabel(report.minBatteryTemp, report.avgBatteryTemp, report.maxBatteryTemp, "\u00B0C"),
        points = report.samples.points { it.batteryTempCelsius },
        totalMs = totalMs,
        lineColor = StatusWarning
    )
    ChartSection(
        title = stringResource(R.string.report_chart_power),
        side = report.avgPowerWatts?.let { "%.1f W".format(it) } ?: "--",
        points = report.samples.points { it.powerWatts },
        totalMs = totalMs,
        lineColor = MaterialTheme.colorScheme.primary
    )
    val cpuFreq = report.samples.points { it.avgCpuFreqKhz?.let { khz -> khz / 1000f } }
    ChartSection(
        title = stringResource(R.string.report_chart_cpu_freq),
        side = freqAvgMaxLabel(report.avgCpuFreqMhz, report.maxCpuFreqMhz),
        points = cpuFreq,
        totalMs = totalMs,
        lineColor = MaterialTheme.colorScheme.primary
    )
    val gpuFreq = report.samples.points { it.gpuFreqKhz?.let { raw -> gpuFreqMhz(raw).toFloat() } }
    ChartSection(
        title = stringResource(R.string.report_chart_gpu_freq),
        side = freqAvgMaxLabel(report.avgGpuFreqMhz, report.maxGpuFreqMhz),
        points = gpuFreq,
        totalMs = totalMs,
        lineColor = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun ReportHeader(report: SessionReport) {
    val subtitle = listOfNotNull(
        formatDateTime(report.startedAtMs),
        formatDuration(report.durationMs),
        report.resolution
    ).joinToString(" \u00B7 ")
    Row(verticalAlignment = Alignment.CenterVertically) {
        AppIconImage(report.packageName, 52.dp)
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(
                report.gameLabel,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** The moments worth remembering from a session: hottest points, worst frame rate, and what it cost in battery. */
@Composable
private fun HighlightsSection(report: SessionReport) {
    val rows = mutableListOf<Pair<String, String>>()
    val peakCpu = report.maxCpuTemp
    if (peakCpu != null) {
        val at = report.peakCpuTempAtMs?.let { formatAxisTime(it) }
        rows += stringResource(R.string.report_peak_cpu_temp) to
            (if (at != null) stringResource(R.string.report_value_at_time, "%.1f\u00B0C".format(peakCpu), at) else "%.1f\u00B0C".format(peakCpu))
    }
    val peakBattery = report.maxBatteryTemp
    if (peakBattery != null) {
        val at = report.peakBatteryTempAtMs?.let { formatAxisTime(it) }
        rows += stringResource(R.string.report_peak_battery_temp) to
            (if (at != null) stringResource(R.string.report_value_at_time, "%.1f\u00B0C".format(peakBattery), at) else "%.1f\u00B0C".format(peakBattery))
    }
    val lowestFps = report.minFps
    if (report.fpsSupported && lowestFps != null) {
        val at = report.lowestFpsAtMs?.let { formatAxisTime(it) }
        rows += stringResource(R.string.report_lowest_fps) to
            (if (at != null) stringResource(R.string.report_value_at_time, "%.0f FPS".format(lowestFps), at) else "%.0f FPS".format(lowestFps))
    }
    val startBattery = report.startBatteryPercent
    val endBattery = report.endBatteryPercent
    if (startBattery != null && endBattery != null) {
        rows += stringResource(R.string.report_battery_used) to "$startBattery% \u2192 $endBattery%"
    }
    report.drainPercentPerHour?.let { rows += stringResource(R.string.report_drain_rate) to "%.1f %%/jam".format(it) }
    report.drainMahPerHour?.let { rows += stringResource(R.string.report_drain_rate_mah) to "%.0f mAh/jam".format(it) }
    if (rows.isEmpty()) return

    SectionCard(title = stringResource(R.string.report_highlights_title)) {
        rows.forEach { (label, value) -> MetricRow(label, value) }
    }
}

/** The one-glance answer: how fast, how steady, and a plain sentence about it. */
@Composable
private fun VerdictPanel(report: SessionReport) {
    val fpsValues = report.samples.mapNotNull { it.fps }
    val avg = report.avgFps
    val stable = if (avg != null && fpsValues.isNotEmpty()) {
        fpsValues.count { it >= avg * MINOR_DROP_RATIO } * 100f / fpsValues.size
    } else null

    HighlightPanel {
        if (!report.fpsSupported || avg == null) {
            Text(
                stringResource(R.string.report_fps_not_supported),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            val stableColor = when {
                stable == null -> MaterialTheme.colorScheme.onSurfaceVariant
                stable >= 95f -> StatusGood
                stable >= 80f -> StatusWarning
                else -> StatusDanger
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Readout(
                    value = "%.0f".format(avg),
                    unit = "FPS",
                    label = stringResource(R.string.report_stat_avg),
                    valueStyle = MaterialTheme.typography.displayLarge,
                    valueColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                Readout(
                    value = stable?.let { "%.0f".format(it) } ?: "--",
                    unit = "%",
                    label = stringResource(R.string.report_stat_stable),
                    valueColor = stableColor
                )
            }
            if (stable != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(
                        when {
                            stable >= 95f -> R.string.report_verdict_great
                            stable >= 80f -> R.string.report_verdict_ok
                            else -> R.string.report_verdict_bad
                        }
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outline))
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth()) {
                StatColumn(stringResource(R.string.report_stat_max), report.maxFps, Modifier.weight(1f))
                VerticalRule()
                StatColumn(stringResource(R.string.report_stat_min), report.minFps, Modifier.weight(1f))
                VerticalRule()
                StatColumn(stringResource(R.string.report_stat_low5), report.low5PercentFps, Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.report_min_fps_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun VerticalRule() {
    Box(Modifier.width(1.dp).height(44.dp).background(MaterialTheme.colorScheme.outline))
}

/** Suhu CPU/baterai (min/avg/maks) plus frekuensi & daya rata-rata -- everything the individual charts below show, at a glance. */
@Composable
private fun ThermalPerformancePanel(report: SessionReport) {
    val hasCpuTemp = report.avgCpuTemp != null
    val hasBatteryTemp = report.avgBatteryTemp != null
    val hasFreqOrPower = report.avgCpuFreqMhz != null || report.avgGpuFreqMhz != null || report.avgPowerWatts != null
    if (!hasCpuTemp && !hasBatteryTemp && !hasFreqOrPower) return

    SectionCard(title = stringResource(R.string.report_thermal_panel_title)) {
        if (hasCpuTemp) {
            GroupLabel(stringResource(R.string.report_group_cpu_temp))
            Row(Modifier.fillMaxWidth()) {
                StatColumn(stringResource(R.string.report_stat_min), report.minCpuTemp, Modifier.weight(1f))
                VerticalRule()
                StatColumn(stringResource(R.string.report_stat_avg), report.avgCpuTemp, Modifier.weight(1f))
                VerticalRule()
                StatColumn(stringResource(R.string.report_stat_max), report.maxCpuTemp, Modifier.weight(1f))
            }
            Spacer(Modifier.height(16.dp))
        }
        if (hasBatteryTemp) {
            GroupLabel(stringResource(R.string.report_group_battery_temp))
            Row(Modifier.fillMaxWidth()) {
                StatColumn(stringResource(R.string.report_stat_min), report.minBatteryTemp, Modifier.weight(1f))
                VerticalRule()
                StatColumn(stringResource(R.string.report_stat_avg), report.avgBatteryTemp, Modifier.weight(1f))
                VerticalRule()
                StatColumn(stringResource(R.string.report_stat_max), report.maxBatteryTemp, Modifier.weight(1f))
            }
            Spacer(Modifier.height(16.dp))
        }
        if (hasFreqOrPower) {
            GroupLabel(stringResource(R.string.report_group_performance))
            Row(Modifier.fillMaxWidth()) {
                StatColumnText(
                    stringResource(R.string.report_stat_cpu_freq),
                    report.avgCpuFreqMhz?.let { "%.0f MHz".format(it) } ?: "--",
                    Modifier.weight(1f)
                )
                VerticalRule()
                StatColumnText(
                    stringResource(R.string.report_stat_gpu_freq),
                    report.avgGpuFreqMhz?.let { "%.0f MHz".format(it) } ?: "--",
                    Modifier.weight(1f)
                )
                VerticalRule()
                StatColumnText(
                    stringResource(R.string.report_stat_power),
                    report.avgPowerWatts?.let { "%.1f W".format(it) } ?: "--",
                    Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun StatColumn(label: String, value: Float?, modifier: Modifier = Modifier) {
    StatColumnText(label, value?.let { "%.1f".format(it) } ?: "--", modifier)
}

@Composable
private fun StatColumnText(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 12.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Text(
            value,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/** Every second of the session as one slice of a strip, so drops show up where they happened. */
@Composable
private fun DropMapSection(report: SessionReport, totalMs: Long) {
    val avg = report.avgFps ?: return
    SectionCard(title = stringResource(R.string.report_map_title)) {
        DropStrip(report.samples, avg, totalMs, Modifier.fillMaxWidth().height(18.dp))
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            AxisText(formatAxisTime(0L))
            AxisText(formatAxisTime(totalMs / 2))
            AxisText(formatAxisTime(totalMs))
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
            LegendDot(StatusGood, stringResource(R.string.report_legend_stable))
            LegendDot(StatusWarning, stringResource(R.string.report_legend_minor))
            LegendDot(StatusDanger, stringResource(R.string.report_legend_severe))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.report_drops_summary, report.minorDropCount, report.severeDropCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DropStrip(samples: List<SessionSample>, avg: Float, totalMs: Long, modifier: Modifier = Modifier) {
    val buckets = 90
    val worst = remember(samples, totalMs) {
        val slices = FloatArray(buckets) { Float.NaN }
        val span = if (totalMs <= 0L) 1L else totalMs
        samples.forEach { sample ->
            val fps = sample.fps ?: return@forEach
            val index = ((sample.elapsedMs.toDouble() / span) * buckets).toInt().coerceIn(0, buckets - 1)
            if (slices[index].isNaN() || fps < slices[index]) slices[index] = fps
        }
        slices
    }
    val emptyColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    Canvas(modifier) {
        val slot = size.width / buckets
        for (i in 0 until buckets) {
            val value = worst[i]
            val color = when {
                value.isNaN() -> emptyColor
                value < avg * SEVERE_DROP_RATIO -> StatusDanger
                value < avg * MINOR_DROP_RATIO -> StatusWarning
                else -> StatusGood
            }
            drawRect(color, topLeft = Offset(i * slot, 0f), size = Size(slot * 0.8f, size.height))
        }
    }
}

@Composable
private fun FpsChartSection(report: SessionReport, totalMs: Long) {
    val points = report.samples.points { it.fps }
    if (points.size < 2) return
    val avg = report.avgFps ?: 0f
    val severe = avg * SEVERE_DROP_RATIO
    val minor = avg * MINOR_DROP_RATIO
    val (low, high) = zeroBasedRange(points.map { it.y })
    SectionCard(title = stringResource(R.string.report_chart_fps)) {
        ReportChart(
            points = points,
            totalMs = totalMs,
            yMin = low,
            yMax = high,
            segmentColor = { fps ->
                when {
                    fps < severe -> StatusDanger
                    fps < minor -> StatusWarning
                    else -> StatusGood
                }
            },
            yFormat = { "%.0f".format(it) },
            height = 170.dp
        )
    }
}

/** How the session's time was spread across FPS values, in 10 FPS steps. */
@Composable
private fun HistogramSection(report: SessionReport) {
    val values = report.samples.mapNotNull { it.fps }
    if (values.size < 2) return
    val avg = report.avgFps ?: 0f
    val step = 10
    val maxBin = (values.maxOrNull() ?: 0f).toInt() / step
    val counts = remember(values) {
        val bins = IntArray(maxBin + 1)
        values.forEach { bins[(it.toInt() / step).coerceIn(0, maxBin)]++ }
        bins
    }
    val peak = (counts.maxOrNull() ?: 1).coerceAtLeast(1)
    val modeBin = counts.indices.maxByOrNull { counts[it] } ?: 0
    val axisColor = MaterialTheme.colorScheme.outline

    SectionCard(title = stringResource(R.string.report_hist_title)) {
        Canvas(Modifier.fillMaxWidth().height(96.dp)) {
            val slot = size.width / counts.size
            val bar = slot * 0.72f
            counts.forEachIndexed { bin, count ->
                val barHeight = size.height * count / peak
                val mid = bin * step + step / 2f
                val color = when {
                    mid < avg * SEVERE_DROP_RATIO -> StatusDanger
                    mid < avg * MINOR_DROP_RATIO -> StatusWarning
                    else -> StatusGood
                }
                drawRect(color, topLeft = Offset(bin * slot + (slot - bar) / 2f, size.height - barHeight), size = Size(bar, barHeight))
            }
            drawLine(axisColor, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            AxisText("0")
            AxisText(((maxBin + 1) * step / 2).toString())
            AxisText(((maxBin + 1) * step).toString())
        }
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.report_histogram_mode, modeBin * step, (modeBin + 1) * step, counts[modeBin] * 100 / values.size),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ChartSection(title: String, side: String, points: List<ChartPoint>, totalMs: Long, lineColor: Color) {
    if (points.size < 2) return
    val (low, high) = paddedRange(points.map { it.y })
    SectionCard(
        title = title,
        trailing = {
            Text(side, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    ) {
        ReportChart(points, totalMs, low, high, lineColor = lineColor, yFormat = { formatAxisNumber(it) })
    }
}

@Composable
private fun AxisText(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 6.dp)
        )
    }
}

private fun avgMaxLabel(avg: Float?, max: Float?, unit: String): String {
    if (avg == null || max == null) return "--"
    return "%.1f%s / %.1f%s".format(avg, unit, max, unit)
}

private fun minAvgMaxLabel(min: Float?, avg: Float?, max: Float?, unit: String): String {
    if (min == null || avg == null || max == null) return "--"
    return "%.1f%s / %.1f%s / %.1f%s".format(min, unit, avg, unit, max, unit)
}

private fun freqAvgMaxLabel(avg: Float?, max: Float?): String {
    if (avg == null || max == null) return "--"
    return "%.0f / %.0f MHz".format(avg, max)
}

private fun List<SessionSample>.points(selector: (SessionSample) -> Float?): List<ChartPoint> =
    mapNotNull { sample -> selector(sample)?.let { ChartPoint(sample.elapsedMs, it) } }
