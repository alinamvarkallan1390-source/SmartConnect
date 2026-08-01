package com.alinam.smartconnect.mobile.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.alinam.smartconnect.mobile.data.db.entity.ConnectionLogEntity
import com.alinam.smartconnect.mobile.ui.component.GlassCard
import com.alinam.smartconnect.mobile.ui.theme.*
import com.alinam.smartconnect.mobile.ui.viewmodel.SettingsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionLogsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val logs by viewModel.connectionLogs.collectAsStateWithLifecycle(initialValue = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("تاریخچه اتصال", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, null)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.clearLogs() }) {
                        Icon(Icons.Default.DeleteSweep, null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Dark80, titleContentColor = Color.White)
            )
        },
        containerColor = Dark90
    ) { padding ->
        if (logs.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("هیچ لاگی وجود ندارد", color = Color.White.copy(alpha = 0.4f))
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(logs) { log -> LogItem(log) }
            }
        }
    }
}

@Composable
fun LogItem(log: ConnectionLogEntity) {
    val isConnected = log.eventType == "CONNECTED"
    GlassCard(modifier = Modifier.fillMaxWidth(), padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(
                if (isConnected) Icons.Default.BluetoothConnected else Icons.Default.BluetoothDisabled,
                null,
                tint = if (isConnected) SuccessGreen else ErrorRed,
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(log.deviceName.ifBlank { log.deviceAddress }, color = Color.White, fontWeight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        SimpleDateFormat("MM/dd HH:mm:ss", Locale.getDefault()).format(Date(log.timestamp)),
                        color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp
                    )
                    if (!isConnected && log.durationSeconds > 0) {
                        Text("${log.durationSeconds}s", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                    }
                }
            }
            Text(if (isConnected) "متصل" else "قطع", color = if (isConnected) SuccessGreen else ErrorRed, fontSize = 12.sp)
        }
    }
}
