package com.alinam.smartconnect.mobile.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.alinam.smartconnect.mobile.ui.screen.DashboardScreen
import com.alinam.smartconnect.mobile.ui.screen.FileTransferScreen
import com.alinam.smartconnect.mobile.ui.screen.SettingsScreen
import com.alinam.smartconnect.mobile.ui.screen.RemoteControlScreen
import com.alinam.smartconnect.mobile.ui.screen.ConnectionLogsScreen
import com.alinam.smartconnect.mobile.ui.screen.DeveloperScreen

sealed class Screen(val route: String) {
    object Dashboard : Screen("dashboard")
    object FileTransfer : Screen("file_transfer")
    object Settings : Screen("settings")
    object RemoteControl : Screen("remote_control")
    object ConnectionLogs : Screen("connection_logs")
    object Developer : Screen("developer")
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Screen.Dashboard.route) {
        composable(Screen.Dashboard.route) {
            DashboardScreen(navController = navController)
        }
        composable(Screen.FileTransfer.route) {
            FileTransferScreen(navController = navController)
        }
        composable(Screen.Settings.route) {
            SettingsScreen(navController = navController)
        }
        composable(Screen.RemoteControl.route) {
            RemoteControlScreen(navController = navController)
        }
        composable(Screen.ConnectionLogs.route) {
            ConnectionLogsScreen(navController = navController)
        }
        composable(Screen.Developer.route) {
            DeveloperScreen(navController = navController)
        }
    }
}
