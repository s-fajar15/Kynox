package com.kynox.gaming.ui.battery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kynox.gaming.AppContainer
import com.kynox.gaming.R
import com.kynox.gaming.data.battery.FAST_CHARGE_MAX_MA
import com.kynox.gaming.data.battery.FAST_CHARGE_MIN_MA
import com.kynox.gaming.domain.model.CHARGE_LIMIT_MAX_PERCENT
import com.kynox.gaming.domain.model.CHARGE_LIMIT_MIN_PERCENT
import com.kynox.gaming.ui.components.ConfirmDialog
import com.kynox.gaming.ui.components.DetailTopBar
import com.kynox.gaming.ui.components.GenericViewModelFactory
import com.kynox.gaming.ui.components.KOutlinedButton
import com.kynox.gaming.ui.components.KynoxCardSpacing
import com.kynox.gaming.ui.components.KynoxListPadding
import com.kynox.gaming.ui.components.KynoxSwitchRow
import com.kynox.gaming.ui.components.MessageBanner
import com.kynox.gaming.ui.components.MetricRow
import com.kynox.gaming.ui.components.MetricStatus
import com.kynox.gaming.ui.components.SectionCard
import com.kynox.gaming.ui.components.SummaryCard
import com.kynox.gaming.ui.components.TonalPill
import com.kynox.gaming.ui.components.UnsupportedNotice
import com.kynox.gaming.ui.components.UnsupportedPill
import com.kynox.gaming.ui.components.fmtDec
import com.kynox.gaming.ui.components.fmtInt
import com.kynox.gaming.ui.components.kynoxCard
import com.kynox.gaming.ui.theme.KynoxShapes
import com.kynox.gaming.ui.theme.StatusDanger
import com.kynox.gaming.ui.theme.StatusWarning

/** Suhu charger: lewat 45 °C = waspada, lewat 55 °C = bahaya. */
private fun chargerTempStatus(c: Float?): MetricStatus? = when {
    c == null -> null
    c >= 55f -> MetricStatus.DANGER
    c >= 45f -> MetricStatus.WARNING
    else -> null
}

