package com.kynox.gaming.ui.navigation

import androidx.compose.ui.graphics.vector.ImageVector
import com.kynox.gaming.R
import com.kynox.gaming.ui.theme.KynoxIcons

sealed class Dest(val route: String) {
    data object Home : Dest("home")
    data object Gaming : Dest("gaming")
    data object Control : Dest("control")
    data object Monitor : Dest("monitor")
    data object Device : Dest("device")
    data object Settings : Dest("settings")
    data object Backup : Dest("settings/backup")
    data object QuickPanel : Dest("settings/quick-panel")
    data object Cleaner : Dest("device/cleaner")

    data object DeviceInfo : Dest("device/info")
    data object Cpu : Dest("device/cpu")
    data object Gpu : Dest("device/gpu")
    data object Thermal : Dest("device/thermal")
    data object Battery : Dest("device/battery")
    data object RootManager : Dest("device/root")
    data object Logs : Dest("device/logs")
    data object Profiles : Dest("device/profiles")
    data object Processes : Dest("device/processes")
    data object RefreshRate : Dest("device/refresh-rate")
    data object Debloat : Dest("device/debloat")
    data object About : Dest("device/about")
    data object Session : Dest("device/session")
    data object SessionHistory : Dest("device/session/history")
    data object SessionReport : Dest("device/session/report/{id}") { fun routeFor(id: Long): String = "device/session/report/$id" }
    data object Automation : Dest("device/automation")
    data object Network : Dest("device/network")
    data object Selinux : Dest("device/selinux")
    data object Snapshot : Dest("device/snapshot")
    data object SessionCompare : Dest("device/session/compare")
    data object CommandConsole : Dest("device/console")
    data object Diagnostics : Dest("device/diagnostics")
    data object GameLibrary : Dest("gaming/library")
}

data class BottomNavItem(val dest: Dest, val labelRes: Int, val icon: ImageVector)

val bottomNavItems = listOf(
    BottomNavItem(Dest.Home, R.string.nav_home, KynoxIcons.Overview),
    BottomNavItem(Dest.Monitor, R.string.nav_monitor, KynoxIcons.Monitor),
    BottomNavItem(Dest.Device, R.string.nav_device, KynoxIcons.Device),
    BottomNavItem(Dest.Settings, R.string.nav_more, KynoxIcons.Apps)
)
