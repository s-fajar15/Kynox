package com.kynox.gaming.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.BatteryStd
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.CompareArrows
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DeveloperBoard
import androidx.compose.material.icons.outlined.DoNotDisturbOn
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SdStorage
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Ikon garis bulat (Outlined). Layar tidak mengimpor Icons.* langsung, jadi satu ikon diganti di satu tempat. */
private fun strokeIcon(name: String, block: PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.8f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathBuilder = block
    ).build()

/** Garis denyut (tab Performa), sama dengan mockup. */
private val PulseIcon: ImageVector = strokeIcon("KynoxPulse") {
    moveTo(3f, 12f)
    lineTo(7f, 12f)
    lineTo(10f, 4.5f)
    lineTo(14f, 19.5f)
    lineTo(17f, 12f)
    lineTo(21f, 12f)
}

private fun PathBuilder.roundSquare(x: Float, y: Float, s: Float, r: Float) {
    moveTo(x + r, y)
    lineTo(x + s - r, y)
    quadTo(x + s, y, x + s, y + r)
    lineTo(x + s, y + s - r)
    quadTo(x + s, y + s, x + s - r, y + s)
    lineTo(x + r, y + s)
    quadTo(x, y + s, x, y + s - r)
    lineTo(x, y + r)
    quadTo(x, y, x + r, y)
    close()
}

/** Empat kotak (tab Lainnya), sama dengan mockup. */
private val GridIcon: ImageVector = strokeIcon("KynoxGrid") {
    roundSquare(4f, 4f, 6.5f, 2f)
    roundSquare(13.5f, 4f, 6.5f, 2f)
    roundSquare(4f, 13.5f, 6.5f, 2f)
    roundSquare(13.5f, 13.5f, 6.5f, 2f)
}

object KynoxIcons {
    // Navigasi utama (bottom bar)
    val Home: ImageVector = Icons.Outlined.Home
    val Pulse: ImageVector = PulseIcon
    val Phone: ImageVector = Icons.Outlined.Smartphone
    val Grid: ImageVector = GridIcon

    // Nama lama, tetap tersambung
    val Overview: ImageVector = Home
    val Monitor: ImageVector = Pulse
    val Control: ImageVector = Icons.Outlined.Tune
    val Device: ImageVector = Phone
    val Settings: ImageVector = Icons.Outlined.Settings
    val DarkMode: ImageVector = Icons.Outlined.DarkMode
    val PlayArrow: ImageVector = Icons.Outlined.PlayArrow
    val Shield: ImageVector = Icons.Outlined.Shield
    val DoNotDisturb: ImageVector = Icons.Outlined.DoNotDisturbOn
    val Brightness: ImageVector = Icons.Outlined.WbSunny
    val CheckOk: ImageVector = Icons.Outlined.CheckCircle

    // Subsistem
    val Cpu: ImageVector = Icons.Outlined.Memory
    val Gpu: ImageVector = Icons.Outlined.DeveloperBoard
    val Ram: ImageVector = Icons.Outlined.SdStorage
    val Battery: ImageVector = Icons.Outlined.BatteryStd
    val Charging: ImageVector = Icons.Outlined.BatteryChargingFull
    val Thermal: ImageVector = Icons.Outlined.Thermostat
    val Root: ImageVector = Icons.Outlined.Terminal
    val Processes: ImageVector = Icons.AutoMirrored.Outlined.ListAlt
    val Display: ImageVector = Icons.Outlined.Speed
    val RefreshRate: ImageVector = Icons.Outlined.Refresh
    val Profiles: ImageVector = Icons.Outlined.Tune
    val Logs: ImageVector = Icons.Outlined.History
    val Notifications: ImageVector = Icons.Outlined.Notifications
    val Info: ImageVector = Icons.Outlined.Info
    val Session: ImageVector = Icons.AutoMirrored.Outlined.ShowChart
    val Apps: ImageVector = Icons.Outlined.Apps
    val Debloat: ImageVector = Icons.Outlined.CleaningServices
    val About: ImageVector = Icons.Outlined.Info

    // Tambahan untuk layar Lainnya
    val Trash: ImageVector = Icons.Outlined.Delete
    val Cleaner: ImageVector = Icons.Outlined.CleaningServices
    val Layers: ImageVector = Icons.Outlined.Layers
    val Backup: ImageVector = Icons.Outlined.Backup
    val Network: ImageVector = Icons.Outlined.Wifi
    val Security: ImageVector = Icons.Outlined.Security
    val Snapshot: ImageVector = Icons.Outlined.PhotoCamera
    val Compare: ImageVector = Icons.Outlined.CompareArrows
    val Report: ImageVector = Icons.Outlined.Description
    val Library: ImageVector = Icons.Outlined.Apps

    // Aksi
    val Back: ImageVector = Icons.AutoMirrored.Outlined.ArrowBack
    val Chevron: ImageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight
    val Refresh: ImageVector = Icons.Outlined.Refresh
    val Search: ImageVector = Icons.Outlined.Search
    val MoveUp: ImageVector = Icons.Outlined.KeyboardArrowUp
    val MoveDown: ImageVector = Icons.Outlined.KeyboardArrowDown
}
