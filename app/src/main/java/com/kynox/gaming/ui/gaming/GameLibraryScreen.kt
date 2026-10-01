package com.kynox.gaming.ui.gaming

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kynox.gaming.AppContainer
import com.kynox.gaming.R
import com.kynox.gaming.domain.model.InstalledGame
import com.kynox.gaming.domain.model.ProfileType
import com.kynox.gaming.ui.components.KLoadingIndicator
import com.kynox.gaming.ui.components.DetailTopBar
import com.kynox.gaming.ui.components.DropdownSelector
import com.kynox.gaming.ui.components.GenericViewModelFactory
import com.kynox.gaming.ui.components.AppIconImage
import com.kynox.gaming.ui.components.SectionCard
import com.kynox.gaming.ui.components.StatusPill
import com.kynox.gaming.ui.theme.StatusGood

@Composable
fun GameLibraryScreen(container: AppContainer, onBack: () -> Unit) {
    val viewModel: GameLibraryViewModel = viewModel(
        factory = GenericViewModelFactory { GameLibraryViewModel(container.gameLibraryRepository) }
    )
    val state by viewModel.uiState.collectAsState()

    Scaffold(topBar = { DetailTopBar(title = stringResource(R.string.game_library_title), onBack = onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = { viewModel.setQuery(it) },
                label = { Text(stringResource(R.string.game_library_search_hint)) },
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            )

            if (state.loading) {
                Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center) {
                    KLoadingIndicator()
                }
                return@Column
            }

            val filtered = state.apps.filter {
                state.query.isBlank() || it.label.contains(state.query, ignoreCase = true)
            }

            if (filtered.isEmpty()) {
                Text(
                    stringResource(R.string.game_library_empty),
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Column
            }

            LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                items(filtered, key = { it.packageName }) { app ->
                    GameRow(
                        app,
                        onToggle = { managed -> viewModel.setManaged(app.packageName, managed) },
                        onProfileSelected = { profile -> viewModel.setProfile(app.packageName, profile) }
                    )
                }
            }
        }
    }
}

@Composable
private fun GameRow(app: InstalledGame, onToggle: (Boolean) -> Unit, onProfileSelected: (ProfileType?) -> Unit) {
    SectionCard(
        title = app.label,
        leadingIcon = { AppIconImage(app.packageName, 20.dp) },
        trailing = { if (app.isAutoDetectedGame) StatusPill(text = stringResource(R.string.game_library_auto_detected), color = StatusGood) }
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Switch(checked = app.isManaged, onCheckedChange = onToggle)
        }
        if (app.isManaged) {
            androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 8.dp))
            val defaultLabel = stringResource(R.string.game_library_profile_default)
            val labels = listOf(
                defaultLabel,
                stringResource(R.string.profile_balanced_label),
                stringResource(R.string.profile_performance_label),
                stringResource(R.string.profile_gaming_label),
                stringResource(R.string.profile_powersave_label),
                stringResource(R.string.profile_custom_label)
            )
            val types = listOf(null, ProfileType.BALANCED, ProfileType.PERFORMANCE, ProfileType.GAMING, ProfileType.POWERSAVE, ProfileType.CUSTOM)
            val selectedIndex = types.indexOf(app.assignedProfile).coerceAtLeast(0)
            DropdownSelector(
                label = stringResource(R.string.game_library_profile_label),
                selected = labels[selectedIndex],
                options = labels,
                enabled = true,
                onSelected = { picked -> onProfileSelected(types[labels.indexOf(picked)]) }
            )
        }
    }
}
