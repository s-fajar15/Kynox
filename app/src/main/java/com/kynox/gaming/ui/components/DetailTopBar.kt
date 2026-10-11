package com.kynox.gaming.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kynox.gaming.R
import com.kynox.gaming.ui.theme.KynoxIcons

/**
 * Bar atas. Tanpa [navigationIcon]: judul layar tingkat atas 26/700 (tab Beranda, Perangkat, Lainnya).
 * Dengan [navigationIcon]: layar detail (tombol kembali + judul), tanpa bottom bar.
 * Tanpa garis pemisah dan tanpa bayangan; latar mengikuti latar layar.
 */
@Composable
fun KynoxTopBar(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    navigationIcon: (@Composable () -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val topLevel = navigationIcon == null
    Column(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(start = if (topLevel) 20.dp else 8.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            navigationIcon?.invoke()
            if (leading != null) {
                leading()
                Box(Modifier.padding(horizontal = 8.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = if (topLevel) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            // Subjudul tidak tampil terus: penjelasannya dibuka lewat ikon (i).
            if (subtitle != null) InfoButton(subtitle, title = title)
            actions()
        }
    }
}

@Composable
fun DetailTopBar(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}, info: String? = null) {
    KynoxTopBar(
        title = title,
        subtitle = info,
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(KynoxIcons.Back, contentDescription = stringResource(R.string.cd_back))
            }
        },
        actions = { actions() }
    )
}
