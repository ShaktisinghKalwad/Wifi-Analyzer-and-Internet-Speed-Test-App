package com.srk.wifianalyzer.presentation

import androidx.compose.runtime.Composable
import android.net.Uri
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Settings
import com.srk.wifianalyzer.presentation.apdetails.AccessPointDetailsScreen
import com.srk.wifianalyzer.presentation.channels.ChannelScreen
import com.srk.wifianalyzer.presentation.scanner.ScannerScreen
import com.srk.wifianalyzer.presentation.speedtest.SpeedTestScreen
import com.srk.wifianalyzer.settings.presentation.SettingsScreen

@Composable
fun AppRoot() {
    val navController = rememberNavController()

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute != "ap/{bssid}"

    val tabs = listOf(
        NavTab(route = "scanner", label = "Scanner", icon = Icons.Filled.Wifi),
        NavTab(route = "channels", label = "Channels", icon = Icons.Filled.Tune),
        NavTab(route = "speedtest", label = "Speed", icon = Icons.Filled.Speed),
        NavTab(route = "settings", label = "Settings", icon = Icons.Filled.Settings),
    )

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    tabs.forEach { tab ->
                        val selected = currentRoute == tab.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo("scanner") { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(imageVector = tab.icon, contentDescription = tab.label) },
                            label = { Text(text = tab.label) },
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "scanner",
            modifier = Modifier.padding(padding)
        ) {
            composable("scanner") {
                ScannerScreen(
                    onOpenApDetails = { bssid ->
                        navController.navigate("ap/${Uri.encode(bssid)}")
                    }
                )
            }
            composable("channels") {
                ChannelScreen()
            }
            composable("speedtest") {
                SpeedTestScreen()
            }
            composable("settings") {
                SettingsScreen()
            }
            composable(
                route = "ap/{bssid}",
                arguments = listOf(navArgument("bssid") { type = NavType.StringType })
            ) {
                AccessPointDetailsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenApDetails = { bssid ->
                        navController.navigate("ap/${Uri.encode(bssid)}")
                    },
                )
            }
        }
    }
}

private data class NavTab(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)
