package com.kynox.gaming.ui.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kynox.gaming.AppContainer
import com.kynox.gaming.data.backup.BackupParse
import com.kynox.gaming.data.backup.BackupFormat
import com.kynox.gaming.data.settings.PortableSettings
import com.kynox.gaming.ui.components.ConfirmDialog
import com.kynox.gaming.ui.components.DetailTopBar
import com.kynox.gaming.ui.components.KButton
import com.kynox.gaming.ui.components.KOutlinedButton
import com.kynox.gaming.ui.components.SectionCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val MAX_BACKUP_BYTES = 1_000_000

@Composable
fun BackupScreen(container: AppContainer, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<BackupParse.Ok?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            status = try {
                val text = container.configBackupManager.export()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                        ?: error("Tidak bisa membuka berkas tujuan.")
                }
                "Cadangan tersimpan."
            } catch (t: Throwable) {
                "Gagal menyimpan cadangan: ${t.message ?: t.javaClass.simpleName}"
            }
            busy = false
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            status = null
            try {
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Tidak bisa membuka berkas.")
                }
                if (bytes.size > MAX_BACKUP_BYTES) {
                    status = "Berkas terlalu besar untuk cadangan Kynox."
                } else {
                    when (val parsed = BackupFormat.parse(String(bytes, Charsets.UTF_8))) {
                        is BackupParse.Ok -> pending = parsed
                        is BackupParse.Rejected -> status = parsed.reason
                    }
                }
            } catch (t: Throwable) {
                status = "Gagal membaca berkas: ${t.message ?: t.javaClass.simpleName}"
            }
            busy = false
        }
    }

    pending?.let { ok ->
        val file = ok.file
        val rules = file.stores.keys.count { it.startsWith("automation_rule_") }
        val games = file.stores["managed_games"]?.get("packages")?.split(",")?.count { it.isNotBlank() } ?: 0
        val settingsCount = PortableSettings.sanitize(file.settings).size
        val created = if (file.createdAt > 0) SimpleDateFormat("d MMM yyyy HH:mm", Locale("id", "ID")).format(Date(file.createdAt)) else "tidak diketahui"
        ConfirmDialog(
            title = "Pulihkan cadangan?",
            message = "Dibuat $created oleh Kynox ${file.appVersion.ifBlank { "?" }}.\n\n" +
                "• $settingsCount pengaturan\n• $rules aturan Automation\n• $games game dipantau\n\n" +
                "Pengaturan dan daftar yang sama akan ditimpa; aturan Automation diganti sesuai berkas. " +
                "Tidak ada perintah sistem yang dijalankan." +
                if (ok.skippedKeys > 0) "\n\n${ok.skippedKeys} bagian yang tidak dikenal dilewati." else "",
            confirmLabel = "Pulihkan",
            onConfirm = {
                val target = file
                pending = null
                scope.launch {
                    busy = true
                    status = try {
                        val summary = container.configBackupManager.restore(target)
                        "Dipulihkan: ${summary.settingsApplied} pengaturan, ${summary.rulesApplied} aturan. " +
                            "Automation tetap mati sampai kamu menyalakannya lagi."
                    } catch (t: Throwable) {
                        "Gagal memulihkan: ${t.message ?: t.javaClass.simpleName}"
                    }
                    busy = false
                }
            },
            onDismiss = { pending = null }
        )
    }

    Scaffold(topBar = { DetailTopBar("Cadangan & Pulihkan", onBack) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionCard("Simpan cadangan") {
                Text(
                    "Menyimpan pengaturan Kynox, aturan Automation, daftar game beserta profilnya, profil kustom, dan refresh rate per aplikasi ke satu berkas JSON.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                KButton(
                    onClick = {
                        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
                        exportLauncher.launch("kynox-backup-$stamp.json")
                    },
                    enabled = !busy
                ) { Text("Simpan cadangan") }
            }
            SectionCard("Pulihkan dari berkas") {
                Text(
                    "Pilih berkas cadangan Kynox. Isinya diperiksa dulu dan kamu melihat ringkasannya sebelum apa pun diubah. Cadangan dari versi Kynox yang lebih baru ditolak.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                KOutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, enabled = !busy) {
                    Text("Pilih berkas cadangan")
                }
            }
            SectionCard("Yang tidak ikut") {
                Text(
                    "Terapkan saat boot, mode aman, batas pengisian, notifikasi pengisian, dan monitor selalu aktif tidak ikut dipulihkan karena menyalakan service atau memengaruhi proses boot. " +
                        "Status perangkat (profil asli, Game Mode, sesi Automation) juga tidak dicadangkan.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            status?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
