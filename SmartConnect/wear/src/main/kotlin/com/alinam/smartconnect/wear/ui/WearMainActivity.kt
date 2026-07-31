package com.alinam.smartconnect.wear.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alinam.smartconnect.wear.service.WearBluetoothService
import com.alinam.smartconnect.wear.ui.viewmodel.WearViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class WearMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WearBluetoothService.start(this)
        setContent {
            WearTheme {
                WearMainScreen()
            }
        }
    }
}

@Composable
fun WearTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF6C63FF),
            background = Color(0xFF000000),
            surface = Color(0xFF12121A),
            onSurface = Color.White
        ),
        content = content
    )
}

@Composable
fun WearMainScreen(viewModel: WearViewModel = hiltViewModel()) {
    val isConnected by viewModel.isConnected.collectAsStateWithLifecycle()
    val phoneInfo by viewModel.phoneInfo.collectAsStateWithLifecycle()
    var isFindingPhone by remember { mutableStateOf(false) }
    val dotAlpha by animateFloatAsState(
        if (isConnected) 1f else 0.3f,
        animationSpec = tween(500),
        label = "dot"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    listOf(Color(0xFF1A0E3F), Color(0xFF000000))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // CONNECTION STATUS
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier.size(8.dp).clip(CircleShape)
                        .background(Color(0xFF00E676).copy(alpha = dotAlpha))
                )
                Text(
                    if (isConnected) "متصل" else "قطع",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (isConnected && phoneInfo != null) {
                val info = phoneInfo!!
                // Phone battery
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.PhoneAndroid, null,
                                tint = Color(0xFF6C63FF), modifier = Modifier.size(14.dp))
                            Text("گوشی", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                        }
                        WearInfoRow("باتری:", "${info.batteryPercent}%" + if (info.isCharging) " ⚡" else "")
                        WearInfoRow("WiFi:", if (info.wifiEnabled) "✓" else "✗")
                        WearInfoRow("اینترنت:", if (info.internetConnected) "✓" else "✗")
                        WearInfoRow("CPU:", "${info.cpuUsage.toInt()}%")
                        WearInfoRow("دما:", "${info.temperature}°")
                    }
                }

                // Time + Date
                val now = remember { java.util.Calendar.getInstance() }
                val timeStr = "%02d:%02d".format(now.get(java.util.Calendar.HOUR_OF_DAY),
                    now.get(java.util.Calendar.MINUTE))
                Text(timeStr, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            } else if (!isConnected) {
                Text("در حال جستجو...", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
            }

            // FIND MY PHONE BUTTON
            if (isConnected) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(
                            if (isFindingPhone) Color(0xFFFF1744).copy(alpha = 0.3f)
                            else Color(0xFF6C63FF).copy(alpha = 0.3f)
                        )
                        .clickable {
                            if (isFindingPhone) {
                                viewModel.stopFindPhone()
                                isFindingPhone = false
                            } else {
                                viewModel.findPhone()
                                isFindingPhone = true
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isFindingPhone) Icons.Default.Stop else Icons.Default.PhoneInTalk,
                        null,
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Text(
                    if (isFindingPhone) "متوقف کردن" else "Find My Phone",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
fun WearInfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
        Text(value, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}
