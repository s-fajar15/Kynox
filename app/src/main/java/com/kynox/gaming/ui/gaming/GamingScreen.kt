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
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(1) {
                androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                HighlightPanel {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.gaming_mode_title),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                if (state.active) stringResource(R.string.common_active) else stringResource(R.string.gaming_mode_state_off),
                                style = MaterialTheme.typography.displaySmall,
                                color = if (state.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = state.active,
                            enabled = !state.busy,
                            onCheckedChange = { viewModel.setGameMode(it) }
                        )
                    }
                    androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 10.dp))
                    Text(
                        stringResource(R.string.gaming_mode_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                SectionCard(
                    title = stringResource(R.string.gaming_autodetect_title),
                    trailing = { if (state.autoDetect && state.rootAvailable) StatusPill(text = stringResource(R.string.common_active), color = StatusGood) }
                ) {
                    Text(
                        stringResource(R.string.gaming_autodetect_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 10.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.gaming_autodetect_switch), style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = state.autoDetect,
                            enabled = state.rootAvailable,
                            onCheckedChange = { checked ->
                                viewModel.setAutoDetect(checked)
                                if (!checked) stopDetection(context)
                            }
                        )
                    }
                    if (!state.rootAvailable) {
                        androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 6.dp))
                        Text(
                            stringResource(R.string.gaming_autodetect_needs_root),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else if (state.managedCount == 0) {
                        androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 6.dp))
                        Text(
                            stringResource(R.string.gaming_autodetect_no_games),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                SectionCard(
                    title = stringResource(R.string.gaming_overlay_title),
                    trailing = { if (state.overlayActive) StatusPill(text = stringResource(R.string.common_active), color = StatusGood) }
                ) {
                    Text(
                        stringResource(R.string.gaming_overlay_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 10.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.gaming_overlay_switch), style = MaterialTheme.typography.bodyMedium)
                        Switch(
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
                    }
                    if (!state.overlayGranted) {
                        androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 6.dp))
                        Text(
                            stringResource(R.string.gaming_overlay_needs_permission),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                SectionCard(title = stringResource(R.string.gaming_library_section_title)) {
                    Text(
                        stringResource(R.string.gaming_library_section_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 10.dp))
                    KOutlinedButton(onClick = onNavigateToLibrary) {
                        Text(stringResource(R.string.btn_manage_games))
                    }
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
