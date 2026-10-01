package com.kynox.gaming.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kynox.gaming.AppContainer
import com.kynox.gaming.ui.components.BarItem
import com.kynox.gaming.ui.components.KynoxBottomBar
import com.kynox.gaming.ui.navigation.KynoxNavHost
import com.kynox.gaming.ui.navigation.bottomNavItems

@Composable
fun KynoxApp(container: AppContainer) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination

    // Every screen owns its own Scaffold + top bar (which already reserves
    // the status bar inset). Without zeroing this outer Scaffold's own
    // inset, Compose reserves the status bar height *twice* -- once here,
    // once in the screen's TopAppBar.
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            val isTopLevel = bottomNavItems.any { it.dest.route == currentRoute?.route }
            if (isTopLevel || currentRoute == null) {
                val items = bottomNavItems.map { item ->
                    BarItem(
                        label = stringResource(item.labelRes),
                        icon = item.icon,
                        selected = currentRoute?.hierarchy?.any { it.route == item.dest.route } == true,
                        onClick = {
                            navController.navigate(item.dest.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
                KynoxBottomBar(items)
            }
        }
    ) { _ ->
        // Sengaja TIDAK memakai padding dari Scaffold: konten dibiarkan memanjang
        // sampai ke belakang bottom bar yang melayang, jadi tidak ada pita hitam.
        // Tiap layar tab sudah punya contentPadding bawah (74-104dp) supaya item
        // terakhir tidak tertutup bar.
        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
            KynoxNavHost(navController = navController, container = container)
        }
    }
}
