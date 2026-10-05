package com.kynox.gaming.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.kynox.gaming.AppContainer
import com.kynox.gaming.ui.about.AboutScreen
import com.kynox.gaming.ui.battery.BatteryScreen
import com.kynox.gaming.ui.control.ControlScreen
import com.kynox.gaming.ui.cpu.CpuScreen
import com.kynox.gaming.ui.dashboard.DashboardScreen
import com.kynox.gaming.ui.debloat.DebloatScreen
import com.kynox.gaming.ui.device.DeviceHubScreen
import com.kynox.gaming.ui.device.DeviceInfoScreen
import com.kynox.gaming.ui.gaming.GameLibraryScreen
import com.kynox.gaming.ui.gaming.GamingScreen
import com.kynox.gaming.ui.gaming.SessionHistoryScreen
import com.kynox.gaming.ui.gaming.SessionReportScreen
import com.kynox.gaming.ui.gaming.SessionScreen
import com.kynox.gaming.ui.gpu.GpuScreen
import com.kynox.gaming.ui.logs.LogsScreen
import com.kynox.gaming.ui.monitor.MonitorScreen
import com.kynox.gaming.ui.profiles.ProfileScreen
import com.kynox.gaming.ui.process.ProcessesScreen
import com.kynox.gaming.ui.display.RefreshRateScreen
import com.kynox.gaming.ui.root.RootScreen
import com.kynox.gaming.ui.settings.SettingsScreen
import com.kynox.gaming.ui.thermal.ThermalScreen
import com.kynox.gaming.ui.tools.AutomationScreen
import com.kynox.gaming.ui.tools.NetworkMonitorScreen
import com.kynox.gaming.ui.tools.SelinuxMonitorScreen
import com.kynox.gaming.ui.tools.KynoxSnapshotScreen
import com.kynox.gaming.ui.tools.SessionCompareScreen
import com.kynox.gaming.ui.tools.CommandConsoleScreen
import com.kynox.gaming.ui.tools.DiagnosticsScreen

@Composable
fun KynoxNavHost(navController: NavHostController, container: AppContainer) {
    NavHost(navController = navController, startDestination = Dest.Home.route) {
        composable(Dest.Home.route) { DashboardScreen(container, onOpenStatus = { navController.navigate(Dest.Diagnostics.route) }) }
        composable(Dest.Gaming.route) {
            GamingScreen(
                container,
                onBack = { navController.popBackStack() },
                onNavigateToLibrary = { navController.navigate(Dest.GameLibrary.route) }
            )
        }
        composable(Dest.Control.route) {
            ControlScreen(container, onOpenBattery = { navController.navigate(Dest.Battery.route) })
        }
        composable(Dest.Monitor.route) { MonitorScreen(container) }
        composable(Dest.Device.route) {
            DeviceHubScreen(container, onNavigate = { dest -> navController.navigate(dest.route) })
        }
        composable(Dest.Settings.route) {
            SettingsScreen(
                container,
                onNavigateAbout = { navController.navigate(Dest.About.route) },
                onNavigateGaming = { navController.navigate(Dest.Gaming.route) },
                onNavigateGameLibrary = { navController.navigate(Dest.GameLibrary.route) },
                onNavigateControl = { navController.navigate(Dest.Control.route) },
                onNavigateQuickPanel = { navController.navigate(Dest.QuickPanel.route) },
                onNavigateBackup = { navController.navigate(Dest.Backup.route) }
            )
        }

        composable(Dest.Backup.route) { com.kynox.gaming.ui.backup.BackupScreen(container, onBack = { navController.popBackStack() }) }
        composable(Dest.QuickPanel.route) {
            com.kynox.gaming.ui.quickpanel.QuickPanelScreen(
                container,
                onBack = { navController.popBackStack() },
                onOpenProfiles = { navController.navigate(Dest.Profiles.route) },
                onOpenRefreshRate = { navController.navigate(Dest.RefreshRate.route) },
                onOpenThermal = { navController.navigate(Dest.Thermal.route) }
            )
        }

        composable(Dest.Cleaner.route) {
            com.kynox.gaming.ui.cleaner.CleanerScreen(container, onBack = { navController.popBackStack() })
        }

        composable(Dest.DeviceInfo.route) { DeviceInfoScreen(container, onBack = { navController.popBackStack() }) }
        composable(Dest.Cpu.route) { CpuScreen(container, onBack = { navController.popBackStack() }) }
        composable(Dest.Gpu.route) { GpuScreen(container, onBack = { navController.popBackStack() }) }
        composable(Dest.Thermal.route) { ThermalScreen(container, onBack = { navController.popBackStack() }) }
        composable(Dest.Battery.route) { BatteryScreen(container, onBack = { navController.popBackStack() }) }
        composable(Dest.RootManager.route) { RootScreen(container, onBack = { navController.popBackStack() }) }
        composable(Dest.Logs.route) { LogsScreen(container, onBack = { navController.popBackStack() }) }
        composable(Dest.Profiles.route) { ProfileScreen(container, onBack = { navController.popBackStack() }) }
        composable(Dest.Processes.route) { ProcessesScreen(container, onBack = { navController.popBackStack() }) }
        composable(Dest.RefreshRate.route) { RefreshRateScreen(container, onBack = { navController.popBackStack() }) }
        composable(Dest.Debloat.route) { DebloatScreen(container, onBack = { navController.popBackStack() }) }
        composable(Dest.About.route) { AboutScreen(container, onBack = { navController.popBackStack() }) }
        composable(Dest.Automation.route) { AutomationScreen(container, onBack = { navController.popBackStack() }) }
        composable(Dest.Network.route) { NetworkMonitorScreen(container, onBack = { navController.popBackStack() }) }
        composable(Dest.Selinux.route) { SelinuxMonitorScreen(container, onBack = { navController.popBackStack() }) }
        composable(Dest.Snapshot.route) { KynoxSnapshotScreen(container, onBack = { navController.popBackStack() }) }
        composable(Dest.SessionCompare.route) { SessionCompareScreen(container, onBack = { navController.popBackStack() }) }
        composable(Dest.CommandConsole.route) { CommandConsoleScreen(container, onBack = { navController.popBackStack() }) }
        composable(Dest.Diagnostics.route) { DiagnosticsScreen(container, onBack = { navController.popBackStack() }) }
        composable(Dest.GameLibrary.route) { GameLibraryScreen(container, onBack = { navController.popBackStack() }) }
        composable(Dest.Session.route) {
            SessionScreen(
                container,
                onBack = { navController.popBackStack() },
                onOpenReport = { navController.navigate(Dest.SessionHistory.route) }
            )
        }
        composable(Dest.SessionHistory.route) {
            SessionHistoryScreen(
                container,
                onBack = { navController.popBackStack() },
                onOpenReport = { id -> navController.navigate(Dest.SessionReport.routeFor(id)) }
            )
        }
        composable(
            Dest.SessionReport.route,
            arguments = listOf(navArgument("id") { type = NavType.LongType })
        ) { entry ->
            SessionReportScreen(
                container,
                sessionId = entry.arguments?.getLong("id") ?: 0L,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
