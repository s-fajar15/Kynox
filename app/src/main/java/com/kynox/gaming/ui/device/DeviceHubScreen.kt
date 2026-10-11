package com.kynox.gaming.ui.device

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kynox.gaming.AppContainer
import com.kynox.gaming.domain.model.DeviceInfo
import com.kynox.gaming.ui.components.DeviceHeroCard
import com.kynox.gaming.ui.components.GenericViewModelFactory
import com.kynox.gaming.ui.components.KOutlinedButton
import com.kynox.gaming.ui.components.KynoxCardSpacing
import com.kynox.gaming.ui.components.KynoxListPadding
import com.kynox.gaming.ui.components.KynoxTopBar
import com.kynox.gaming.ui.components.MetricRow
import com.kynox.gaming.ui.components.SectionCard
import com.kynox.gaming.ui.components.fmtDec
import com.kynox.gaming.ui.components.fmtGbValue
import com.kynox.gaming.ui.components.kernelShort
import com.kynox.gaming.ui.components.ramLabel
import com.kynox.gaming.ui.navigation.Dest

/**
 * Tab Perangkat: HANYA informasi perangkat (tanpa daftar fitur). Kartu identitas, lalu bagian Prosesor dan GPU,
 * Memori dan penyimpanan, Layar dan sistem sebagai daftar label-nilai. Rincian lengkap ada di "Informasi Perangkat".
 */
@Composable
fun DeviceHubScreen(container: AppContainer, onNavigate: (Dest) -> Unit) {
    val vm: DeviceInfoViewModel = viewModel(factory = GenericViewModelFactory { DeviceInfoViewModel(container.deviceInfoRepository) })
    val info by vm.info.collectAsState()
    // Frekuensi tertinggi: dibaca sekali dari sysfs CPU (batas maksimum tiap core), bukan frekuensi saat ini.
    val maxFreqKhz by produceState<Int?>(initialValue = null) {
        value = runCatching { container.cpuRepository.readSnapshot().cores.mapNotNull { it.maxFreqKhz }.maxOrNull() }.getOrNull()
    }

    Scaffold(topBar = { KynoxTopBar(title = "Perangkat") }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = KynoxListPadding,
            verticalArrangement = KynoxCardSpacing
        ) {
            item(key = "hero", contentType = "hero") { DeviceHeroCard(info) }
            item(key = "cpu", contentType = "section") {
                SectionCard(title = "Prosesor dan GPU") {
                    MetricRow("Chipset", socName(info))
                    MetricRow("Core", coreText(info))
                    MetricRow("Frekuensi tertinggi", maxFreqKhz?.let { fmtDec(it / 1_000_000.0, 2) + " GHz" } ?: "--")
                    MetricRow("GPU", gpuText(info))
                }
            }
            item(key = "memory", contentType = "section") {
                SectionCard(title = "Memori dan penyimpanan") {
                    MetricRow("RAM", info?.totalRamBytes?.let { ramLabel(it)?.removeSuffix(" RAM") } ?: "--")
                    MetricRow("Swap (zram)", swapText(info))
                    MetricRow("Penyimpanan", storageText(info))
                }
            }
            item(key = "system", contentType = "section") {
                SectionCard(title = "Layar dan sistem") {
                    MetricRow("Layar", displayText(info))
                    MetricRow("Android", info?.let { "${it.androidVersion} · SDK ${it.androidSdk}" } ?: "--")
                    MetricRow("Kernel", info?.let { kernelShort(it.kernelVersion) } ?: "--")
                    MetricRow("SELinux", info?.selinuxStatus?.takeIf { it.isNotBlank() } ?: "--")
                }
            }
            item(key = "more", contentType = "button") {
                KOutlinedButton(onClick = { onNavigate(Dest.DeviceInfo) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Informasi perangkat lengkap")
                }
            }
        }
    }
}

private fun valid(v: String?): String? = v?.takeIf { it.isNotBlank() && !it.equals("unknown", true) && it != "Not available" }

private fun socName(info: DeviceInfo?): String {
    if (info == null) return "--"
    return listOfNotNull(valid(info.socManufacturer), valid(info.socModel)).joinToString(" ")
        .ifBlank { valid(info.hardware) ?: "--" }
}

private fun coreText(info: DeviceInfo?): String {
    if (info == null) return "--"
    return listOfNotNull(
        info.cpuCoreCount.takeIf { it > 0 }?.toString(),
        valid(info.cpuArchitecture)
    ).joinToString(" · ").ifBlank { "--" }
}

private fun gpuText(info: DeviceInfo?): String {
    if (info == null) return "--"
    // GL_VERSION berbentuk "OpenGL ES 3.2 V@..."; yang ditampilkan hanya nomor versinya.
    val gles = valid(info.glEsVersion)
        ?.let { Regex("OpenGL ES (\\d+(?:\\.\\d+)?)").find(it)?.groupValues?.getOrNull(1) }
        ?.let { "GLES $it" }
    return listOfNotNull(valid(info.gpuRenderer), gles).joinToString(" · ").ifBlank { "--" }
}

private fun swapText(info: DeviceInfo?): String = when {
    info == null -> "--"
    info.swapTotalBytes <= 0 -> "Tidak ada"
    else -> fmtGbValue(info.swapTotalBytes, 0) + " GB"
}

private fun storageText(info: DeviceInfo?): String {
    val s = info?.storage?.takeIf { it.totalBytes > 0 } ?: return "--"
    return "${fmtGbValue(s.usedBytes, 0)} dari ${fmtGbValue(s.totalBytes, 0)} GB"
}

private fun displayText(info: DeviceInfo?): String {
    val d = info?.display ?: return "--"
    val hz = if (d.refreshRateHz > 0f) "%.0f Hz".format(d.refreshRateHz) else null
    return listOfNotNull(valid(d.resolution), hz).joinToString(" · ").ifBlank { "--" }
}
