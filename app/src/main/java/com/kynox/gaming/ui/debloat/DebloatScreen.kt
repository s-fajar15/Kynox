package com.kynox.gaming.ui.debloat

import com.kynox.gaming.ui.components.groupedItems
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kynox.gaming.AppContainer
import com.kynox.gaming.R
import com.kynox.gaming.data.debloat.DebloatApp
import com.kynox.gaming.ui.components.AppIconImage
import com.kynox.gaming.ui.components.ConfirmDialog
import com.kynox.gaming.ui.components.DetailTopBar
import com.kynox.gaming.ui.components.GenericViewModelFactory
import com.kynox.gaming.ui.components.HighlightPanel
import com.kynox.gaming.ui.components.KDivider
import com.kynox.gaming.ui.theme.StatusWarning

@Composable
fun DebloatScreen(container: AppContainer, onBack: () -> Unit) {
    val viewModel: DebloatViewModel = viewModel(
        factory = GenericViewModelFactory { DebloatViewModel(container.debloatRepository) }
    )
    val state by viewModel.uiState.collectAsState()
    var confirmTarget by remember { mutableStateOf<DebloatApp?>(null) }

    Scaffold(topBar = { DetailTopBar(title = stringResource(R.string.debloat_title), onBack = onBack) }) { padding ->
        val filtered = state.apps.filter {
            state.query.isBlank() ||
                it.label.contains(state.query, ignoreCase = true) ||
                it.packageName.contains(state.query, ignoreCase = true)
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp)
        ) {
            // Peringatan tetap tampil langsung di kartu, bukan di balik (i).
            item(key = "warning") {
                com.kynox.gaming.ui.components.NoticeCard(
                    title = stringResource(R.string.debloat_warning_title),
                    text = stringResource(R.string.debloat_warning_desc),
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
            item(key = "search") {
                com.kynox.gaming.ui.components.KynoxSearchField(
                    value = state.query,
                    onValueChange = viewModel::setQuery,
                    placeholder = stringResource(R.string.debloat_search_hint),
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
            if (state.loading) {
                item(key = "loading") {
                    Row(Modifier.fillMaxWidth().padding(32.dp), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() }
                }
            } else {
                groupedItems(filtered, key = { it.packageName }) { app ->
                    DebloatRow(
                        app = app,
                        busy = state.busyPackage == app.packageName,
                        onToggle = { wantDisabled ->
                            if (wantDisabled) confirmTarget = app else viewModel.setDisabled(app.packageName, false)
                        }
                    )
                }
            }
        }
    }

    confirmTarget?.let { app ->
        ConfirmDialog(
            title = stringResource(R.string.debloat_confirm_title, app.label),
            message = stringResource(R.string.debloat_confirm_message, app.packageName),
            confirmLabel = stringResource(R.string.debloat_confirm_action),
            onConfirm = {
                viewModel.setDisabled(app.packageName, true)
                confirmTarget = null
            },
            onDismiss = { confirmTarget = null }
        )
    }
}

@Composable
private fun DebloatRow(app: DebloatApp, busy: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIconImage(app.packageName, 36.dp)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(app.label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Switch(checked = app.enabled, onCheckedChange = { checked -> onToggle(!checked) })
        }
    }
}
