package com.kynox.gaming.ui.control

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kynox.gaming.AppContainer
import com.kynox.gaming.R
import com.kynox.gaming.core.utils.gpuFreqMhz
import com.kynox.gaming.domain.model.CpuCoreState
import com.kynox.gaming.ui.battery.BatteryViewModel
import com.kynox.gaming.ui.components.ConfirmDialog
import com.kynox.gaming.ui.components.DropdownSelector
import com.kynox.gaming.ui.components.GenericViewModelFactory
import com.kynox.gaming.ui.components.KButton
import com.kynox.gaming.ui.components.KDestructiveButton
import com.kynox.gaming.ui.components.KOutlinedButton
import com.kynox.gaming.ui.components.KynoxTopBar
import com.kynox.gaming.ui.components.MetricRow
import com.kynox.gaming.ui.components.SectionCard
import com.kynox.gaming.ui.components.StatusPill
import com.kynox.gaming.ui.cpu.CpuViewModel
import com.kynox.gaming.ui.gpu.GpuViewModel
import com.kynox.gaming.ui.theme.KynoxIcons
import com.kynox.gaming.ui.theme.KynoxShapes
import com.kynox.gaming.ui.theme.StatusDanger
import com.kynox.gaming.ui.theme.StatusGood
import com.kynox.gaming.ui.theme.StatusWarning
import com.kynox.gaming.ui.theme.kynoxColors
import com.kynox.gaming.ui.thermal.ThermalViewModel

private enum class ControlTab(val labelRes: Int) {
    CPU(R.string.control_tab_cpu),
    GPU(R.string.control_tab_gpu),
    THERMAL(R.string.control_tab_thermal),
    BATTERY(R.string.control_tab_battery)
}

/**
 * Live quick-controls for CPU/GPU/Thermal/Battery in one place (PRD "Control").
 * Distinct from Device (a read-mostly hardware/system hub): every value here
 * is editable, backed by the same ViewModels the full detail screens use --
 * nothing here is a mock, and "Details" links hand off to those full screens
 * for anything not condensed (affinity, custom fast-charge current, per-zone
 * thermal readouts).
 */
@Composable
fun ControlScreen(
    container: AppContainer,
    onOpenBattery: () -> Unit
) {
    var tab by remember { mutableStateOf(ControlTab.CPU) }

    Scaffold(topBar = { KynoxTopBar(title = stringResource(R.string.control_title)) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ControlTabRow(selected = tab, onSelect = { tab = it })
            when (tab) {
                ControlTab.CPU -> CpuControlTab(container)
                ControlTab.GPU -> GpuControlTab(container)
                ControlTab.THERMAL -> ThermalControlTab(container)
                ControlTab.BATTERY -> BatteryControlTab(container, onOpenBattery)
            }
        }
    }
}

@Composable
private fun ControlTabRow(selected: ControlTab, onSelect: (ControlTab) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(ControlTab.values().toList()) { entry ->
            val active = entry == selected
            val bg = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.kynoxColors.surfaceSunken
            val fg = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            Box(
                Modifier
                    .clip(KynoxShapes.control)
                    .background(bg)
                    .clickable { onSelect(entry) }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(stringResource(entry.labelRes), style = MaterialTheme.typography.labelLarge, color = fg)
            }
        }
    }
}

// ---- CPU ----

@Composable
private fun CpuControlTab(container: AppContainer) {
    val viewModel: CpuViewModel = viewModel(factory = GenericViewModelFactory { CpuViewModel(container.cpuRepository) })
    val snapshot by viewModel.snapshot.collectAsState()
    val cores = snapshot?.cores ?: emptyList()
    val unknown = stringResource(R.string.common_unknown)
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

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            SectionCard(title = stringResource(R.string.control_cpu_management)) {
                MetricRow(
                    stringResource(R.string.dashboard_usage),
                    snapshot?.overallUsagePercent?.let { "%.0f%%".format(it) } ?: unknown
                )
                val firstWritable = cores.firstOrNull { it.availableGovernors.isNotEmpty() }
                if (firstWritable != null) {
                    DropdownSelector(
                        label = stringResource(R.string.field_governor),
                        selected = firstWritable.governor ?: unknown,
                        options = firstWritable.availableGovernors,
                        enabled = firstWritable.canWriteGovernor,
                        onSelected = { governor -> cores.forEach { viewModel.setGovernor(it.core, governor) } }
                    )
                }
            }
        }
        item {
            SectionCard(title = stringResource(R.string.control_core_control)) {
                cores.forEach { core -> CoreRow(core, onOnlineToggle = { on -> if (on) viewModel.setOnline(core.core, true) else pendingOffline = core.core }) }
            }
        }
    }
}

