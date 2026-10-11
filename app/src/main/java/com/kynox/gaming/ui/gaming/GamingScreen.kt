package com.kynox.gaming.ui.gaming

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kynox.gaming.ui.components.HighlightPanel
import com.kynox.gaming.ui.components.KynoxTopBar
import com.kynox.gaming.ui.components.KOutlinedButton
import com.kynox.gaming.AppContainer
import com.kynox.gaming.R
import com.kynox.gaming.ui.components.GenericViewModelFactory
import com.kynox.gaming.ui.components.SectionCard
import com.kynox.gaming.service.GameDetectionService
import com.kynox.gaming.service.QuickOverlayService
import com.kynox.gaming.ui.components.StatusPill
import com.kynox.gaming.ui.theme.KynoxIcons
import com.kynox.gaming.ui.theme.StatusGood

@Composable
fun GamingScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onNavigateToLibrary: () -> Unit,
) {
    val viewModel: GamingViewModel = viewModel(
        factory = GenericViewModelFactory {
            GamingViewModel(container.gamingModeRepository, container.gameDetectionRepository, container.overlayPermissionRepository)
        }
    )
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(state.autoDetect, state.rootAvailable) {
        if (state.autoDetect && state.rootAvailable) startDetection(context)
    }

    Scaffold(
        topBar = {
            KynoxTopBar(
                title = stringResource(R.string.gaming_title),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(KynoxIcons.Back, contentDescription = stringResource(R.string.cd_back))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = com.kynox.gaming.ui.components.KynoxListPadding,
            verticalArrangement = com.kynox.gaming.ui.components.KynoxCardSpacing
        ) {
            item(key = "mode") {
                com.kynox.gaming.ui.components.SummaryCard {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.gaming_mode_title), style = MaterialTheme.typography.labelMedium)
                            Text(
                                if (state.active) stringResource(R.string.common_active) else stringResource(R.string.gaming_mode_state_off),
                                style = MaterialTheme.typography.displaySmall
                            )
                        }
                        com.kynox.gaming.ui.components.InfoButton(stringResource(R.string.gaming_mode_desc), title = stringResource(R.string.gaming_mode_title))
                        Switch(
                            checked = state.active,
                            enabled = !state.busy,
                            onCheckedChange = { viewModel.setGameMode(it) }
                        )
                    }
                }
            }

            item(key = "autodetect") {
                SectionCard(
                    title = stringResource(R.string.gaming_autodetect_title),
                    info = stringResource(R.string.gaming_autodetect_desc)
                ) {
                    com.kynox.gaming.ui.components.KynoxSwitchRow(
                        title = stringResource(R.string.gaming_autodetect_switch),
                        checked = state.autoDetect,
                        enabled = state.rootAvailable,
                        onCheckedChange = { checked ->
                            viewModel.setAutoDetect(checked)
                            if (!checked) stopDetection(context)
                        }
                    )
                    // Peringatan tetap terlihat langsung di kartu.
                    if (!state.rootAvailable) {
                        Text(
                            stringResource(R.string.gaming_autodetect_needs_root),
                            style = MaterialTheme.typography.bodySmall,
                            color = com.kynox.gaming.ui.theme.StatusWarning,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else if (state.managedCount == 0) {
                        Text(
                            stringResource(R.string.gaming_autodetect_no_games),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
            }

            item(key = "overlay") {
                SectionCard(
                    title = stringResource(R.string.gaming_overlay_title),
                    info = stringResource(R.string.gaming_overlay_desc)
                ) {
                    com.kynox.gaming.ui.components.KynoxSwitchRow(
                        title = stringResource(R.string.gaming_overlay_switch),
                        checked = state.overlayActive,
                        onCheckedChange = { checked ->
                            if (checked) {
                                viewModel.prepareOverlayPermission { granted ->
                                    if (granted) {
                                        startQuickOverlay(context)
                                        viewModel.setOverlayActive(true)
                                    }
                                }
                            } else {
                                stopQuickOverlay(context)
                                viewModel.setOverlayActive(false)
                            }
                        }
                    )
                    if (!state.overlayGranted) {
                        Text(
                            stringResource(R.string.gaming_overlay_needs_permission),
                            style = MaterialTheme.typography.bodySmall,
                            color = com.kynox.gaming.ui.theme.StatusWarning,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
            }

            item(key = "library") {
                SectionCard(title = stringResource(R.string.gaming_library_section_title), info = stringResource(R.string.gaming_library_section_desc)) {
                    KOutlinedButton(onClick = onNavigateToLibrary, modifier = Modifier.padding(vertical = 12.dp)) {
                        Text(stringResource(R.string.btn_manage_games))
                    }
                }
            }
        }
    }
}

private fun startDetection(context: Context) {
    val intent = Intent(context, GameDetectionService::class.java).apply {
        action = GameDetectionService.ACTION_START
    }
    if (Build.VERSION.SDK_INT >= 26) {
        context.startForegroundService(intent)
    } else {
        context.startService(intent)
    }
}

private fun stopDetection(context: Context) {
    val intent = Intent(context, GameDetectionService::class.java).apply {
        action = GameDetectionService.ACTION_STOP
    }
    context.startService(intent)
}

private fun startQuickOverlay(context: Context) {
    QuickOverlayService.start(context)
}

private fun stopQuickOverlay(context: Context) {
    QuickOverlayService.stop(context)
}
