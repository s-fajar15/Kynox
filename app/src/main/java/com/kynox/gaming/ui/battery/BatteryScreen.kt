package com.kynox.gaming.ui.battery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kynox.gaming.ui.components.KOutlinedButton
import com.kynox.gaming.AppContainer
import com.kynox.gaming.R
import com.kynox.gaming.data.battery.FAST_CHARGE_MAX_MA
import com.kynox.gaming.domain.model.CHARGE_LIMIT_MAX_PERCENT
import com.kynox.gaming.domain.model.CHARGE_LIMIT_MIN_PERCENT
import com.kynox.gaming.data.battery.FAST_CHARGE_MIN_MA
import com.kynox.gaming.ui.components.ConfirmDialog
import com.kynox.gaming.ui.components.DetailTopBar
import com.kynox.gaming.ui.components.HighlightPanel
import com.kynox.gaming.ui.components.MetricRow
import com.kynox.gaming.ui.components.GenericViewModelFactory
import com.kynox.gaming.ui.components.SectionCard
import com.kynox.gaming.ui.components.StatusPill
import com.kynox.gaming.ui.theme.StatusDanger
import com.kynox.gaming.ui.theme.StatusGood

@Composable
fun BatteryScreen(container: AppContainer, onBack: () -> Unit) {
    val viewModel: BatteryViewModel = viewModel(
        factory = GenericViewModelFactory { BatteryViewModel(container.batteryRepository, container.settingsRepository) }
    )
    val state by viewModel.uiState.collectAsState()
    var pendingToggle by remember { mutableStateOf<Boolean?>(null) }
    var pendingAdvanced by remember { mutableStateOf<Boolean?>(null) }
    val unknown = stringResource(R.string.common_unknown)

    Scaffold(topBar = { DetailTopBar(title = stringResource(R.string.battery_charging_title), onBack = onBack) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(1) {
                androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val info = state.info

                // Headline: capacity + charging status up front, like the reference mockup's 82% hero.
                HighlightPanel {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            info?.capacityPercent?.let { "$it%" } ?: "--",
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                        StatusPill(text = info?.chargingStatus ?: stringResource(R.string.common_loading), color = StatusGood)
                    }
                    androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HeroStat("Daya", info?.powerWatts?.let { "%.1f W".format(it) } ?: "--", Modifier.weight(1f))
                        HeroStat("Tegangan", info?.voltageMilliVolts?.let { "%.2f V".format(it / 1000f) } ?: "--", Modifier.weight(1f))
                        HeroStat("Arus", info?.currentMicroAmps?.let { "${it / 1000} mA" } ?: "--", Modifier.weight(1f))
                        HeroStat("Suhu", info?.temperatureCelsius?.let { "%.0f°C".format(it) } ?: "--", Modifier.weight(1f))
                    }
                }

                SectionCard(title = stringResource(R.string.dashboard_battery)) {
                    MetricRow(stringResource(R.string.field_health), info?.health ?: unknown)
                    MetricRow(stringResource(R.string.field_charge_counter), info?.chargeCounterMicroAh?.let { "$it µAh" } ?: unknown)
                    MetricRow(stringResource(R.string.field_cycle_count), info?.cycleCount?.toString() ?: unknown)
                }

                val charger = state.charger
                if (charger?.supported == true && (charger.online || info?.isPlugged == true)) {
                    SectionCard(title = stringResource(R.string.charger_info_title)) {
                        MetricRow(stringResource(R.string.field_charger_type), charger.chargerType)
                        MetricRow(stringResource(R.string.field_charger_protocol), charger.fastChargeProtocol)
                        MetricRow(stringResource(R.string.field_charger_temp), charger.chargerTemperatureCelsius?.let { "%.1f°C".format(it) } ?: unknown)
                        MetricRow(stringResource(R.string.field_negotiated_voltage), charger.negotiatedVoltageMilliVolts?.let { "$it mV" } ?: unknown)
                        MetricRow(stringResource(R.string.field_negotiated_current), charger.negotiatedCurrentMilliAmps?.let { "$it mA" } ?: unknown)
                        if (charger.typeCMode != null) {
                            MetricRow(stringResource(R.string.field_typec_mode), charger.typeCMode)
                        }
                    }
                }

                val cl = state.chargeLimit
                SectionCard(
                    title = stringResource(R.string.charge_limit_title),
                    trailing = {
                        if (cl?.supported == true) {
                            StatusPill(
                                text = if (state.chargeLimitEnabled) stringResource(R.string.common_active) else stringResource(R.string.status_stock),
                                color = if (state.chargeLimitEnabled) StatusGood else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                ) {
                    if (cl?.supported != true) {
                        Text(cl?.reason ?: stringResource(R.string.common_checking_compat), style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Text(
                            stringResource(R.string.charge_limit_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 10.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                stringResource(R.string.charge_limit_switch_label, state.chargeLimitPercent),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Switch(
                                checked = state.chargeLimitEnabled,
                                onCheckedChange = { viewModel.setChargeLimitEnabled(it) }
                            )
                        }
                        androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 12.dp))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            KOutlinedButton(
                                onClick = { viewModel.setChargeLimitPercent((state.chargeLimitPercent - 5).coerceAtLeast(CHARGE_LIMIT_MIN_PERCENT)) },
                                enabled = state.chargeLimitPercent > CHARGE_LIMIT_MIN_PERCENT
                            ) { Text("-5") }
                            Text(
                                "${state.chargeLimitPercent}%",
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.weight(1f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            KOutlinedButton(
                                onClick = { viewModel.setChargeLimitPercent((state.chargeLimitPercent + 5).coerceAtMost(CHARGE_LIMIT_MAX_PERCENT)) },
                                enabled = state.chargeLimitPercent < CHARGE_LIMIT_MAX_PERCENT
                            ) { Text("+5") }
                        }
                    }
                }

                val fc = state.fastCharging
                SectionCard(
                    title = stringResource(R.string.fast_charging_title),
                    trailing = {
                        if (fc?.supported == true) {
                            StatusPill(
                                text = if (fc.active) stringResource(R.string.common_active) else stringResource(R.string.status_stock),
                                color = if (fc.active) StatusGood else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                ) {
                    if (fc?.supported != true) {
                        Text(fc?.reason ?: stringResource(R.string.common_checking_compat), style = MaterialTheme.typography.bodyMedium)
                    } else {
                        if (fc.active && fc.activeNodePath != null) {
                            NodeInfoBlock(fc.activeNodePath, fc.activeNodeValue ?: unknown)
                            androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 10.dp))
                        }

                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                stringResource(R.string.fast_charging_title) + ": " +
                                    (if (fc.active) stringResource(R.string.common_active) else stringResource(R.string.status_stock)),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Switch(
                                checked = fc.active,
                                enabled = !state.busy,
                                onCheckedChange = { turningOn -> if (turningOn) pendingToggle = true else viewModel.setFastChargingEnabled(false) }
                            )
                        }

                        androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 6.dp))
                        Text(
                            "${stringResource(R.string.target_current_label, state.targetMa)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        KOutlinedButton(onClick = { viewModel.toggleCustomInput() }, enabled = !state.busy) {
                            Text(stringResource(R.string.fc_set_custom_value))
                        }
                        if (state.showCustomInput) {
                            CustomValueInput(
                                initial = state.targetMa,
                                onSet = { value -> viewModel.setTargetMa(value); viewModel.applyCustomValue() }
                            )
                        }
                    }
                }

                if (fc?.supported == true) {
                    SectionCard(
                        title = stringResource(R.string.fc_advanced_section_title),
                        trailing = {
                            StatusPill(
                                text = if (fc.advancedActive) stringResource(R.string.fc_advanced_status_active) else stringResource(R.string.fc_advanced_status_inactive),
                                color = if (fc.advancedActive) StatusDanger else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    ) {
                        Text(
                            stringResource(R.string.fc_advanced_warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 10.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stringResource(R.string.fc_advanced_toggle_label), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = 12.dp))
                            Switch(
                                checked = fc.advancedActive,
                                enabled = !state.busy && fc.active,
                                onCheckedChange = { turningOn -> if (turningOn) pendingAdvanced = true else viewModel.setAdvancedEnabled(false) }
                            )
                        }
                    }
                }
                }
            }
        }
    }

    if (pendingToggle == true) {
        ConfirmDialog(
            title = stringResource(R.string.dialog_apply_fc_title),
            message = stringResource(R.string.dialog_apply_fc_msg, state.targetMa),
            confirmLabel = stringResource(R.string.btn_apply),
            onConfirm = { pendingToggle = null; viewModel.setFastChargingEnabled(true) },
            onDismiss = { pendingToggle = null }
        )
    }
    if (pendingAdvanced == true) {
        ConfirmDialog(
            title = stringResource(R.string.fc_advanced_toggle_label),
            message = stringResource(R.string.fc_advanced_warning),
            confirmLabel = stringResource(R.string.common_active),
            onConfirm = { pendingAdvanced = null; viewModel.setAdvancedEnabled(true) },
            onDismiss = { pendingAdvanced = null }
        )
    }
}

@Composable
private fun HeroStat(label: String, value: String, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun NodeInfoBlock(path: String, rawValue: String) {
    Text(stringResource(R.string.fc_active_node_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
    MetricRow(stringResource(R.string.fc_path_label), path)
    MetricRow(stringResource(R.string.fc_raw_value_label), rawValue)
}

@Composable
private fun CustomValueInput(initial: Int, onSet: (Int) -> Unit) {
    var text by remember { mutableStateOf(initial.toString()) }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.filter { c -> c.isDigit() } },
            label = { Text(stringResource(R.string.fc_custom_value_hint)) },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f)
        )
        KOutlinedButton(
            onClick = { text.toIntOrNull()?.let { onSet(it.coerceIn(FAST_CHARGE_MIN_MA, FAST_CHARGE_MAX_MA)) } },
            modifier = Modifier.padding(start = 8.dp)
        ) { Text(stringResource(R.string.btn_set)) }
    }
}
