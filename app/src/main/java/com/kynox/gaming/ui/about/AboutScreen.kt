package com.kynox.gaming.ui.about

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.kynox.gaming.AppContainer
import com.kynox.gaming.R
import com.kynox.gaming.core.root.RootStatus
import com.kynox.gaming.ui.components.DetailTopBar
import com.kynox.gaming.ui.components.HighlightPanel
import com.kynox.gaming.ui.components.MetricRow
import com.kynox.gaming.ui.components.MetricStatus
import com.kynox.gaming.ui.components.SectionCard

@Composable
fun AboutScreen(container: AppContainer, onBack: () -> Unit) {
    val context = LocalContext.current
    var rootStatus by remember { mutableStateOf<RootStatus?>(null) }

    LaunchedEffect(Unit) {
        rootStatus = container.rootRepository.current()
    }

    val packageInfo = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
    }
    val versionName = packageInfo?.versionName ?: "1.0.0"
    val versionCode = if (Build.VERSION.SDK_INT >= 28) {
        packageInfo?.longVersionCode
    } else {
        @Suppress("DEPRECATION") packageInfo?.versionCode?.toLong()
    }

    Scaffold(topBar = { DetailTopBar(title = stringResource(R.string.about_label), onBack = onBack) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                HighlightPanel {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.about_app_name), style = MaterialTheme.typography.titleLarge)
                        Text(
                            stringResource(R.string.about_app_tagline),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            item {
                SectionCard(title = stringResource(R.string.about_section_app)) {
                    AboutRow(stringResource(R.string.field_version), if (versionCode != null) "$versionName ($versionCode)" else versionName)
                    AboutRow(stringResource(R.string.field_developer), stringResource(R.string.about_developer_value))
                    val githubUrl = stringResource(R.string.about_github_url)
                    AboutRow(
                        stringResource(R.string.field_github),
                        stringResource(R.string.about_github_value),
                        onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(githubUrl))) } }
                    )
                }
            }
            item {
                SectionCard(title = stringResource(R.string.about_section_status)) {
                    val status = rootStatus
                    MetricRow(
                        stringResource(R.string.about_root_status),
                        status?.let { if (it.isAvailable) it.provider.name else stringResource(R.string.about_root_unavailable) }
                            ?: stringResource(R.string.common_loading),
                        status = status?.let { if (it.isAvailable) MetricStatus.GOOD else MetricStatus.DANGER }
                    )
                    if (status?.suVersion != null) {
                        MetricRow(stringResource(R.string.about_su_version), status.suVersion)
                    }
                    MetricRow(stringResource(R.string.about_android_version), "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                }
            }
            item {
                Text(
                    stringResource(R.string.about_footer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun AboutRow(label: String, value: String, onClick: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = if (onClick != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            textDecoration = if (onClick != null) TextDecoration.Underline else TextDecoration.None
        )
    }
}
