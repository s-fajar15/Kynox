package com.kynox.gaming.ui.theme

import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.DoNotDisturbOn
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.BatteryStd
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.DeveloperBoard
import androidx.compose.material.icons.outlined.DeviceHub
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SdStorage
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * One outlined set, one stroke weight. Screens never import Icons.* directly,
 * so an icon is swapped in exactly one place.
 */
object KynoxIcons {
    // Primary navigation
    val Overview: ImageVector = Icons.Outlined.Dashboard
    val Monitor: ImageVector = Icons.AutoMirrored.Outlined.ShowChart
    val Control: ImageVector = Icons.Outlined.Tune
    val Device: ImageVector = Icons.Outlined.Smartphone
    val Settings: ImageVector = Icons.Outlined.Settings
    val DarkMode: ImageVector = Icons.Outlined.DarkMode
    val PlayArrow: ImageVector = Icons.Outlined.PlayArrow
    val Shield: ImageVector = Icons.Outlined.Shield
    val DoNotDisturb: ImageVector = Icons.Outlined.DoNotDisturbOn
    val Brightness: ImageVector = Icons.Outlined.WbSunny
    val CheckOk: ImageVector = Icons.Outlined.CheckCircle

    // Subsystems
    val Cpu: ImageVector = Icons.Outlined.Memory
    val Gpu: ImageVector = Icons.Outlined.DeveloperBoard
    val Ram: ImageVector = Icons.Outlined.SdStorage
    val Battery: ImageVector = Icons.Outlined.BatteryStd
    val Charging: ImageVector = Icons.Outlined.BatteryChargingFull
    val Thermal: ImageVector = Icons.Outlined.Thermostat
    val Root: ImageVector = Icons.Outlined.Terminal
    val Processes: ImageVector = Icons.AutoMirrored.Outlined.ListAlt
    val Display: ImageVector = Icons.Outlined.Speed
    val RefreshRate: ImageVector = Icons.Outlined.Speed
    val Profiles: ImageVector = Icons.Outlined.Layers
    val Logs: ImageVector = Icons.Outlined.History
    val Notifications: ImageVector = Icons.Outlined.Notifications
    val Info: ImageVector = Icons.Outlined.Info
    val Session: ImageVector = Icons.AutoMirrored.Outlined.ShowChart
    val Apps: ImageVector = Icons.Outlined.Apps
    val Debloat: ImageVector = Icons.Outlined.CleaningServices
    val About: ImageVector = Icons.Outlined.Info

    // Actions
    val Back: ImageVector = Icons.AutoMirrored.Outlined.ArrowBack
    val Chevron: ImageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight
    val Refresh: ImageVector = Icons.Outlined.Refresh
    val Search: ImageVector = Icons.Outlined.Search
}
