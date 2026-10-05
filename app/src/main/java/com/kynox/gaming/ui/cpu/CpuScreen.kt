package com.kynox.gaming.ui.cpu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.kynox.gaming.ui.components.ConfirmDialog
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kynox.gaming.AppContainer
import com.kynox.gaming.R
import com.kynox.gaming.domain.model.CpuCoreState
import com.kynox.gaming.ui.components.DropdownSelector
import com.kynox.gaming.ui.components.DetailTopBar
import com.kynox.gaming.ui.components.GenericViewModelFactory
import com.kynox.gaming.ui.components.HighlightPanel
import com.kynox.gaming.ui.components.KLinearProgress
import com.kynox.gaming.ui.components.MetricRow
import com.kynox.gaming.ui.components.Readout
import com.kynox.gaming.ui.components.SectionCard
import com.kynox.gaming.ui.components.StatusPill
import com.kynox.gaming.ui.theme.StatusGood
import com.kynox.gaming.ui.theme.StatusWarning

@Composable
fun CpuScreen(container: AppContainer, onBack: () -> Unit) {
    val viewModel: CpuViewModel = viewModel(factory = GenericViewModelFactory { CpuViewModel(container.cpuRepository) })
    val snapshot by viewModel.snapshot.collectAsState()
    val unknownFreq = stringResource(R.string.common_unknown)
    var pendingOffline by remember { mutableStateOf<Int?>(null) }

    pendingOffline?.let { coreIndex ->
        ConfirmDialog(
            title = "Matikan core $coreIndex?",
            message = "Mematikan core bisa membuat perangkat melambat atau hang. Core bisa dinyalakan lagi dari layar ini.",
            confirmLabel = "Matikan",
            onConfirm = {
                pendingOffline = null
                viewModel.setOnline(coreIndex, false)
            },
            onDismiss = { pendingOffline = null }
        )
    }

    Scaffold(topBar = { DetailTopBar(title = stringResource(R.string.cpu_manager_title), onBack = onBack) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val cores = snapshot?.cores ?: emptyList()

            item {
                val currentMhz = cores.firstOrNull()?.currentFreqKhz?.let { (it / 1000).toString() } ?: unknownFreq
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HighlightPanel(Modifier.weight(1f)) {
                        Readout(currentMhz, if (currentMhz != unknownFreq) "MHz" else "", stringResource(R.string.field_current_freq))
                    }
                    HighlightPanel(Modifier.weight(1f)) {
                        val usage = snapshot?.overallUsagePercent
                        Readout(usage?.let { "%.0f".format(it) } ?: unknownFreq, if (usage != null) "%" else "", stringResource(R.string.dashboard_usage))
                    }
                }
            }
            items(cores, key = { it.core }) { core ->
                CoreCard(
                    core,
                    onGovernorSelected = { governor -> viewModel.setGovernor(core.core, governor) },
                    onOnlineToggle = { online -> if (online) viewModel.setOnline(core.core, true) else pendingOffline = core.core }
                )
            }
        }
    }
}

@Composable
private fun CoreCard(core: CpuCoreState, onGovernorSelected: (String) -> Unit, onOnlineToggle: (Boolean) -> Unit) {
    val unknown = stringResource(R.string.common_unknown)
    SectionCard(
        title = stringResource(R.string.core_title, core.core),
        trailing = {
            StatusPill(
                text = if (core.online != false) stringResource(R.string.status_online) else stringResource(R.string.status_offline),
                color = if (core.online != false) StatusGood else StatusWarning
            )
        }
    ) {
        MetricRow(stringResource(R.string.field_current_freq), core.currentFreqKhz?.let { "${it / 1000} MHz" } ?: unknown)
        MetricRow(stringResource(R.string.field_min_freq), core.minFreqKhz?.let { "${it / 1000} MHz" } ?: unknown)
        MetricRow(stringResource(R.string.field_max_freq), core.maxFreqKhz?.let { "${it / 1000} MHz" } ?: unknown)
        val min = core.minFreqKhz
        val max = core.maxFreqKhz
        val current = core.currentFreqKhz
        if (min != null && max != null && current != null && max > min) {
            Spacer(Modifier.height(4.dp))
            KLinearProgress(progress = (current - min).toFloat() / (max - min).toFloat())
            Spacer(Modifier.height(8.dp))
        }
        if (core.availableGovernors.isNotEmpty()) {
            DropdownSelector(
                label = stringResource(R.string.field_governor),
                selected = core.governor ?: unknown,
                options = core.availableGovernors,
                enabled = core.canWriteGovernor,
                onSelected = onGovernorSelected
            )
            if (!core.canWriteGovernor) {
                Text(stringResource(R.string.governor_readonly_warning), style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
            }
        } else {
            MetricRow(stringResource(R.string.field_governor), core.governor ?: unknown)
        }
        if (core.online != null) {
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.field_core_online),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Switch(checked = core.online, onCheckedChange = onOnlineToggle, enabled = core.canToggleOnline)
            }
            if (!core.canToggleOnline && core.core == 0) {
                Text(
                    stringResource(R.string.core_zero_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
