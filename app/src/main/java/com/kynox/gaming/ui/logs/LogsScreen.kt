package com.kynox.gaming.ui.logs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kynox.gaming.AppContainer
import com.kynox.gaming.R
import com.kynox.gaming.domain.model.LogEntry
import com.kynox.gaming.ui.components.DetailTopBar
import com.kynox.gaming.ui.components.GenericViewModelFactory
import com.kynox.gaming.ui.components.SectionCard
import com.kynox.gaming.ui.components.StatusPill
import com.kynox.gaming.ui.theme.StatusDanger
import com.kynox.gaming.ui.theme.StatusGood
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LogsScreen(container: AppContainer, onBack: () -> Unit) {
    val viewModel: LogsViewModel = viewModel(factory = GenericViewModelFactory { LogsViewModel(container.logRepository) })
    val logs by viewModel.logs.collectAsState()
    val formatter = remember { SimpleDateFormat("dd MMM HH:mm:ss", Locale.getDefault()) }
    val clearLogsDesc = stringResource(R.string.cd_clear_logs)

    Scaffold(
        topBar = {
            DetailTopBar(
                title = stringResource(R.string.logs_title),
                onBack = onBack,
                actions = {
                    IconButton(onClick = { viewModel.clear() }) {
                        Icon(Icons.Filled.Delete, contentDescription = clearLogsDesc)
                    }
                }
            )
        }
    ) { padding ->
        if (logs.isEmpty()) {
            androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.logs_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(logs, key = { it.timestamp.toString() + it.target }) { entry -> LogRow(entry, formatter) }
        }
    }
}

@Composable
private fun LogRow(entry: LogEntry, formatter: SimpleDateFormat) {
    SectionCard(
        title = entry.action,
        trailing = {
            StatusPill(
                text = entry.result,
                color = if (entry.result == "SUCCESS") StatusGood else StatusDanger
            )
        }
    ) {
        Text(entry.target, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(formatter.format(Date(entry.timestamp)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (entry.previousValue != null || entry.newValue != null) {
            Text(
                stringResource(R.string.log_value_arrow, entry.previousValue ?: "?", entry.newValue ?: "?"),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        entry.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = StatusDanger) }
    }
}
