package com.alinam.smartconnect.mobile.ui.screen

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.alinam.smartconnect.mobile.data.model.ConnectionQuality
import com.alinam.smartconnect.mobile.data.model.ConnectionState
import com.alinam.smartconnect.mobile.data.model.toColor
import com.alinam.smartconnect.mobile.data.model.toLabel
import com.alinam.smartconnect.mobile.ui.component.GlassCard
import com.alinam.smartconnect.mobile.ui.navigation.Screen
import com.alinam.smartconnect.mobile.ui.theme.*
import com.alinam.smartconnect.mobile.ui.viewmodel.DashboardViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    navController: NavController,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val connectedDevice by viewModel.connectedDevice.collectAsStateWithLifecycle()
    val watchInfo by viewModel.watchInfo.collectAsStateWithLifecycle()
    val rssi by viewModel.rssi.collectAsStateWithLifecycle()
    val quality by viewModel.connectionQuality.collectAsStateWithLifecycle()
    val latency by viewModel.latency.collectAsStateWithLifecycle()
    var isFinding by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse),
        label = "pulse"
    )
    val isConnected = connectionState == ConnectionState.CONNECTED
    val dotColor by animateColorAsState(
        if (isConnected) SuccessGreen else ErrorRed, label = "dot"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SmartConnect", fontWeight = FontWeight.Bold, fontSize = 22.sp) },
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.Settings.route) }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Dark80,
                    titleContentColor = Color.White
                )
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Dark80) {
                NavigationBarItem(
                    selected = true,
                    onClick = {},
                    icon = { Icon(Icons.Default.Watch, null) },
                    label = { Text("داشبورد") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { navController.navigate(Screen.RemoteControl.route) },
                    icon = { Icon(Icons.Default.SettingsRemote, null) },
                    label = { Text("کنترل") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { navController.navigate(Screen.FileTransfer.route) },
                    icon = { Icon(Icons.Default.FolderOpen, null) },
                    label = { Text("فایل") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { navController.navigate(Screen.ConnectionLogs.route) },
                    icon = { Icon(Icons.Default.History, null) },
                    label = { Text("لاگ") }
                )
            }
        },
        containerColor = Dark90
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // CONNECTION STATUS CARD
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(72.dp)
                            .scale(if (isConnected) pulseScale else 1f)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(Purple.copy(alpha = 0.6f), Color.Transparent)
                                )
                            )
                    ) {
                        Icon(
                            Icons.Default.Watch,
                            contentDescription = null,
                            tint = if (isConnected) SuccessGreen else Color.Gray,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(dotColor)
                            )
                            Text(
                                text = when (connectionState) {
                                    ConnectionState.CONNECTED -> connectedDevice?.name ?: "متصل"
                                    ConnectionState.SCANNING -> "در حال جستجو..."
                                    ConnectionState.CONNECTING -> "در حال اتصال..."
                                    else -> "قطع اتصال"
                                },
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 18.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        if (isConnected) {
                            Text(
                                "کیفیت: ${quality.toLabel()} | تاخیر: ${latency}ms",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 13.sp
                            )
                            // RSSI signal bar
                            RssiBar(rssi = rssi)
                        } else {
                            Text(
                                "دستگاهی متصل نیست",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 13.sp
                            )
                        }
                    }
                    if (!isConnected) {
                        IconButton(onClick = { viewModel.startScan() }) {
                            Icon(Icons.Default.Bluetooth, null, tint = Purple)
                        }
                    } else {
                        IconButton(onClick = { viewModel.disconnect() }) {
                            Icon(Icons.Default.BluetoothDisabled, null, tint = ErrorRed)
                        }
                    }
                }
            }

            // BLUETOOTH NOT ENABLED
            if (!isConnected && connectionState == ConnectionState.DISCONNECTED) {
                val btAdapter = remember { BluetoothAdapter.getDefaultAdapter() }
                if (btAdapter?.isEnabled == false) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = WarningOrange.copy(alpha = 0.2f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.BluetoothDisabled, null, tint = WarningOrange)
                            Column(modifier = Modifier.weight(1f)) {
                                Text("بلوتوث غیرفعال است", color = WarningOrange, fontWeight = FontWeight.Bold)
                                Text("برای اتصال به ساعت، بلوتوث را روشن کنید",
                                    color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                            }
                            TextButton(onClick = {
                                context.startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                            }) { Text("روشن کن", color = WarningOrange) }
                        }
                    }
                }
            }

            // WATCH DASHBOARD GRID
            if (isConnected && watchInfo != null) {
                val info = watchInfo!!
                Text("داشبورد ساعت", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)

                // Battery + Charging
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    InfoCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.BatteryFull,
                        label = "شارژ ساعت",
                        value = "${info.batteryPercent}%",
                        iconColor = if (info.batteryPercent > 30) SuccessGreen else ErrorRed,
                        sub = if (info.isCharging) "در حال شارژ" else ""
                    )
                    InfoCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Thermostat,
                        label = "دمای ساعت",
                        value = "${info.temperature}°C",
                        iconColor = if (info.temperature < 40) SuccessGreen else WarningOrange
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    InfoCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Memory,
                        label = "RAM",
                        value = formatBytes(info.ramAvailable),
                        sub = "از ${formatBytes(info.ramTotal)}",
                        iconColor = Cyan
                    )
                    InfoCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Speed,
                        label = "CPU",
                        value = "${info.cpuUsage.toInt()}%",
                        iconColor = if (info.cpuUsage < 70) SuccessGreen else WarningOrange
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    InfoCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Storage,
                        label = "فضا",
                        value = formatBytes(info.storageAvailable),
                        sub = "آزاد",
                        iconColor = PurpleLight
                    )
                    InfoCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.SignalCellularAlt,
                        label = "سیگنال BT",
                        value = "${rssi} dBm",
                        iconColor = Color(quality.toColor())
                    )
                }

                // Device info
                GlassCard(modifier = Modifier.fillMaxWidth(), padding = 12.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("اطلاعات دستگاه", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                        InfoRow("مدل:", info.model.ifBlank { info.deviceName })
                        InfoRow("Android:", info.androidVersion)
                        InfoRow("Firmware:", info.firmwareVersion)
                        InfoRow("آخرین همگام:", formatTimestamp(info.lastSyncTimestamp))
                    }
                }
            }

            // QUICK ACTIONS
            if (isConnected) {
                Text("اقدامات سریع", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ActionButton(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.LocationSearching,
                        label = if (isFinding) "متوقف کن" else "پیدا کردن ساعت",
                        color = if (isFinding) ErrorRed else Purple
                    ) {
                        if (isFinding) {
                            viewModel.stopFindWatch()
                            isFinding = false
                        } else {
                            viewModel.findWatch()
                            isFinding = true
                        }
                    }
                    ActionButton(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.SettingsRemote,
                        label = "کنترل ساعت",
                        color = Cyan
                    ) { navController.navigate(Screen.RemoteControl.route) }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ActionButton(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.FolderOpen,
                        label = "انتقال فایل",
                        color = SuccessGreen
                    ) { navController.navigate(Screen.FileTransfer.route) }
                    ActionButton(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.ContentCopy,
                        label = "همگام کلیپ‌بورد",
                        color = WarningOrange
                    ) {
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                            as android.content.ClipboardManager
                        val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                        viewModel.syncClipboard(text)
                    }
                }
            }

            // DISTANCE ESTIMATE
            if (isConnected) {
                val distance = viewModel.estimateDistance()
                GlassCard(modifier = Modifier.fillMaxWidth(), padding = 12.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.SocialDistance, null, tint = Cyan, modifier = Modifier.size(28.dp))
                        Column {
                            Text("فاصله تقریبی", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                            Text(
                                if (distance < 0) "نامشخص" else "~${"%.1f".format(distance)} متر",
                                color = Color.White, fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RssiBar(rssi: Int) {
    val bars = when {
        rssi == 0 || rssi == Int.MIN_VALUE -> 0
        rssi >= -60 -> 5
        rssi >= -70 -> 4
        rssi >= -75 -> 3
        rssi >= -80 -> 2
        else -> 1
    }
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        repeat(5) { i ->
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height((6 + i * 4).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (i < bars) SuccessGreen else Color.Gray.copy(alpha = 0.3f)
                    )
            )
        }
        Text(
            if (rssi == 0 || rssi == Int.MIN_VALUE) " —"
            else " ${rssi}dBm",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 11.sp
        )
    }
}

@Composable
fun InfoCard(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    sub: String = "",
    iconColor: Color = Purple
) {
    GlassCard(modifier = modifier, padding = 14.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(icon, null, tint = iconColor, modifier = Modifier.size(18.dp))
                Text(label, color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
            }
            Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            if (sub.isNotBlank()) Text(sub, color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
        Text(value, color = Color.White, fontSize = 13.sp)
    }
}

@Composable
fun ActionButton(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    GlassCard(
        modifier = modifier.clickable(onClick = onClick),
        padding = 14.dp
    ) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.2f))
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            }
            Text(label, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 13.sp)
        }
    }
}

fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1_073_741_824 -> "${"%.1f".format(bytes / 1_073_741_824.0)} GB"
        bytes >= 1_048_576 -> "${"%.0f".format(bytes / 1_048_576.0)} MB"
        bytes >= 1024 -> "${"%.0f".format(bytes / 1024.0)} KB"
        else -> "$bytes B"
    }
}

fun formatTimestamp(ts: Long): String {
    if (ts == 0L) return "-"
    return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(ts))
}
