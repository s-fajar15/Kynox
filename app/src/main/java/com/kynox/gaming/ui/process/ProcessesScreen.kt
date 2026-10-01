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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kynox.gaming.AppContainer
import com.kynox.gaming.R
import com.kynox.gaming.domain.model.ProcessInfo
import com.kynox.gaming.domain.model.ProcessSort
import com.kynox.gaming.ui.components.AppIconImage
import com.kynox.gaming.ui.components.DetailTopBar
import com.kynox.gaming.ui.components.DropdownSelector
import com.kynox.gaming.ui.components.GenericViewModelFactory
import com.kynox.gaming.ui.components.KButton
import com.kynox.gaming.ui.components.KDivider
import com.kynox.gaming.ui.components.KLoadingIndicator
import com.kynox.gaming.ui.theme.StatusDanger

@Composable
fun ProcessesScreen(container: AppContainer, onBack: () -> Unit) {
    val viewModel: ProcessViewModel = viewModel(
        factory = GenericViewModelFactory { ProcessViewModel(container.processRepository) }
    )
    val state by viewModel.uiState.collectAsState()

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

            if (state.loading) {
                Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center) {
                    KLoadingIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    items(state.visible, key = { it.pid }) { process ->
                        Column {
                            ProcessRow(process, killing = state.killingPid == process.pid, onKill = { viewModel.kill(process.pid) })
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
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIconImage(process.name, 32.dp)
            Column(Modifier.padding(start = 12.dp)) {
                Text(process.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 2)
                Text(process.pid.toString(), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.processes_user, process.user),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            stringResource(R.string.processes_cpu, process.cpuPercent?.let { "%.1f%%".format(it) } ?: unknown),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            stringResource(
                R.string.processes_memory,
                process.memoryKb?.let { "%.1f MB".format(it / 1024f) } ?: unknown,
                process.memoryPercent?.let { "%.1f%%".format(it) } ?: unknown
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))
        if (process.isSystemCritical) {
            Text(
                stringResource(R.string.processes_critical_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            KButton(
                onClick = onKill,
                enabled = !killing,
                colors = ButtonDefaults.buttonColors(containerColor = StatusDanger),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (killing) stringResource(R.string.processes_killing) else stringResource(R.string.processes_kill))
            }
        }
    }
}
