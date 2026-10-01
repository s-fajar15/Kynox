package com.kynox.gaming

import android.content.Context
import com.kynox.gaming.core.root.RootExecutor
import com.kynox.gaming.core.root.RootExecutorImpl
import com.kynox.gaming.core.sysfs.CapabilityEngine
import com.kynox.gaming.data.backup.BackupStore
import com.kynox.gaming.data.battery.BatteryRepository
import com.kynox.gaming.data.automation.AutomationRepository
import com.kynox.gaming.data.network.NetworkRepository

import com.kynox.gaming.data.cpu.CpuRepository
import com.kynox.gaming.data.device.DeviceInfoRepository
import com.kynox.gaming.data.gaming.ForegroundAppReader
import com.kynox.gaming.data.gaming.GameDetectionRepository
import com.kynox.gaming.data.gaming.GameLibraryRepository
import com.kynox.gaming.data.gaming.GameSessionRepository
import com.kynox.gaming.data.gaming.GamingModeRepository
import com.kynox.gaming.data.gaming.OverlayPermissionRepository
import com.kynox.gaming.data.gpu.GpuRepository
import com.kynox.gaming.data.logs.LogRepository
import com.kynox.gaming.data.monitor.MonitorRecorder
import com.kynox.gaming.data.profiles.ProfileRepository
import com.kynox.gaming.data.root.RootRepository
import com.kynox.gaming.data.settings.SettingsRepository
import com.kynox.gaming.data.thermal.ThermalRepository


/**
 * Hand-rolled dependency container. A DI framework (Hilt/Koin) was left out
 * on purpose: it needs annotation processing (KAPT/KSP), which is one more
 * moving part to get working inside a Termux build environment, and this
 * project's object graph is small enough not to need it.
 */
class AppContainer(context: Context) {
    val rootExecutor: RootExecutor = RootExecutorImpl()
    val capabilityEngine = CapabilityEngine(rootExecutor)
    val rootRepository = RootRepository(rootExecutor)
    val backupStore = BackupStore(context)
    val automationRepository = AutomationRepository(backupStore)
    val networkRepository = NetworkRepository(context)
    val logRepository = LogRepository(context)
    val settingsRepository = SettingsRepository(context)

    val deviceInfoRepository = DeviceInfoRepository(context, capabilityEngine, rootRepository)
    val cpuRepository = CpuRepository(capabilityEngine, rootRepository, rootExecutor, logRepository)
    val gpuRepository = GpuRepository(capabilityEngine, rootRepository, rootExecutor, logRepository)
    val thermalRepository = ThermalRepository(capabilityEngine, rootRepository, rootExecutor, backupStore, logRepository)
    val batteryRepository = BatteryRepository(context, capabilityEngine, rootRepository, rootExecutor, backupStore, logRepository)
    val profileRepository = ProfileRepository(cpuRepository, gpuRepository, thermalRepository, backupStore, logRepository)
    val monitorRecorder = MonitorRecorder(context, batteryRepository, cpuRepository, gpuRepository, thermalRepository, deviceInfoRepository)
    val gameLibraryRepository = GameLibraryRepository(context, backupStore)
    val gamingModeRepository = GamingModeRepository(profileRepository, gameLibraryRepository, backupStore)
    val overlayPermissionRepository = OverlayPermissionRepository(context, rootExecutor, logRepository)
    val foregroundAppReader = ForegroundAppReader(rootExecutor)
    val gameDetectionRepository = GameDetectionRepository(
        rootExecutor, foregroundAppReader, gameLibraryRepository, gamingModeRepository, backupStore, logRepository
    )
    val gameSessionRepository = GameSessionRepository(context, rootExecutor, cpuRepository, gpuRepository, thermalRepository, batteryRepository)
    val processRepository = com.kynox.gaming.data.process.ProcessRepository(rootExecutor)
    val refreshRateRepository = com.kynox.gaming.data.display.RefreshRateRepository(context, rootExecutor, backupStore, logRepository)
    val debloatRepository = com.kynox.gaming.data.debloat.DebloatRepository(context, rootExecutor, logRepository)
}