@Composable
private fun CoreRow(core: CpuCoreState, onOnlineToggle: (Boolean) -> Unit) {
    val unknown = stringResource(R.string.common_unknown)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            stringResource(R.string.core_title, core.core),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(72.dp)
        )
        Text(
            core.currentFreqKhz?.let { "${it / 1000} MHz" } ?: unknown,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        if (core.online != null) {
            Switch(checked = core.online, onCheckedChange = onOnlineToggle, enabled = core.canToggleOnline)
        } else {
            StatusPill(text = stringResource(R.string.status_online), color = StatusGood)
        }
    }
}

// ---- GPU ----

@Composable
private fun GpuControlTab(container: AppContainer) {
    val viewModel: GpuViewModel = viewModel(factory = GenericViewModelFactory { GpuViewModel(container.gpuRepository) })
    val gpu by viewModel.state.collectAsState()
    val unknown = stringResource(R.string.common_unknown)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            SectionCard(title = stringResource(R.string.dashboard_gpu)) {
                if (gpu?.supported != true) {
                    Text(gpu?.reason ?: stringResource(R.string.common_loading), style = MaterialTheme.typography.bodyMedium)
                } else {
                    val state = gpu!!
                    MetricRow(stringResource(R.string.field_current_freq), state.currentFreqKhz?.let { "${gpuFreqMhz(it)} MHz" } ?: unknown)
                    MetricRow(stringResource(R.string.dashboard_utilization), state.utilizationPercent?.let { "%.0f%%".format(it) } ?: unknown)
                    MetricRow(stringResource(R.string.dashboard_temperature), state.temperatureCelsius?.let { "%.1f°C".format(it) } ?: unknown)
                    if (state.availableGovernors.isNotEmpty()) {
                        DropdownSelector(
                            label = stringResource(R.string.field_governor),
                            selected = state.governor ?: unknown,
                            options = state.availableGovernors,
                            enabled = state.canWriteGovernor,
                            onSelected = { viewModel.setGovernor(it) }
                        )
                    } else {
                        MetricRow(stringResource(R.string.field_governor), state.governor ?: unknown)
                    }
                }
            }
        }
    }
}

// ---- Thermal ----

@Composable
private fun ThermalControlTab(container: AppContainer) {
    val viewModel: ThermalViewModel = viewModel(factory = GenericViewModelFactory { ThermalViewModel(container.thermalRepository) })
    val state by viewModel.uiState.collectAsState()
    var showConfirm by remember { mutableStateOf(false) }
    val unknown = stringResource(R.string.common_unknown)
    val active = state.disableState?.active == true

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            SectionCard(
                title = stringResource(R.string.control_thermal_status),
                trailing = {
                    StatusPill(
                        text = if (active) stringResource(R.string.status_offline) else stringResource(R.string.status_online),
                        color = if (active) StatusDanger else StatusGood
                    )
                }
            ) {
                state.zones.take(4).forEach { zone ->
                    MetricRow(zone.zoneName, zone.temperatureCelsius?.let { "%.0f°C".format(it) } ?: unknown)
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!active) {
                        KDestructiveButton(onClick = { showConfirm = true }, enabled = !state.busy) {
                            Text(stringResource(R.string.btn_disable_thermal))
                        }
                    } else {
                        KButton(onClick = { viewModel.restoreThermal() }, enabled = !state.busy) {
                            Text(stringResource(R.string.btn_restore))
                        }
                    }
                }
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

// ---- Battery ----

@Composable
private fun BatteryControlTab(container: AppContainer, onOpenBattery: () -> Unit) {
    val viewModel: BatteryViewModel = viewModel(
        factory = GenericViewModelFactory { BatteryViewModel(container.batteryRepository, container.settingsRepository) }
    )
    val state by viewModel.uiState.collectAsState()
    val unknown = stringResource(R.string.common_unknown)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            SectionCard(title = stringResource(R.string.dashboard_battery)) {
                MetricRow(stringResource(R.string.dashboard_capacity), state.info?.capacityPercent?.let { "$it%" } ?: unknown)
                MetricRow(stringResource(R.string.dashboard_power), state.info?.powerWatts?.let { "%.1f W".format(it) } ?: unknown)
                MetricRow(stringResource(R.string.dashboard_temperature), state.info?.temperatureCelsius?.let { "%.1f°C".format(it) } ?: unknown)
            }
        }
        item {
            SectionCard(title = stringResource(R.string.charge_limit_title)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.charge_limit_switch_label, state.chargeLimitPercent),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Switch(checked = state.chargeLimitEnabled, onCheckedChange = { viewModel.setChargeLimitEnabled(it) })
                }
            }
        }
        item {
            SectionCard(title = stringResource(R.string.fast_charging_title)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        state.fastCharging?.targetCurrentMa?.let { "$it mA" } ?: unknown,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Switch(
                        checked = state.fastCharging?.active == true,
                        enabled = !state.busy,
                        onCheckedChange = { viewModel.setFastChargingEnabled(it) }
                    )
                }
                Spacer(Modifier.height(10.dp))
                KOutlinedButton(onClick = onOpenBattery) { Text(stringResource(R.string.control_open_battery_details)) }
            }
        }
    }
}
