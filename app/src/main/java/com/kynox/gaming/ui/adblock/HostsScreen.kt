package com.kynox.gaming.ui.adblock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kynox.gaming.AppContainer
import com.kynox.gaming.data.adblock.HostsOutcome
import com.kynox.gaming.data.adblock.HostsSource
import com.kynox.gaming.data.adblock.HostsState
import com.kynox.gaming.data.adblock.managerName
import com.kynox.gaming.ui.components.ConfirmDialog
import com.kynox.gaming.ui.components.DetailTopBar
import com.kynox.gaming.ui.components.KButton
import com.kynox.gaming.ui.components.KOutlinedButton
import com.kynox.gaming.ui.components.MessageBanner
import com.kynox.gaming.ui.components.MetricRow
import com.kynox.gaming.ui.components.SectionCard
import com.kynox.gaming.ui.components.StatusPill
import com.kynox.gaming.ui.components.UnsupportedNotice
import com.kynox.gaming.ui.components.UnsupportedPill
import com.kynox.gaming.ui.theme.StatusGood
import com.kynox.gaming.ui.theme.StatusWarning
import kotlinx.coroutines.launch

private enum class HostsConfirm { INSTALL, REMOVE }

@Composable
fun HostsScreen(container: AppContainer, onBack: () -> Unit) {
    val repo = container.hostsRepository
    val scope = rememberCoroutineScope()

    var state by remember { mutableStateOf<HostsState?>(null) }
    val sources = remember { mutableStateListOf<HostsSource>() }
    var whitelist by remember { mutableStateOf("") }
    var blacklist by remember { mutableStateOf("") }
    var newUrl by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<String?>(null) }
    var outcome by remember { mutableStateOf<HostsOutcome?>(null) }
    var confirm by remember { mutableStateOf<HostsConfirm?>(null) }

    suspend fun reload() { state = repo.state() }
    LaunchedEffect(Unit) {
        reload()
        sources.clear(); sources.addAll(repo.sources())
        whitelist = repo.whitelistText(); blacklist = repo.blacklistText()
    }

    fun run(block: suspend () -> HostsOutcome) {
        scope.launch {
            busy = true; outcome = null
            outcome = block()
            progress = null
            reload()
            busy = false
        }
    }

    Scaffold(topBar = { DetailTopBar(title = "Blokir Iklan: Hosts", onBack = onBack, info = HOSTS_NOTES) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = com.kynox.gaming.ui.components.KynoxListPadding,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            outcome?.let { o -> item { MessageBanner(o.message, onDismiss = { outcome = null }, isError = !o.success) } }
            progress?.let { p -> item { Text(p, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }

            item {
                val s = state
                SectionCard(
                    title = "Status",
                    trailing = {
                        when {
                            s == null -> {}
                            !s.rootAvailable || !s.supported -> UnsupportedPill()
                            s.markedForRemoval -> StatusPill("Menunggu reboot (hapus)", StatusWarning)
                            !s.installed -> StatusPill("Belum dipasang", MaterialTheme.colorScheme.onSurfaceVariant)
                            s.needsReboot -> StatusPill("Menunggu reboot", StatusWarning)
                            else -> StatusPill("Aktif", StatusGood)
                        }
                    }
                ) {
                    when {
                        s == null -> Text("Memeriksa…", style = MaterialTheme.typography.bodyMedium)
                        !s.rootAvailable -> UnsupportedNotice("Butuh akses root.")
                        !s.supported -> UnsupportedNotice("Blokir Iklan butuh root Magisk, KernelSU, atau APatch.")
                        else -> {
                            MetricRow("Manajer root", managerName(s.provider))
                            MetricRow("Modul", if (s.installed) "Terpasang" else "Belum dipasang")
                            MetricRow("Termount di /system/etc/hosts", if (s.mounted) "Ya" else "Belum")
                            MetricRow("Domain diblokir", s.moduleBlocked?.toString() ?: "-")
                        }
                    }
                }
            }

            if (state?.rootAvailable == true && state?.supported == true) {
                item {
                    SectionCard(title = "Aksi") {
                        val s = state
                        if (s?.installed != true || s.markedForRemoval) {
                            KButton(onClick = { confirm = HostsConfirm.INSTALL }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Pasang modul") }
                        } else {
                            KButton(
                                onClick = { run { repo.update { progress = it } } },
                                enabled = !busy, modifier = Modifier.fillMaxWidth()
                            ) { Text("Perbarui daftar") }
                            Spacer(Modifier.height(8.dp))
                            KOutlinedButton(onClick = { run { repo.disable() } }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Matikan blokir (kembalikan hosts asli)") }
                            Spacer(Modifier.height(8.dp))
                            KOutlinedButton(onClick = { confirm = HostsConfirm.REMOVE }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Hapus modul") }
                        }
                    }
                }

                item {
                    SectionCard(title = "Sumber daftar") {
                        sources.forEachIndexed { i, src ->
                            com.kynox.gaming.ui.components.KynoxSwitchRow(
                                title = src.url.removePrefix("https://"),
                                checked = src.enabled,
                                onCheckedChange = { v ->
                                    sources[i] = src.copy(enabled = v)
                                    scope.launch { repo.saveSources(sources.toList()) }
                                }
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = newUrl, onValueChange = { newUrl = it.take(300) },
                            label = { Text("Tambah sumber (https://…)") }, singleLine = true,
                            isError = newUrl.isNotBlank() && !repo.isValidSourceUrl(newUrl.trim()),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        KOutlinedButton(
                            onClick = {
                                val u = newUrl.trim()
                                if (repo.isValidSourceUrl(u) && sources.none { it.url == u }) {
                                    sources.add(HostsSource(u, true)); newUrl = ""
                                    scope.launch { repo.saveSources(sources.toList()) }
                                }
                            },
                            enabled = repo.isValidSourceUrl(newUrl.trim())
                        ) { Text("Tambah") }
                    }
                }

                item {
                    SectionCard(title = "Daftar putih & hitam", info = "Satu domain per baris. Daftar putih membuka domain yang terblokir; daftar hitam menambah domain yang diblokir. Berlaku pada pembaruan berikutnya.") {
                        OutlinedTextField(value = whitelist, onValueChange = { whitelist = it }, label = { Text("Daftar putih (jangan blokir)") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = blacklist, onValueChange = { blacklist = it }, label = { Text("Daftar hitam (selalu blokir)") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        KOutlinedButton(onClick = { scope.launch { repo.saveLists(whitelist, blacklist); outcome = HostsOutcome(true, "Daftar disimpan. Jalankan Perbarui daftar untuk menerapkannya.") } }) { Text("Simpan daftar") }
                    }
                }
            }

        }
    }

    confirm?.let { c ->
        ConfirmDialog(
            title = if (c == HostsConfirm.INSTALL) "Pasang modul Hosts?" else "Hapus modul Hosts?",
            message = if (c == HostsConfirm.INSTALL)
                "Kynox akan membuat modul kynox_hosts di /data/adb/modules berisi file hosts. Butuh reboot sekali agar termount. Isi awalnya sama dengan hosts asli (belum memblokir apa pun)."
            else
                "Pemblokiran dimatikan sekarang, dan modul ditandai untuk dihapus Magisk pada reboot berikutnya.",
            confirmLabel = if (c == HostsConfirm.INSTALL) "Pasang" else "Hapus",
            onConfirm = { confirm = null; if (c == HostsConfirm.INSTALL) run { repo.install() } else run { repo.remove() } },
            onDismiss = { confirm = null }
        )
    }
}

/** Catatan cara kerja dan batas, dibuka lewat ikon (i) di bar atas. */
private const val HOSTS_NOTES =
        "• Kynox membuat modul kecil (kynox_hosts) yang didukung Magisk, KernelSU, dan APatch. Manajer modulnya memasangnya di atas /system/etc/hosts saat boot; partisi sistem tidak disentuh. Reboot dibutuhkan sekali setelah memasang; pembaruan berikutnya langsung berlaku.\n" +
        "• Daftar diunduh aplikasi hanya saat kamu menekan Perbarui daftar (butuh izin internet). Tidak ada unduhan di latar belakang.\n" +
        "• Kynox memberi label SELinux hanya pada file hosts buatannya agar bisa dibaca aplikasi. Kebijakan SELinux tidak diubah.\n" +
        "• Memblokir di tingkat domain. Iklan yang disajikan dari domain yang sama dengan kontennya (misalnya video YouTube) tidak terblokir, dan aplikasi yang memakai DNS sendiri melewatinya.\n" +
        "• Jika sudah memakai modul pemblokir hosts lain, jangan pasang keduanya: keduanya memasang file hosts yang sama.\n" +
        "• Di KernelSU/APatch, bila setelah reboot status masih \"Belum\" termount, pastikan mode mount modul aktif di manager (KernelSU versi baru butuh metamodule mount, mis. overlayfs/magic mount).\n" +
        "• Belum diuji di perangkat; jika ada yang tidak beres, Matikan blokir dan Hapus modul mengembalikan semuanya."
