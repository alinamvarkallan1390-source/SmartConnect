package com.alinam.smartconnect.mobile.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.alinam.smartconnect.mobile.data.model.ConnectionState
import com.alinam.smartconnect.mobile.ui.component.GlassCard
import com.alinam.smartconnect.mobile.ui.theme.*
import com.alinam.smartconnect.mobile.ui.viewmodel.DashboardViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperScreen(
    navController: NavController,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val rssi by viewModel.rssi.collectAsStateWithLifecycle()
    val quality by viewModel.connectionQuality.collectAsStateWithLifecycle()
    val latency by viewModel.latency.collectAsStateWithLifecycle()
    val scanResults by viewModel.scanResults.collectAsStateWithLifecycle()
    var logText by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Developer", fontWeight = FontWeight.Bold) },
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // BT STATUS
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Bluetooth Debug", color = Purple, fontWeight = FontWeight.Bold)
                    Text("State: ${connectionState.name}", color = Color.White, fontFamily = FontFamily.Monospace)
                    Text("RSSI: $rssi dBm", color = Color.White, fontFamily = FontFamily.Monospace)
                    Text("Quality: ${quality.name}", color = Color.White, fontFamily = FontFamily.Monospace)
                    Text("Latency: ${latency}ms", color = Color.White, fontFamily = FontFamily.Monospace)
                }
            }

            // SCAN
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Scan Results (${scanResults.size})", color = Cyan, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { viewModel.startScan() }) { Text("Start Scan") }
                        OutlinedButton(onClick = { viewModel.stopScan() }) { Text("Stop Scan") }
                    }
                    scanResults.forEach { device ->
                        Text(
                            "${device.name ?: "Unknown"} | ${device.address}",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // SEND TEST MESSAGE
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Remote Control Test", color = WarningOrange, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            viewModel.sendRemoteControl("SET_BRIGHTNESS", "128")
                            logText += "Sent SET_BRIGHTNESS 128
"
                        }) { Text("Brightness 50%") }
                        OutlinedButton(onClick = {
                            viewModel.findWatch()
                            logText += "Sent FIND_WATCH
"
                        }) { Text("Find Watch") }
                    }
                }
            }

            // LOG
            if (logText.isNotBlank()) {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Log", color = SuccessGreen, fontWeight = FontWeight.Bold)
                            IconButton(onClick = { logText = "" }) {
                                Icon(Icons.Default.ClearAll, null, tint = Color.White.copy(alpha = 0.5f))
                            }
                        }
                        Text(logText, color = SuccessGreen, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
