package com.kynox.gaming.ui.thermal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ButtonDefaults

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kynox.gaming.ui.components.KButton
import com.kynox.gaming.ui.components.KOutlinedButton
import com.kynox.gaming.AppContainer
import com.kynox.gaming.R
import com.kynox.gaming.domain.model.ThermalEngineMode
import com.kynox.gaming.domain.model.ThermalZone
import com.kynox.gaming.ui.components.ConfirmDialog
import com.kynox.gaming.ui.components.DetailTopBar
import com.kynox.gaming.ui.components.GenericViewModelFactory
import com.kynox.gaming.ui.components.KLoadingIndicator
import com.kynox.gaming.ui.components.MetricRow
import com.kynox.gaming.ui.components.SectionCard
import com.kynox.gaming.ui.components.StatusPill
import com.kynox.gaming.ui.theme.StatusDanger
import com.kynox.gaming.ui.theme.StatusGood
import com.kynox.gaming.ui.theme.StatusWarning

@Composable
fun ThermalScreen(container: AppContainer, onBack: () -> Unit) {
    val viewModel: ThermalViewModel = viewModel(factory = GenericViewModelFactory { ThermalViewModel(container.thermalRepository) })
    val state by viewModel.uiState.collectAsState()
    var showConfirm by remember { mutableStateOf(false) }
    val unknown = stringResource(R.string.common_unknown)

    Scaffold(topBar = { DetailTopBar(title = stringResource(R.string.thermal_manager_title), onBack = onBack) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                ThermalEngineCard(
                    state = state,
                    onDisable = { showConfirm = true },
                    onRestore = { viewModel.restoreThermal() }
                )
            }
            items(state.zones, key = { it.zoneName }) { zone ->
                ThermalZoneCard(zone, unknown, thermalDisabled = state.disableState?.active == true)
            }
        }
    }

    if (showConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.dialog_disable_thermal_title),
            message = stringResource(R.string.dialog_disable_thermal_msg),
            confirmLabel = stringResource(R.string.btn_disable),
            onConfirm = { showConfirm = false; viewModel.disableThermal() },
            onDismiss = { showConfirm = false }
        )
    }
}

@Composable
private fun ThermalZoneCard(zone: ThermalZone, unknown: String, thermalDisabled: Boolean) {
    val tempUnavailableLabel = stringResource(R.string.dashboard_temp_thermal_off)
    SectionCard(title = zone.zoneName) {
        MetricRow(stringResource(R.string.field_sensor_type), zone.sensorType)
        MetricRow(
            stringResource(R.string.dashboard_temperature),
            zone.temperatureCelsius?.let { "%.1f°C".format(it) } ?: (if (thermalDisabled) tempUnavailableLabel else unknown)
        )
        if (zone.tripPoints.isNotEmpty()) {
            zone.tripPoints.forEach { trip ->
                MetricRow(
                    stringResource(R.string.trip_point_label, trip.index, trip.type ?: "?"),
                    trip.temperatureCelsius?.let { "%.1f°C".format(it) } ?: unknown
                )
            }
        }
    }
}

@Composable
private fun ThermalEngineCard(state: ThermalUiState, onDisable: () -> Unit, onRestore: () -> Unit) {
    val disableState = state.disableState
    val mode = disableState?.mode ?: ThermalEngineMode.ACTIVE
    val supported = disableState?.supported == true

    SectionCard(
        title = stringResource(R.string.thermal_engine_title),
        trailing = {
            if (supported) {
                when (mode) {
                    ThermalEngineMode.ACTIVE -> StatusPill(stringResource(R.string.thermal_pill_active), StatusGood)
                    ThermalEngineMode.DISABLED -> StatusPill(stringResource(R.string.status_disabled), StatusDanger)
                    ThermalEngineMode.PARTIAL -> StatusPill(stringResource(R.string.thermal_pill_partial), StatusWarning)
                }
            }
        }
    ) {
        if (disableState == null) {
            Text(stringResource(R.string.common_checking_compat), style = MaterialTheme.typography.bodyMedium)
        } else if (!supported) {
            Text(disableState.reason, style = MaterialTheme.typography.bodyMedium)
        } else {
            val color = when (mode) {
                ThermalEngineMode.ACTIVE -> StatusGood
                ThermalEngineMode.DISABLED -> StatusDanger
                ThermalEngineMode.PARTIAL -> StatusWarning
            }
            val headline = when (mode) {
                ThermalEngineMode.ACTIVE -> stringResource(R.string.thermal_headline_active)
                ThermalEngineMode.DISABLED -> stringResource(R.string.thermal_headline_disabled)
                ThermalEngineMode.PARTIAL -> stringResource(R.string.thermal_headline_partial)
            }
            val sub = when (mode) {
                ThermalEngineMode.ACTIVE -> stringResource(R.string.thermal_sub_active)
                ThermalEngineMode.DISABLED -> stringResource(R.string.thermal_sub_disabled)
                ThermalEngineMode.PARTIAL -> stringResource(R.string.thermal_sub_partial)
            }

            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.padding(top = 6.dp).size(10.dp).background(color, CircleShape))
                Column(Modifier.padding(start = 10.dp)) {
                    Text(headline, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
                    Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(10.dp))
            if (disableState.controlsTotal > 0) {
                Text(
                    stringResource(R.string.thermal_detail_controls, disableState.controlsDisabled, disableState.controlsTotal),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (disableState.daemonsRunning + disableState.daemonsStopped > 0) {
                Text(
                    stringResource(R.string.thermal_detail_services, disableState.daemonsRunning, disableState.daemonsStopped),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            state.notice?.let { notice ->
                Spacer(Modifier.height(10.dp))
                val text = when (notice) {
                    ThermalNotice.Disabled -> stringResource(R.string.thermal_notice_disabled)
                    ThermalNotice.Restored -> stringResource(R.string.thermal_notice_restored)
                    is ThermalNotice.Failed -> stringResource(R.string.thermal_notice_failed, notice.reason)
                }
                val noticeColor = when (notice) {
                    ThermalNotice.Disabled -> StatusWarning
                    ThermalNotice.Restored -> StatusGood
                    is ThermalNotice.Failed -> StatusDanger
                }
                Text(text, style = MaterialTheme.typography.bodySmall, color = noticeColor)
            }

            Spacer(Modifier.height(12.dp))
            if (state.busy) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    KLoadingIndicator(markSize = 22.dp)
                }
                Spacer(Modifier.height(12.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (mode != ThermalEngineMode.ACTIVE) {
                    KButton(
                        onClick = onRestore,
                        enabled = !state.busy,
                        colors = ButtonDefaults.buttonColors(containerColor = StatusGood)
                    ) { Text(stringResource(R.string.btn_reenable_thermal)) }
                }
                if (mode != ThermalEngineMode.DISABLED) {
                    KOutlinedButton(onClick = onDisable, enabled = !state.busy) {
                        Text(stringResource(R.string.btn_disable_thermal))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.thermal_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
