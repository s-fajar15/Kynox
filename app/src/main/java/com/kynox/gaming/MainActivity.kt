package com.kynox.gaming

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.kynox.gaming.data.settings.AppSettings
import com.kynox.gaming.data.settings.ThemeMode
import com.kynox.gaming.service.ChargingMonitorService
import com.kynox.gaming.service.MonitorService
import com.kynox.gaming.ui.KynoxApp
import com.kynox.gaming.ui.splash.SplashScreen
import com.kynox.gaming.ui.theme.KynoxTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onStart() {
        super.onStart()
        com.kynox.gaming.core.utils.AppVisibility.set(true)
    }

    override fun onStop() {
        com.kynox.gaming.core.utils.AppVisibility.set(false)
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as KynoxApplication).container
        lifecycleScope.launch {
            val startupSettings = container.settingsRepository.settingsFlow.first()
            if (startupSettings.chargingNotification) {
                ChargingMonitorService.enable(this@MainActivity)
            }
            if (startupSettings.monitorEnabled) {
                MonitorService.start(this@MainActivity)
            }
        }
        setContent {
            val settings by container.settingsRepository.settingsFlow.collectAsState(initial = AppSettings())
            val systemDark = isSystemInDarkTheme()
            val useDark = when (settings.themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> systemDark
            }

            DisposableEffect(useDark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { useDark },
                    // Transparan: area di bawah bottom bar mengikuti background layar.
                    navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { useDark }
                )
                // Matikan scrim otomatis Android (3-button nav) supaya benar-benar transparan.
                if (android.os.Build.VERSION.SDK_INT >= 29) {
                    window.isNavigationBarContrastEnforced = false
                }
                onDispose { }
            }

            var showSplash by remember { mutableStateOf(true) }

            KynoxTheme(useDarkTheme = useDark) {
                if (showSplash) {
                    SplashScreen(onFinished = { showSplash = false })
                } else {
                    KynoxApp(container = container)
                }
            }
        }
    }
}
