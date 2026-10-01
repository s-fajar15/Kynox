package com.kynox.gaming.ui.gaming

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kynox.gaming.ui.components.KTextButton
import com.kynox.gaming.AppContainer
import com.kynox.gaming.R
import com.kynox.gaming.domain.model.MINOR_DROP_RATIO
import com.kynox.gaming.domain.model.SEVERE_DROP_RATIO
import com.kynox.gaming.domain.model.SessionReport
import com.kynox.gaming.ui.components.AppIconImage
import com.kynox.gaming.ui.components.ConfirmDialog
import com.kynox.gaming.ui.components.DetailTopBar
import com.kynox.gaming.ui.components.GenericViewModelFactory
import com.kynox.gaming.ui.theme.StatusDanger
import com.kynox.gaming.ui.theme.StatusGood
import com.kynox.gaming.ui.theme.StatusWarning
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private data class DayGroup(val dayStart: Long, val label: String, val reports: List<SessionReport>)

/**
 * Session history as a timeline rather than a stack of cards: sessions are
 * grouped by day along a vertical rail, and each one carries its own FPS
 * sparkline and a stability bar (share of samples that were stable, minor
 * drops and severe drops), so a bad session is visible before it is opened.
 */
@Composable
fun SessionHistoryScreen(container: AppContainer, onBack: () -> Unit, onOpenReport: (Long) -> Unit) {
    val viewModel: SessionHistoryViewModel = viewModel(
        factory = GenericViewModelFactory { SessionHistoryViewModel(container.gameSessionRepository) }
    )
    val state by viewModel.uiState.collectAsState()
    var pendingDelete by remember { mutableStateOf<SessionReport?>(null) }
    val today = stringResource(R.string.history_today)
    val yesterday = stringResource(R.string.history_yesterday)
    val groups = remember(state.reports, today, yesterday) { groupByDay(state.reports, today, yesterday) }

    Scaffold(topBar = { DetailTopBar(title = stringResource(R.string.history_title), onBack = onBack) }) { padding ->
        if (!state.loading && state.reports.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.history_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp)
            ) {
                item { HistorySummary(state.reports) }
                groups.forEach { group ->
                    item(key = "day-${group.dayStart}") { DayHeader(group.label) }
                    itemsIndexed(group.reports, key = { _, report -> report.startedAtMs }) { index, report ->
                        TimelineEntry(
                            report = report,
                            isFirst = index == 0,
                            isLast = index == group.reports.lastIndex,
                            onClick = { onOpenReport(report.startedAtMs) },
                            onDelete = { pendingDelete = report }
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { report ->
        ConfirmDialog(
            title = stringResource(R.string.history_delete_title),
            message = stringResource(R.string.history_delete_message, report.gameLabel, formatDateTime(report.startedAtMs)),
            confirmLabel = stringResource(R.string.btn_delete),
            onConfirm = {
                viewModel.delete(report)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null }
        )
    }
}

@Composable
private fun HistorySummary(reports: List<SessionReport>) {
    val totalMs = reports.sumOf { it.durationMs }
    val averages = reports.mapNotNull { it.avgFps }
    val overall = if (averages.isEmpty()) null else averages.average().toFloat()

    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp)) {
        Text(
            stringResource(R.string.history_headline),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            stringResource(R.string.history_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth()) {
            SummaryStat(reports.size.toString(), stringResource(R.string.history_stat_sessions), Modifier.weight(1f))
            SummaryStat(formatDuration(totalMs), stringResource(R.string.history_stat_playtime), Modifier.weight(1.4f))
            SummaryStat(overall?.let { "%.0f".format(it) } ?: "--", stringResource(R.string.history_stat_avg_fps), Modifier.weight(1f))
        }
    }
}

@Composable
private fun SummaryStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DayHeader(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(top = 24.dp, bottom = 10.dp)
    )
}

@Composable
private fun TimelineEntry(
    report: SessionReport,
    isFirst: Boolean,
    isLast: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val fpsValues = remember(report.startedAtMs) { report.samples.mapNotNull { it.fps } }
    val spark = remember(report.startedAtMs) { downsample(fpsValues, 48) }
    val avg = report.avgFps ?: 0f
    val railColor = MaterialTheme.colorScheme.outline
    val nodeColor = MaterialTheme.colorScheme.primary
    val holeColor = MaterialTheme.colorScheme.background
    val total = fpsValues.size
    val severe = report.severeDropCount
    val minor = report.minorDropCount
    val stable = (total - severe - minor).coerceAtLeast(0)

    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(
            Modifier
                .width(24.dp)
                .fillMaxHeight()
                .drawBehind {
                    val x = size.width / 2f
                    val nodeY = 22.dp.toPx()
                    val top = if (isFirst) nodeY else 0f
                    val bottom = if (isLast) nodeY else size.height
                    drawLine(railColor, Offset(x, top), Offset(x, bottom), strokeWidth = 2.dp.toPx())
                    drawCircle(nodeColor, radius = 6.dp.toPx(), center = Offset(x, nodeY))
                    drawCircle(holeColor, radius = 3.dp.toPx(), center = Offset(x, nodeY))
                }
        )
        Column(Modifier.weight(1f).padding(start = 8.dp, bottom = 26.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onClick),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppIconImage(report.packageName, 36.dp)
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(report.gameLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${formatTime(report.startedAtMs)} · ${formatDuration(report.durationMs)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        report.avgFps?.let { "%.0f".format(it) } ?: "--",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text("FPS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (spark.size >= 2) {
                Spacer(Modifier.height(12.dp))
                FpsSparkline(spark, avg, Modifier.fillMaxWidth().height(32.dp))
            }
            if (total > 0) {
                Spacer(Modifier.height(10.dp))
                StabilityBar(stable, minor, severe)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                val stableText = if (total > 0) stringResource(R.string.history_stable_pct, stable * 100 / total) else ""
                val caption = listOfNotNull(stableText.ifEmpty { null }, report.resolution).joinToString(" \u00B7 ")
                Text(caption, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                KTextButton(onClick = onDelete, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                    Text(
                        stringResource(R.string.btn_delete),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun FpsSparkline(values: List<Float>, avg: Float, modifier: Modifier = Modifier) {
    val peak = (values.maxOrNull() ?: 1f).coerceAtLeast(1f)
    Canvas(modifier) {
        val stroke = 1.8.dp.toPx()
        val pad = stroke
        val step = size.width / (values.size - 1)
        fun yOf(value: Float): Float = pad + (size.height - 2 * pad) * (1f - (value / peak).coerceIn(0f, 1f))
        for (i in 1 until values.size) {
            val value = values[i]
            val color = when {
                value < avg * SEVERE_DROP_RATIO -> StatusDanger
                value < avg * MINOR_DROP_RATIO -> StatusWarning
                else -> StatusGood
            }
            drawLine(
                color = color,
                start = Offset((i - 1) * step, yOf(values[i - 1])),
                end = Offset(i * step, yOf(value)),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun StabilityBar(stable: Int, minor: Int, severe: Int) {
    Row(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))) {
        if (stable > 0) Box(Modifier.weight(stable.toFloat()).fillMaxHeight().background(StatusGood))
        if (minor > 0) Box(Modifier.weight(minor.toFloat()).fillMaxHeight().background(StatusWarning))
        if (severe > 0) Box(Modifier.weight(severe.toFloat()).fillMaxHeight().background(StatusDanger))
    }
}

private fun downsample(values: List<Float>, target: Int): List<Float> {
    if (values.size <= target) return values
    val bucket = values.size / target.toFloat()
    return (0 until target).map { i ->
        val from = (i * bucket).toInt()
        val to = ((i + 1) * bucket).toInt().coerceAtMost(values.size).coerceAtLeast(from + 1)
        values.subList(from, to).average().toFloat()
    }
}

private fun startOfDay(ms: Long): Long = Calendar.getInstance().apply {
    timeInMillis = ms
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun groupByDay(reports: List<SessionReport>, today: String, yesterday: String): List<DayGroup> {
    val todayStart = startOfDay(System.currentTimeMillis())
    val yesterdayStart = Calendar.getInstance().apply {
        timeInMillis = todayStart
        add(Calendar.DAY_OF_YEAR, -1)
    }.timeInMillis
    val fullDate = SimpleDateFormat("EEEE, dd MMM", Locale.getDefault())
    return reports
        .groupBy { startOfDay(it.startedAtMs) }
        .map { (start, list) ->
            val label = when (start) {
                todayStart -> today
                yesterdayStart -> yesterday
                else -> fullDate.format(Date(start))
            }
            DayGroup(start, label, list)
        }
        .sortedByDescending { it.dayStart }
}
