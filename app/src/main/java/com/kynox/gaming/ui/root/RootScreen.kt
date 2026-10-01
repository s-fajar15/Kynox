package com.kynox.gaming.ui.root

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kynox.gaming.ui.components.KButton
import com.kynox.gaming.AppContainer
import com.kynox.gaming.R
import com.kynox.gaming.ui.components.DetailTopBar
import com.kynox.gaming.ui.components.GenericViewModelFactory
import com.kynox.gaming.ui.components.MetricRow
import com.kynox.gaming.ui.components.SectionCard

@Composable
fun RootScreen(container: AppContainer, onBack: () -> Unit) {
    val viewModel: RootViewModel = viewModel(factory = GenericViewModelFactory { RootViewModel(container.rootRepository) })
    val status by viewModel.status.collectAsState()
    val unknown = stringResource(R.string.common_unknown)

    Scaffold(topBar = { DetailTopBar(title = stringResource(R.string.root_manager_title), onBack = onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionCard(title = stringResource(R.string.root_status_title)) {
                MetricRow(stringResource(R.string.field_available), if (status?.isAvailable == true) stringResource(R.string.value_yes) else stringResource(R.string.value_no))
                MetricRow(stringResource(R.string.field_provider), status?.provider?.name ?: "…")
                MetricRow(stringResource(R.string.field_su_version), status?.suVersion ?: unknown)
                MetricRow(stringResource(R.string.field_detail), status?.detail ?: "…")
            }
            KButton(onClick = { viewModel.recheck() }) { Text(stringResource(R.string.btn_recheck_root)) }
        }
    }
}
