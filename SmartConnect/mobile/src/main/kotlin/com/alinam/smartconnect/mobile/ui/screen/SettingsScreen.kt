package com.alinam.smartconnect.mobile.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.alinam.smartconnect.mobile.ui.component.GlassCard
import com.alinam.smartconnect.mobile.ui.navigation.Screen
import com.alinam.smartconnect.mobile.ui.theme.*
import com.alinam.smartconnect.mobile.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val isOptimized by viewModel.isOptimized.collectAsStateWithLifecycle()
    val isMiui by viewModel.isMiui.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("تنظیمات", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Dark80, titleContentColor = Color.White)
            )
        },
        containerColor = Dark90
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // BATTERY OPTIMIZATION
            if (isOptimized) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = WarningOrange.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.BatterySaver, null, tint = WarningOrange)
                            Spacer(Modifier.width(8.dp))
                            Text("بهینه‌سازی باتری فعال است", color = WarningOrange, fontWeight = FontWeight.Bold)
                        }
                        Text("برای عملکرد بهتر، بهینه‌سازی باتری را غیرفعال کنید",
                            color = Color.White.copy(alpha = 0.7f))
                        Button(
                            onClick = { viewModel.requestIgnoreBatteryOptimizations() },
                            colors = ButtonDefaults.buttonColors(containerColor = WarningOrange)
                        ) { Text("غیرفعال کردن", color = Color.White) }
                    }
                }
            }

            if (isMiui) {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PhoneAndroid, null, tint = Cyan)
                            Spacer(Modifier.width(8.dp))
                            Text("دستگاه MIUI/HyperOS", color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                        Text("برای اجرای خودکار پس از ریبوت، Auto Start را فعال کنید",
                            color = Color.White.copy(alpha = 0.7f))
                        OutlinedButton(onClick = { viewModel.openAutoStartSettings() }) {
                            Text("تنظیمات Auto Start", color = Cyan)
                        }
                    }
                }
            }

            // NAVIGATION
            SettingsNavItem(
                icon = Icons.Default.DeveloperMode, label = "حالت توسعه‌دهنده", sub = "تست بلوتوث و ابزارها"
            ) { navController.navigate(Screen.Developer.route) }

            SettingsNavItem(
                icon = Icons.Default.History, label = "تاریخچه اتصال", sub = "لاگ اتصال و قطع‌شدن‌ها"
            ) { navController.navigate(Screen.ConnectionLogs.route) }

            // APP INFO
            GlassCard(modifier = Modifier.fillMaxWidth(), padding = 12.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("SmartConnect", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("نسخه 1.0.0 | ساخته شده برای POCO X3 Pro + Telzeal TC4G",
                        color = Color.White.copy(alpha = 0.5f))
                }
            }
        }
    }
}

@Composable
fun SettingsNavItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    sub: String,
    onClick: () -> Unit
) {
    GlassCard(modifier = Modifier.fillMaxWidth().then(
        Modifier.clickable(onClick = onClick)
    ), padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, tint = Purple, modifier = Modifier.size(24.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(label, color = Color.White, fontWeight = FontWeight.Medium)
                Text(sub, color = Color.White.copy(alpha = 0.5f), fontSize = 12.dp.value.sp)
            }
            Icon(Icons.Default.ChevronRight, null, tint = Color.White.copy(alpha = 0.4f))
        }
    }
}

private fun Modifier.clickable(onClick: () -> Unit) =
    this.then(Modifier.wrapContentSize().let {
        androidx.compose.foundation.clickable(onClick = onClick).let { m ->
            Modifier.then(m)
        }
    })
