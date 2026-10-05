package com.kynox.gaming.ui.process

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kynox.gaming.AppContainer
import com.kynox.gaming.R
import com.kynox.gaming.domain.model.ProcessInfo
import com.kynox.gaming.domain.model.ProcessSort
import com.kynox.gaming.ui.components.AppIconImage
import com.kynox.gaming.ui.components.ConfirmDialog
import com.kynox.gaming.ui.components.KTextButton
import com.kynox.gaming.ui.components.DetailTopBar
import com.kynox.gaming.ui.components.DropdownSelector
import com.kynox.gaming.ui.components.GenericViewModelFactory
import com.kynox.gaming.ui.components.KButton
import com.kynox.gaming.ui.components.KDivider
import com.kynox.gaming.ui.components.KLoadingIndicator
import com.kynox.gaming.ui.theme.StatusDanger
import com.kynox.gaming.ui.theme.StatusGood
import com.kynox.gaming.ui.theme.StatusWarning

@Composable
fun ProcessesScreen(container: AppContainer, onBack: () -> Unit) {
    val viewModel: ProcessViewModel = viewModel(
        factory = GenericViewModelFactory { ProcessViewModel(container.processRepository) }
    )
    val state by viewModel.uiState.collectAsState()
    var pendingKill by remember { mutableStateOf<ProcessInfo?>(null) }

    pendingKill?.let { target ->
        ConfirmDialog(
            title = stringResource(R.string.processes_kill_confirm_title),
            message = stringResource(R.string.processes_kill_confirm_message, target.name, target.pid),
            confirmLabel = stringResource(R.string.processes_kill),
            onConfirm = {
                viewModel.kill(target.pid)
                pendingKill = null
            },
            onDismiss = { pendingKill = null }
        )
    }

    Scaffold(topBar = { DetailTopBar(title = stringResource(R.string.processes_title), onBack = onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::setQuery,
                    label = { Text(stringResource(R.string.processes_search_hint)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(12.dp))
                val cpuLabel = stringResource(R.string.processes_sort_cpu)
                val memoryLabel = stringResource(R.string.processes_sort_memory)
                val nameLabel = stringResource(R.string.processes_sort_name)
                val labelsBySort = remember(cpuLabel, memoryLabel, nameLabel) {
                    mapOf(ProcessSort.CPU to cpuLabel, ProcessSort.MEMORY to memoryLabel, ProcessSort.NAME to nameLabel)
                }
                val sortsByLabel = remember(cpuLabel, memoryLabel, nameLabel) {
                    mapOf(cpuLabel to ProcessSort.CPU, memoryLabel to ProcessSort.MEMORY, nameLabel to ProcessSort.NAME)
                }
                DropdownSelector(
                    label = stringResource(R.string.processes_sort_label),
                    selected = labelsBySort[state.sort] ?: cpuLabel,
                    options = listOf(cpuLabel, memoryLabel, nameLabel),
                    enabled = true,
                    onSelected = { label -> sortsByLabel[label]?.let(viewModel::setSort) }
                )
            }

            state.killResult?.let { result ->
                val ok = result.success
                val label = if (ok) stringResource(R.string.processes_kill_ok, result.name)
                else stringResource(R.string.processes_kill_failed, result.name)
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (ok) StatusGood else StatusWarning,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    KTextButton(onClick = viewModel::dismissKillResult) { Text(stringResource(R.string.dialog_cancel)) }
                }
            }

            if (state.loading) {
                Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center) {
                    KLoadingIndicator()
                }
            } else if (state.visible.isEmpty()) {
                Text(
                    stringResource(R.string.processes_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    items(state.visible, key = { it.pid }) { process ->
                        Column {
                            ProcessRow(process, killing = state.killingPid == process.pid, onKill = { pendingKill = process })
                            KDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProcessRow(process: ProcessInfo, killing: Boolean, onKill: () -> Unit) {
    val unknown = stringResource(R.string.common_unknown)
    val cpu = process.cpuPercent?.let { "%.1f%%".format(it) } ?: unknown
    val mem = process.memoryKb?.let { "%.1f MB".format(it / 1024f) } ?: unknown
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIconImage(process.name, 32.dp)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    process.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    stringResource(R.string.processes_pid_user, process.pid, process.user),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "CPU $cpu \u00B7 RAM $mem",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            if (!process.isSystemCritical) {
                KTextButton(onClick = onKill, enabled = !killing) {
                    Text(
                        if (killing) stringResource(R.string.processes_killing) else stringResource(R.string.processes_kill),
                        color = if (killing) MaterialTheme.colorScheme.onSurfaceVariant else StatusDanger
                    )
                }
            }
        }
        if (process.isSystemCritical) {
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.processes_critical_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
