package com.kynox.gaming.ui.about

import com.kynox.gaming.ui.theme.KynoxIcons
import com.kynox.gaming.ui.components.KynoxListRow
import com.kynox.gaming.ui.components.ListCard
import com.kynox.gaming.ui.components.KMark
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

    val githubUrl = stringResource(R.string.about_github_url)
    val openGithub = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(githubUrl))) }; Unit }

    Scaffold(topBar = { DetailTopBar(title = "Tentang Kynox", onBack = onBack) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    KMark(markSize = 84.dp)
                    Spacer(Modifier.height(14.dp))
                    Text(stringResource(R.string.about_app_name), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "v$versionName" + (versionCode?.let { " (Build $it)" } ?: ""),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(18.dp))
                    Text(
                        "Aplikasi untuk memantau, mengoptimalkan, dan mengelola perangkat Android kamu dengan mudah.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }
            item {
                ListCard {
                    KynoxListRow(
                        "Pengembang", stringResource(R.string.about_developer_value), KynoxIcons.About,
                        subtitleColor = MaterialTheme.colorScheme.primary, onClick = openGithub
                    )
                    KynoxListRow(
                        "Status root",
                        rootStatus?.let { if (it.isAvailable) it.provider.name else stringResource(R.string.about_root_unavailable) }
                            ?: stringResource(R.string.common_loading),
                        KynoxIcons.Root,
                        subtitleColor = MaterialTheme.colorScheme.primary,
                        trailing = {}
                    )
                    KynoxListRow(
                        "Versi Android", "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})", KynoxIcons.Device,
                        subtitleColor = MaterialTheme.colorScheme.primary, trailing = {}
                    )
                    KynoxListRow(
                        "GitHub", stringResource(R.string.about_github_value), KynoxIcons.Info,
                        subtitleColor = MaterialTheme.colorScheme.primary, onClick = openGithub
                    )
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
