package com.kynox.gaming.ui.components

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.kynox.gaming.data.gaming.ForegroundAppReader
import com.kynox.gaming.ui.theme.KynoxIcons
import com.kynox.gaming.ui.theme.StatusGood
import com.kynox.gaming.ui.theme.KynoxShapes

/**
 * Dialog izin ala mockup, ditampilkan sekali saat pertama dibuka (hanya jika ada izin yang belum diberikan).
 * Setiap baris menjelaskan kegunaan izinnya dan bisa diketuk sendiri; "Izinkan" membuka izin pertama yang masih kurang.
 */
@Composable
fun PermissionPrompt(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) tick++ }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }

    val usageOk = remember(tick) { ForegroundAppReader.hasUsageAccess(context) }
    val notifOk = remember(tick) {
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }
    val overlayOk = remember(tick) { Settings.canDrawOverlays(context) }

    LaunchedEffect(usageOk, notifOk, overlayOk) {
        if (usageOk && notifOk && overlayOk) onDismiss()
    }

    val askUsage = {
        context.startActivitySafely(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    val askNotif = {
        if (Build.VERSION.SDK_INT >= 33) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    val askOverlay = {
        context.startActivitySafely(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().kynoxCard(KynoxShapes.hero).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            KMark(markSize = 52.dp)
            Spacer(Modifier.height(8.dp))
            Text("Kynox", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(
                "Aplikasi ini memerlukan akses untuk membantu mengoptimalkan perangkat Anda.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PermissionItem("Akses penggunaan", "untuk analisis dan deteksi aplikasi", KynoxIcons.Monitor, usageOk, askUsage)
                PermissionItem("Akses notifikasi", "untuk status dan peringatan", KynoxIcons.Notifications, notifOk, askNotif)
                PermissionItem("Tampil di atas aplikasi", "untuk overlay dan refresh rate", KynoxIcons.Display, overlayOk, askOverlay)
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                KOutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Tolak") }
                KButton(
                    onClick = {
                        when {
                            !usageOk -> askUsage()
                            !notifOk -> askNotif()
                            !overlayOk -> askOverlay()
                            else -> onDismiss()
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Izinkan") }
            }
        }
    }
}

@Composable
private fun PermissionItem(
    title: String,
    reason: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    granted: Boolean,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(if (granted) Modifier else Modifier.clickable(onClick = onClick))
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconTile(icon, size = 38.dp, circle = true)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text("($reason)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (granted) Icon(KynoxIcons.CheckOk, null, tint = StatusGood, modifier = Modifier.size(22.dp))
    }
}
