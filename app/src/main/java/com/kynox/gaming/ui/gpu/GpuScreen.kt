package com.kynox.gaming.ui.gpu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kynox.gaming.core.utils.gpuFreqMhz
import com.kynox.gaming.AppContainer
import com.kynox.gaming.R
import com.kynox.gaming.ui.components.DropdownSelector
import com.kynox.gaming.ui.components.DetailTopBar
import com.kynox.gaming.ui.components.GenericViewModelFactory
import com.kynox.gaming.ui.components.HighlightPanel
import com.kynox.gaming.ui.components.MetricRow
import com.kynox.gaming.ui.components.Readout
import com.kynox.gaming.ui.components.SectionCard

@Composable
fun GpuScreen(container: AppContainer, onBack: () -> Unit) {
    val viewModel: GpuViewModel = viewModel(factory = GenericViewModelFactory { GpuViewModel(container.gpuRepository) })
    val state by viewModel.state.collectAsState()
    val unknown = stringResource(R.string.common_unknown)

    Scaffold(topBar = { DetailTopBar(title = stringResource(R.string.gpu_manager_title), onBack = onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val gpu = state
            if (gpu?.supported == true) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HighlightPanel(Modifier.weight(1f)) {
                        Readout(gpu.currentFreqKhz?.let { gpuFreqMhz(it).toString() } ?: unknown, if (gpu.currentFreqKhz != null) "MHz" else "", stringResource(R.string.field_current_freq))
                    }
                    HighlightPanel(Modifier.weight(1f)) {
                        Readout(gpu.utilizationPercent?.let { "%.0f".format(it) } ?: unknown, if (gpu.utilizationPercent != null) "%" else "", stringResource(R.string.dashboard_utilization))
                    }
                }
            }
            SectionCard(title = stringResource(R.string.dashboard_gpu)) {
                if (gpu?.supported != true) {
                    Text(gpu?.reason ?: stringResource(R.string.common_loading), style = MaterialTheme.typography.bodyMedium)
                } else {
                    MetricRow(stringResource(R.string.field_min_freq), gpu.minFreqKhz?.let { "${gpuFreqMhz(it)} MHz" } ?: unknown)
                    MetricRow(stringResource(R.string.field_max_freq), gpu.maxFreqKhz?.let { "${gpuFreqMhz(it)} MHz" } ?: unknown)
                    MetricRow(stringResource(R.string.dashboard_temperature), gpu.temperatureCelsius?.let { "%.1f°C".format(it) } ?: unknown)
                    if (gpu.availableGovernors.isNotEmpty()) {
                        DropdownSelector(
                            label = stringResource(R.string.field_governor),
                            selected = gpu.governor ?: unknown,
                            options = gpu.availableGovernors,
                            enabled = gpu.canWriteGovernor,
                            onSelected = { viewModel.setGovernor(it) }
                        )
                    } else {
                        MetricRow(stringResource(R.string.field_governor), gpu.governor ?: unknown)
                    }
                }
            }
        }
    }
}