@Composable
fun BatteryScreen(container: AppContainer, onBack: () -> Unit) {
    val viewModel: BatteryViewModel = viewModel(
        factory = GenericViewModelFactory { BatteryViewModel(container.batteryRepository, container.settingsRepository) }
    )
    val state by viewModel.uiState.collectAsState()
    var pendingToggle by remember { mutableStateOf<Boolean?>(null) }
    var pendingAdvanced by remember { mutableStateOf<Boolean?>(null) }
    val unknown = "--"
    val active = stringResource(R.string.common_active)
    val stock = stringResource(R.string.status_stock)

    Scaffold(topBar = { DetailTopBar(title = stringResource(R.string.battery_charging_title), onBack = onBack) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = KynoxListPadding,
            verticalArrangement = KynoxCardSpacing
        ) {
            val info = state.info
            val charger = state.charger
            val cl = state.chargeLimit
            val fc = state.fastCharging

            state.message?.let { msg ->
                item(key = "message") { MessageBanner(message = msg, onDismiss = { viewModel.consumeMessage() }) }
            }

            // Ringkasan: persen besar + status pengisian + daya, tegangan, arus, suhu.
            item(key = "summary") {
                SummaryCard {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.weight(1f)) {
                            Text(
                                info?.capacityPercent?.toString() ?: "--",
                                style = MaterialTheme.typography.displayLarge,
                                maxLines = 1,
                                modifier = Modifier.alignByBaseline()
                            )
                            Text(
                                "%",
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(start = 4.dp).alignByBaseline()
                            )
                        }
                        TonalPill(
                            info?.chargingStatus ?: stringResource(R.string.common_loading),
                            container = MaterialTheme.colorScheme.onPrimaryContainer,
                            content = MaterialTheme.colorScheme.primaryContainer
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HeroStat("Daya", info?.powerWatts?.let { fmtDec(it) + " W" } ?: "--", Modifier.weight(1f))
                        HeroStat("Tegangan", info?.voltageMilliVolts?.let { fmtDec(it / 1000f, 2) + " V" } ?: "--", Modifier.weight(1f))
                        HeroStat("Arus", info?.currentMicroAmps?.let { "${it / 1000} mA" } ?: "--", Modifier.weight(1f))
                        HeroStat("Suhu", info?.temperatureCelsius?.let { fmtDec(it, 0) + " °C" } ?: "--", Modifier.weight(1f))
                    }
                }
            }

            item(key = "battery") {
                SectionCard(title = stringResource(R.string.dashboard_battery)) {
                    MetricRow(stringResource(R.string.field_health), info?.health?.takeIf { it.isNotBlank() } ?: unknown)
                    MetricRow(stringResource(R.string.field_charge_counter), info?.chargeCounterMicroAh?.let { fmtInt(it) + " µAh" } ?: unknown)
                    MetricRow(stringResource(R.string.field_cycle_count), info?.cycleCount?.toString() ?: unknown)
                }
            }

            if (charger?.supported == true && (charger.online || info?.isPlugged == true)) {
                item(key = "charger") {
                    SectionCard(title = stringResource(R.string.charger_info_title)) {
                        MetricRow(stringResource(R.string.field_charger_type), charger.chargerType)
                        MetricRow(stringResource(R.string.field_charger_protocol), charger.fastChargeProtocol)
                        MetricRow(
                            stringResource(R.string.field_charger_temp),
                            charger.chargerTemperatureCelsius?.let { fmtDec(it) + " °C" } ?: unknown,
                            status = chargerTempStatus(charger.chargerTemperatureCelsius)
                        )
                        MetricRow(stringResource(R.string.field_negotiated_voltage), charger.negotiatedVoltageMilliVolts?.let { "$it mV" } ?: unknown)
                        MetricRow(stringResource(R.string.field_negotiated_current), charger.negotiatedCurrentMilliAmps?.let { "$it mA" } ?: unknown)
                        if (charger.typeCMode != null) {
                            MetricRow(stringResource(R.string.field_typec_mode), charger.typeCMode)
                        }
                    }
                }
            }

            item(key = "limit") {
                SectionCard(
                    title = stringResource(R.string.charge_limit_title),
                    trailing = {
                        if (cl != null && !cl.supported) {
                            UnsupportedPill()
                        } else if (cl?.supported == true) {
                            TonalPill(if (state.chargeLimitEnabled) active else stock)
                        }
                    },
                    info = stringResource(R.string.charge_limit_desc)
                ) {
                    if (cl?.supported != true) {
                        if (cl != null) UnsupportedNotice(cl.reason, Modifier.padding(vertical = 12.dp))
                        else Text(stringResource(R.string.common_checking_compat), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 12.dp))
                    } else {
                        KynoxSwitchRow(
                            title = stringResource(R.string.charge_limit_switch_label, state.chargeLimitPercent),
                            checked = state.chargeLimitEnabled,
                            onCheckedChange = { viewModel.setChargeLimitEnabled(it) }
                        )
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            KOutlinedButton(
                                onClick = { viewModel.setChargeLimitPercent((state.chargeLimitPercent - 5).coerceAtLeast(CHARGE_LIMIT_MIN_PERCENT)) },
                                enabled = state.chargeLimitPercent > CHARGE_LIMIT_MIN_PERCENT
                            ) { Text("-5") }
                            Text(
                                "${state.chargeLimitPercent}%",
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center
                            )
                            KOutlinedButton(
                                onClick = { viewModel.setChargeLimitPercent((state.chargeLimitPercent + 5).coerceAtMost(CHARGE_LIMIT_MAX_PERCENT)) },
                                enabled = state.chargeLimitPercent < CHARGE_LIMIT_MAX_PERCENT
                            ) { Text("+5") }
                        }
                    }
                }
            }

            item(key = "fast") {
                SectionCard(
                    title = stringResource(R.string.fast_charging_title),
                    trailing = {
                        if (fc != null && !fc.supported) {
                            UnsupportedPill()
                        } else if (fc?.supported == true) {
                            TonalPill(if (fc.active) active else stock)
                        }
                    }
                ) {
                    if (fc?.supported != true) {
                        if (fc != null) UnsupportedNotice(fc.reason, Modifier.padding(vertical = 12.dp))
                        else Text(stringResource(R.string.common_checking_compat), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 12.dp))
                    } else {
                        KynoxSwitchRow(
                            title = stringResource(R.string.fast_charging_title),
                            checked = fc.active,
                            enabled = !state.busy,
                            onCheckedChange = { turningOn -> if (turningOn) pendingToggle = true else viewModel.setFastChargingEnabled(false) }
                        )
                        MetricRow(
                            "Arus pengisian nyata",
                            state.info?.currentMicroAmps?.let { "${kotlin.math.abs(it) / 1000} mA" } ?: "--"
                        )
                        MetricRow("Arus target", "${state.targetMa} mA")
                        if (fc.active && fc.activeNodePath != null) {
                            NodeInfoBlock(fc.activeNodePath, fc.activeNodeValue ?: unknown)
                        }
                        state.verdict?.let { v ->
                            val lines = buildList {
                                add(if (v.daemonRunning) "Proses pengisi berjalan." else "Proses pengisi TIDAK berjalan." + (v.launchError?.let { " ($it)" } ?: ""))
                                if (!v.readOk && v.nodesPresent == 0) {
                                    add("Gagal membaca node batas arus (perintah root timeout/error). Belum bisa disimpulkan apakah kernel mendukung; coba nyalakan ulang.")
                                } else if (v.nodesPresent == 0) {
                                    add("Kernel tidak mendukung batas arus ini: tidak ada node batas arus yang bisa dibaca, jadi nilai target tidak punya efek.")
                                } else if (v.nodesAtTarget == 0) {
                                    add("Kernel menolak nilai target: dari ${v.nodesPresent} node batas arus, tidak ada yang bernilai ${state.targetMa} mA. Kemungkinan kernel/charge IC mengunci nilainya.")
                                } else {
                                    add("Node batas arus: ${v.nodesAtTarget} dari ${v.nodesPresent} bernilai ${state.targetMa} mA.")
                                }
                                v.sample?.let { add("Terbaca: $it") }
                                add(
                                    if (!v.plugged) "Charger belum terpasang, efek ke arus belum bisa diukur."
                                    else "Arus terukur ${v.measuredCurrentMa ?: "?"} mA. Target hanyalah batas atas; arus sebenarnya ditentukan charger, kabel, dan chip charging."
                                )
                            }
                            val bad = !(v.daemonRunning && v.nodesAtTarget > 0)
                            Text(
                                lines.joinToString("\n"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .padding(top = 12.dp)
                                    .fillMaxWidth()
                                    .background(
                                        (if (bad) StatusWarning.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant)
                                            .compositeOver(MaterialTheme.colorScheme.surface),
                                        KynoxShapes.iconTile
                                    )
                                    .padding(12.dp)
                            )
                        }
                        KOutlinedButton(
                            onClick = { viewModel.toggleCustomInput() },
                            enabled = !state.busy,
                            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
                        ) { Text(stringResource(R.string.fc_set_custom_value)) }
                        if (state.showCustomInput) {
                            CustomValueInput(
                                initial = state.targetMa,
                                onSet = { value -> viewModel.setTargetMa(value); viewModel.applyCustomValue() }
                            )
                        }
                    }
                }
            }

            if (fc?.supported == true) {
                item(key = "advanced") {
                    SectionCard(
                        title = stringResource(R.string.fc_advanced_section_title),
                        trailing = {
                            TonalPill(
                                if (fc.advancedActive) stringResource(R.string.fc_advanced_status_active) else stringResource(R.string.fc_advanced_status_inactive),
                                container = if (fc.advancedActive) StatusDanger.copy(alpha = 0.2f).compositeOver(MaterialTheme.colorScheme.surface) else MaterialTheme.colorScheme.surfaceVariant
                            )
                        }
                    ) {
                        // Peringatan penting tetap tampil langsung di kartu, bukan di (i).
                        Text(
                            stringResource(R.string.fc_advanced_warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                        KynoxSwitchRow(
                            title = stringResource(R.string.fc_advanced_toggle_label),
                            checked = fc.advancedActive,
                            enabled = !state.busy && fc.active,
                            onCheckedChange = { turningOn -> if (turningOn) pendingAdvanced = true else viewModel.setAdvancedEnabled(false) }
                        )
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
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f), maxLines = 1)
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** Node sysfs aktif. Path dan nilai mentah memakai monospace (satu-satunya tempat monospace di layar ini). */
@Composable
private fun NodeInfoBlock(path: String, rawValue: String) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Text(stringResource(R.string.fc_active_node_title), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(path, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), modifier = Modifier.padding(top = 4.dp))
        Text(
            stringResource(R.string.fc_raw_value_label) + ": " + rawValue,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CustomValueInput(initial: Int, onSet: (Int) -> Unit) {
    var text by remember { mutableStateOf(initial.toString()) }
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.filter { c -> c.isDigit() } },
            label = { Text(stringResource(R.string.fc_custom_value_hint)) },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = KynoxShapes.iconTile,
            modifier = Modifier.weight(1f)
        )
        KOutlinedButton(
            onClick = { text.toIntOrNull()?.let { onSet(it.coerceIn(FAST_CHARGE_MIN_MA, FAST_CHARGE_MAX_MA)) } },
            modifier = Modifier.padding(start = 8.dp)
        ) { Text(stringResource(R.string.btn_set)) }
    }
}
