package com.kynox.gaming.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kynox.gaming.core.root.RootStatus
import com.kynox.gaming.data.battery.BatteryRepository
import com.kynox.gaming.data.cpu.CpuRepository
import com.kynox.gaming.data.device.DeviceInfoRepository
import com.kynox.gaming.data.gpu.GpuRepository
import com.kynox.gaming.data.root.RootRepository
import com.kynox.gaming.data.settings.SettingsRepository
import com.kynox.gaming.data.thermal.ThermalRepository
import com.kynox.gaming.domain.model.BatteryInfo
import com.kynox.gaming.domain.model.CpuSnapshot
import com.kynox.gaming.domain.model.GpuState
import com.kynox.gaming.domain.model.RamUsage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

private const val HISTORY_SIZE = 40

data class DashboardUiState(
    val loading: Boolean = true,
    val battery: BatteryInfo? = null,
    val cpu: CpuSnapshot? = null,
    val gpu: GpuState? = null,
    val ram: RamUsage? = null,
    val cpuTempCelsius: Float? = null,
    val gpuTempCelsius: Float? = null,
    val thermalEngineActive: Boolean = true,
    val rootStatus: RootStatus? = null,
    val cpuUsageHistory: List<Float> = emptyList(),
    val ramUsageHistory: List<Float> = emptyList(),
    val batteryCurrentHistory: List<Float> = emptyList()
)

class DashboardViewModel(
    private val batteryRepository: BatteryRepository,
    private val cpuRepository: CpuRepository,
    private val gpuRepository: GpuRepository,
    private val thermalRepository: ThermalRepository,
    private val deviceInfoRepository: DeviceInfoRepository,
    private val settingsRepository: SettingsRepository,
    private val rootRepository: RootRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(rootStatus = rootRepository.current())
            val intervalMs = settingsRepository.settingsFlow.first().refreshIntervalMs
            var tick = 0
            while (true) {
                com.kynox.gaming.core.utils.AppVisibility.awaitForeground()
                // Checking whether the thermal engine is disabled costs an
                // extra root shell call (see ThermalRepository.disableState),
                // and that state rarely flips while this screen is open, so
                // it is refreshed far less often than everything else here.
                val checkThermalState = tick % THERMAL_STATE_CHECK_EVERY == 0
                refreshOnce(checkThermalState)
                tick++
                delay(intervalMs)
            }
        }
    }

    private suspend fun refreshOnce(checkThermalState: Boolean) {
        val battery = batteryRepository.readInfo()
        val cpu = cpuRepository.readSnapshot()
        val gpu = gpuRepository.readState()
        val ram = deviceInfoRepository.readRamUsage()
        val cpuGpuTemps = thermalRepository.findTemperaturesFor(
            listOf("cpu", "soc", "cpuss", "apc", "cpu0", "tsens"),
            listOf("gpu", "gfx")
        )
        val cpuTemp = cpuGpuTemps.first
        val gpuTemp = gpu.temperatureCelsius ?: cpuGpuTemps.second
        val thermalActive = if (checkThermalState) {
            !thermalRepository.disableState().active
        } else {
            _uiState.value.thermalEngineActive
        }

        _uiState.value = _uiState.value.copy(
            loading = false,
            battery = battery,
            cpu = cpu,
            gpu = gpu,
            ram = ram,
            cpuTempCelsius = cpuTemp,
            gpuTempCelsius = gpuTemp,
            thermalEngineActive = thermalActive,
            cpuUsageHistory = appendHistory(_uiState.value.cpuUsageHistory, cpu.overallUsagePercent ?: 0f),
            ramUsageHistory = appendHistory(_uiState.value.ramUsageHistory, ram.usedPercent),
            batteryCurrentHistory = appendHistory(
                _uiState.value.batteryCurrentHistory,
                (battery.currentMicroAmps?.let { kotlin.math.abs(it) / 1000f }) ?: 0f
            )
        )
    }

    private fun appendHistory(history: List<Float>, value: Float): List<Float> {
        val updated = history + value
        return if (updated.size > HISTORY_SIZE) updated.takeLast(HISTORY_SIZE) else updated
    }

    private companion object {
        const val THERMAL_STATE_CHECK_EVERY = 5
    }
}
