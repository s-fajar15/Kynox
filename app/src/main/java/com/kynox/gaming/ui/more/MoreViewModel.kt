package com.kynox.gaming.ui.more

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kynox.gaming.core.root.RootStatus
import com.kynox.gaming.data.adblock.HostsRepository
import com.kynox.gaming.data.adblock.HostsState
import com.kynox.gaming.data.battery.BatteryRepository
import com.kynox.gaming.data.cpu.CpuRepository
import com.kynox.gaming.data.gaming.GamingModeRepository
import com.kynox.gaming.data.gpu.GpuRepository
import com.kynox.gaming.data.profiles.ProfileRepository
import com.kynox.gaming.data.root.RootRepository
import com.kynox.gaming.data.thermal.ThermalRepository
import com.kynox.gaming.domain.model.ProfileType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Nilai ringkas untuk baris layar Lainnya. Null = belum terbaca (ditampilkan kosong atau "--"). */
data class MoreUiState(
    val profile: ProfileType? = null,
    val profileLoaded: Boolean = false,
    val cpuGovernor: String? = null,
    val gpuGovernor: String? = null,
    val gpuSupported: Boolean? = null,
    val cpuTempCelsius: Float? = null,
    val batteryPercent: Int? = null,
    val hosts: HostsState? = null,
    val root: RootStatus? = null,
    val performanceModeActive: Boolean? = null
)

/**
 * Hanya membaca (tidak mengubah apa pun). Nilai cepat (baterai, governor, suhu) diperbarui tiap beberapa detik;
 * nilai yang butuh shell root (profil, hosts, mode performa, root) jauh lebih jarang.
 */
class MoreViewModel(
    private val batteryRepository: BatteryRepository,
    private val cpuRepository: CpuRepository,
    private val gpuRepository: GpuRepository,
    private val thermalRepository: ThermalRepository,
    private val profileRepository: ProfileRepository,
    private val hostsRepository: HostsRepository,
    private val rootRepository: RootRepository,
    private val gamingModeRepository: GamingModeRepository
) : ViewModel() {

    private val _state = MutableStateFlow(MoreUiState())
    val state: StateFlow<MoreUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            var tick = 0
            while (true) {
                com.kynox.gaming.core.utils.AppVisibility.awaitForeground()
                refreshFast()
                if (tick % SLOW_EVERY == 0) refreshSlow()
                tick++
                delay(FAST_INTERVAL_MS)
            }
        }
    }

    private suspend fun refreshFast() {
        val battery = runCatching { batteryRepository.readInfo() }.getOrNull()
        val cpu = runCatching { cpuRepository.readSnapshot() }.getOrNull()
        val gpu = runCatching { gpuRepository.readState() }.getOrNull()
        val temps = runCatching {
            thermalRepository.findTemperaturesFor(
                listOf("cpu", "soc", "cpuss", "apc", "cpu0", "tsens"),
                listOf("gpu", "gfx")
            )
        }.getOrNull()
        _state.update {
            it.copy(
                batteryPercent = battery?.capacityPercent ?: it.batteryPercent,
                cpuGovernor = cpu?.cores?.firstOrNull { c -> c.online != false && !c.governor.isNullOrBlank() }?.governor ?: it.cpuGovernor,
                gpuGovernor = gpu?.governor?.takeIf { g -> g.isNotBlank() } ?: it.gpuGovernor,
                gpuSupported = gpu?.supported ?: it.gpuSupported,
                cpuTempCelsius = temps?.first ?: temps?.second ?: it.cpuTempCelsius
            )
        }
    }

    private suspend fun refreshSlow() {
        val profile = runCatching { profileRepository.activeProfile() }
        val hosts = runCatching { hostsRepository.state() }.getOrNull()
        val root = runCatching { rootRepository.current() }.getOrNull()
        val mode = runCatching { gamingModeRepository.isActive() }.getOrNull()
        _state.update {
            it.copy(
                profile = if (profile.isSuccess) profile.getOrNull() else it.profile,
                profileLoaded = it.profileLoaded || profile.isSuccess,
                hosts = hosts ?: it.hosts,
                root = root ?: it.root,
                performanceModeActive = mode ?: it.performanceModeActive
            )
        }
    }

    private companion object {
        const val FAST_INTERVAL_MS = 3000L
        const val SLOW_EVERY = 5
    }
}
