package com.kynox.gaming.ui.profiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Icon
import com.kynox.gaming.ui.components.KDivider
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kynox.gaming.ui.components.KOutlinedButton
import com.kynox.gaming.ui.components.KTextButton
import com.kynox.gaming.AppContainer
import com.kynox.gaming.R
import com.kynox.gaming.domain.model.ProfileParamResult
import com.kynox.gaming.domain.model.ProfileType
import com.kynox.gaming.ui.components.DetailTopBar
import com.kynox.gaming.ui.components.GenericViewModelFactory
import com.kynox.gaming.ui.components.HighlightPanel
import com.kynox.gaming.ui.components.SectionCard
import com.kynox.gaming.ui.components.StatusPill
import com.kynox.gaming.ui.theme.StatusDanger
import com.kynox.gaming.ui.theme.StatusGood

private data class ProfileInfo(val type: ProfileType, val labelRes: Int, val descRes: Int)

private val presetInfo = listOf(
    ProfileInfo(ProfileType.BALANCED, R.string.profile_balanced_label, R.string.profile_balanced_desc),
    ProfileInfo(ProfileType.PERFORMANCE, R.string.profile_performance_label, R.string.profile_performance_desc),
    ProfileInfo(ProfileType.GAMING, R.string.profile_gaming_label, R.string.profile_gaming_desc),
    ProfileInfo(ProfileType.POWERSAVE, R.string.profile_powersave_label, R.string.profile_powersave_desc),
    ProfileInfo(ProfileType.CUSTOM, R.string.profile_custom_label, R.string.profile_custom_desc)
)

@Composable
fun ProfileScreen(container: AppContainer, onBack: () -> Unit) {
    val viewModel: ProfileViewModel = viewModel(
        factory = GenericViewModelFactory { ProfileViewModel(container.profileRepository, container.settingsRepository, container.thermalRepository) }
    )
    val state by viewModel.uiState.collectAsState()
    var showThermalPolicyDialog by remember { mutableStateOf(false) }

    Scaffold(topBar = { DetailTopBar(title = stringResource(R.string.profiles_title), onBack = onBack) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                val active = presetInfo.firstOrNull { it.type == state.activeProfile }
                HighlightPanel {
                    Text(
                        stringResource(R.string.active_profile_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        active?.let { stringResource(it.labelRes) } ?: stringResource(R.string.common_unknown),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    active?.let {
                        Text(
                            stringResource(it.descRes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                SectionCard(title = stringResource(R.string.profiles_section_presets)) {
                    presetInfo.forEachIndexed { index, info ->
                        ProfileRow(
                            info = info,
                            isActive = state.activeProfile == info.type,
                            busy = state.busy,
                            customAvailable = info.type != ProfileType.CUSTOM || state.hasCustomSaved,
                            onApply = { viewModel.apply(info.type) }
                        )
                        if (index < presetInfo.lastIndex) KDivider()
                    }
                }
            }

            state.lastResults?.let { results ->
                item {
                    SectionCard(
                        title = stringResource(R.string.last_apply_result_title),
                        trailing = {
                            val failed = results.count { !it.applied }
                            StatusPill(
                                text = stringResource(R.string.profile_result_summary, results.size - failed, failed),
                                color = if (failed == 0) StatusGood else StatusDanger
                            )
                        }
                    ) {
                        results.forEachIndexed { index, result ->
                            ResultRow(result)
                            if (index < results.lastIndex) KDivider()
                        }
                    }
                }
            }

            if (state.thermalPolicy.supported) {
                item {
                    SectionCard(
                        title = stringResource(R.string.thermal_policy_section_title),
                        trailing = {
                            state.thermalPolicy.currentPolicy?.let {
                                StatusPill(text = it, color = StatusGood)
                            }
                        }
                    ) {
                        Text(
                            stringResource(
                                R.string.thermal_policy_zone_support,
                                state.thermalPolicy.supportedZoneCount,
                                state.thermalPolicy.totalZoneCount
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        KOutlinedButton(
                            onClick = { showThermalPolicyDialog = true },
                            enabled = !state.thermalPolicyBusy && state.thermalPolicy.availablePolicies.isNotEmpty()
                        ) {
                            Text(stringResource(R.string.thermal_policy_change_button))
                        }
                    }
                }
            }

            item {
                SectionCard(title = stringResource(R.string.profile_custom_section_title)) {
                    Text(
                        stringResource(R.string.profile_custom_section_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    KOutlinedButton(onClick = { viewModel.saveCurrentAsCustom() }, enabled = !state.busy) {
                        Text(stringResource(R.string.btn_save_current_custom))
                    }
                }
            }

            item {
                SectionCard(title = stringResource(R.string.apply_on_boot_section_title)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.apply_on_boot_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f).padding(end = 12.dp)
                        )
                        Switch(checked = state.applyOnBoot, onCheckedChange = { viewModel.setApplyOnBoot(it) })
                    }
                }
            }

            item {
                SectionCard(title = stringResource(R.string.reset_profile_section_title)) {
                    Text(
                        stringResource(R.string.reset_profile_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    KOutlinedButton(onClick = { viewModel.reset() }, enabled = !state.busy) {
                        Text(stringResource(R.string.btn_reset_to_original))
                    }
                }
            }
        }
    }

    if (showThermalPolicyDialog) {
        ThermalPolicyDialog(
            current = state.thermalPolicy.currentPolicy,
            options = state.thermalPolicy.availablePolicies,
            onSelect = { policy ->
                viewModel.setThermalPolicy(policy)
                showThermalPolicyDialog = false
            },
            onDismiss = { showThermalPolicyDialog = false }
        )
    }
}

@Composable
private fun ThermalPolicyDialog(
    current: String?,
    options: List<String>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.thermal_policy_dialog_title)) },
        text = {
            androidx.compose.foundation.layout.Column(Modifier.selectableGroup()) {
                options.forEach { option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = option == current,
                                onClick = { onSelect(option) },
                                role = Role.RadioButton
                            )
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = option == current, onClick = { onSelect(option) })
                        Text(option, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { KTextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_close)) } }
    )
}

@Composable
private fun ProfileRow(
    info: ProfileInfo,
    isActive: Boolean,
    busy: Boolean,
    customAvailable: Boolean,
    onApply: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(stringResource(info.labelRes), style = MaterialTheme.typography.titleSmall)
            Text(
                if (customAvailable) stringResource(info.descRes) else stringResource(R.string.profile_no_custom_saved),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        when {
            isActive -> StatusPill(text = stringResource(R.string.common_active), color = StatusGood)
            customAvailable -> KTextButton(onClick = onApply, enabled = !busy) {
                Text(stringResource(R.string.btn_apply_profile))
            }
        }
    }
}

@Composable
private fun ResultRow(result: ProfileParamResult) {
    val color = if (result.applied) StatusGood else StatusDanger
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            if (result.applied) Icons.Filled.CheckCircle else Icons.Filled.Error,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(18.dp).padding(top = 2.dp)
        )
        Column(Modifier.padding(start = 12.dp)) {
            Text(result.label, style = MaterialTheme.typography.bodyMedium)
            Text(
                result.detail.ifBlank { stringResource(R.string.profile_result_no_detail) },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
