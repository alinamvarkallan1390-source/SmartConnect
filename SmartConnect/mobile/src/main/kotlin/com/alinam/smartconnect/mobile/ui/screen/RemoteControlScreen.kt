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
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.alinam.smartconnect.mobile.ui.component.GlassCard
import com.alinam.smartconnect.mobile.ui.theme.*
import com.alinam.smartconnect.mobile.ui.viewmodel.DashboardViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteControlScreen(
    navController: NavController,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    var brightness by remember { mutableFloatStateOf(0.5f) }
    var volume by remember { mutableFloatStateOf(0.5f) }
    var screenTimeout by remember { mutableStateOf("30s") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("کنترل ساعت", fontWeight = FontWeight.Bold) },
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
            // BRIGHTNESS
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Brightness6, null, tint = WarningOrange)
                        Spacer(Modifier.width(8.dp))
                        Text("روشنایی صفحه", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                    Slider(
                        value = brightness,
                        onValueChange = { brightness = it },
                        onValueChangeFinished = {
                            viewModel.sendRemoteControl("SET_BRIGHTNESS", (brightness * 255).toInt().toString())
                        },
                        colors = SliderDefaults.colors(thumbColor = WarningOrange, activeTrackColor = WarningOrange)
                    )
                }
            }

            // VOLUME
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.VolumeUp, null, tint = Cyan)
                        Spacer(Modifier.width(8.dp))
                        Text("صدا", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                    Slider(
                        value = volume,
                        onValueChange = { volume = it },
                        onValueChangeFinished = {
                            viewModel.sendRemoteControl("SET_VOLUME", (volume * 15).toInt().toString())
                        },
                        colors = SliderDefaults.colors(thumbColor = Cyan, activeTrackColor = Cyan)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { viewModel.mediaControl("VOLUME_DOWN") }) {
                            Icon(Icons.Default.VolumeDown, null)
                        }
                        OutlinedButton(onClick = { viewModel.mediaControl("VOLUME_UP") }) {
                            Icon(Icons.Default.VolumeUp, null)
                        }
                    }
                }
            }

            // MEDIA CONTROLS
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.MusicNote, null, tint = Purple)
                        Spacer(Modifier.width(8.dp))
                        Text("کنترل موزیک", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        IconButton(onClick = { viewModel.mediaControl("PREV") }) {
                            Icon(Icons.Default.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(36.dp))
                        }
                        IconButton(onClick = { viewModel.mediaControl("PLAY") }) {
                            Icon(Icons.Default.PlayCircle, null, tint = Purple, modifier = Modifier.size(48.dp))
                        }
                        IconButton(onClick = { viewModel.mediaControl("NEXT") }) {
                            Icon(Icons.Default.SkipNext, null, tint = Color.White, modifier = Modifier.size(36.dp))
                        }
                    }
                }
            }

            // WATCH POWER ACTIONS
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("اقدامات دستگاه", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { viewModel.sendRemoteControl("OPEN_SETTINGS") },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Settings, null, tint = Color.White)
                            Spacer(Modifier.width(4.dp))
                            Text("تنظیمات", color = Color.White)
                        }
                        OutlinedButton(
                            onClick = { viewModel.sendRemoteControl("VIBRATE") },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Vibration, null, tint = PurpleLight)
                            Spacer(Modifier.width(4.dp))
                            Text("ویبره", color = Color.White)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // REBOOT and SHUTDOWN require privileged system permissions
                        // and are intentionally NOT exposed in this build.
                        OutlinedButton(
                            onClick = { /* intentionally disabled */ },
                            enabled = false,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.RestartAlt, null, tint = WarningOrange.copy(alpha = 0.4f))
                            Spacer(Modifier.width(4.dp))
                            Text("ری‌استارت", color = Color.White.copy(alpha = 0.4f))
                        }
                        OutlinedButton(
                            onClick = { /* intentionally disabled */ },
                            enabled = false,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.PowerSettingsNew, null, tint = ErrorRed.copy(alpha = 0.4f))
                            Spacer(Modifier.width(4.dp))
                            Text("خاموش", color = Color.White.copy(alpha = 0.4f))
                        }
                    }
                }
            }
        }
    }
}
