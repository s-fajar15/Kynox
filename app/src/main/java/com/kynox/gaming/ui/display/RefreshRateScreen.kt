package com.kynox.gaming.ui.display

import com.kynox.gaming.ui.components.startActivitySafely
import androidx.compose.foundation.layout.Column
import com.kynox.gaming.ui.components.SectionCard
import com.kynox.gaming.ui.components.KOutlinedButton
import com.kynox.gaming.data.gaming.ForegroundAppReader
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Lifecycle
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.DisposableEffect
import android.provider.Settings
import android.net.Uri
import android.content.Intent
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kynox.gaming.AppContainer
import com.kynox.gaming.R
import com.kynox.gaming.domain.model.InstalledGame
import com.kynox.gaming.service.RefreshRateService
import com.kynox.gaming.ui.components.AppIconImage
import com.kynox.gaming.ui.components.DetailTopBar
import com.kynox.gaming.ui.components.DropdownSelector
import com.kynox.gaming.ui.components.GenericViewModelFactory
import com.kynox.gaming.ui.components.KDivider
import com.kynox.gaming.ui.components.StatusPill
import com.kynox.gaming.ui.theme.StatusDanger
import com.kynox.gaming.ui.theme.StatusGood
import kotlinx.coroutines.delay

@Composable
fun RefreshRateScreen(container: AppContainer, onBack: () -> Unit) {
    val viewModel: RefreshRateViewModel = viewModel(
        factory = GenericViewModelFactory { RefreshRateViewModel(container.refreshRateRepository, container.gameLibraryRepository) }
    )
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(state.enabled) {
        if (state.enabled) RefreshRateService.start(context) else RefreshRateService.stop(context)
        if (state.enabled) {
            // Give the service a moment to actually apply before checking --
            // this is what surfaces a device that silently ignores the write.
            delay(2500)
            viewModel.checkApplied()
        }
    }

    Scaffold(topBar = { DetailTopBar(title = stringResource(R.string.refresh_rate_title), onBack = onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.refresh_rate_enable),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Switch(checked = state.enabled, onCheckedChange = viewModel::setEnabled)
            }
            // Tanpa root: butuh dua izin biasa agar tetap berfungsi di semua ROM/device.
            var tick by remember { mutableIntStateOf(0) }
            var rootOk by remember { mutableStateOf<Boolean?>(null) }
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) tick++ }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }
            LaunchedEffect(tick) {
                rootOk = try { container.rootRepository.current().isAvailable } catch (_: Throwable) { false }
            }
            val canOverlay = remember(tick) { Settings.canDrawOverlays(context) }
            val usageOk = remember(tick) { ForegroundAppReader.hasUsageAccess(context) }
            if (rootOk == false && (!canOverlay || !usageOk)) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    SectionCard("Mode tanpa root") {
                        Text(
                            "Root tidak terdeteksi. Beri dua izin ini agar refresh rate per aplikasi tetap berjalan.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!canOverlay) {
                            KOutlinedButton(
                                onClick = {
                                    context.startActivitySafely(
                                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                },
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                            ) { Text("Izinkan tampil di atas aplikasi lain") }
                        }
                        if (!usageOk) {
                            KOutlinedButton(
                                onClick = {
                                    context.startActivitySafely(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                },
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                            ) { Text("Izinkan akses penggunaan aplikasi") }
                        }
                    }
                }
            }
            if (state.enabled) {
                val appliedHz = state.appliedHz
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Refresh rate aktif",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    when {
                        state.checkingApplied -> Text(
                            stringResource(R.string.common_loading),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        appliedHz != null -> StatusPill(
                            text = stringResource(R.string.refresh_rate_applied_value, appliedHz),
                            color = StatusGood
                        )
                        else -> StatusPill(
                            text = stringResource(R.string.refresh_rate_applied_unknown),
                            color = StatusDanger
                        )
                    }
                }
            }
            Text(
                stringResource(R.string.refresh_rate_min_android_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                label = { Text(stringResource(R.string.refresh_rate_search_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            )

            val filtered = state.apps.filter {
                state.query.isBlank() || it.label.contains(state.query, ignoreCase = true)
            }

            LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                items(filtered, key = { it.packageName }) { app ->
                    Column {
                        AppRateRow(
                            app = app,
                            currentHz = state.overrides[app.packageName],
                            defaultHz = state.defaultRate,
                            supportedRates = state.supportedRates,
                            onSelected = { hz -> viewModel.setOverride(app.packageName, hz) }
                        )
                        KDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun AppRateRow(app: InstalledGame, currentHz: Int?, defaultHz: Int, supportedRates: List<Int>, onSelected: (Int?) -> Unit) {
    val defaultLabel = stringResource(R.string.refresh_rate_default, defaultHz)
    val options = listOf(defaultLabel) + supportedRates.map { "$it Hz" }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIconImage(app.packageName, 36.dp)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(app.label, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        DropdownSelector(
            label = "",
            selected = currentHz?.let { "$it Hz" } ?: defaultLabel,
            options = options,
            enabled = true,
            onSelected = { label ->
                val hz = label.removeSuffix(" Hz").toIntOrNull()
                onSelected(hz)
            }
        )
    }
}
