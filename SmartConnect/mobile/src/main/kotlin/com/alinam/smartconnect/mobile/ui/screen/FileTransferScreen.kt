package com.alinam.smartconnect.mobile.ui.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.alinam.smartconnect.mobile.data.db.entity.FileTransferEntity
import com.alinam.smartconnect.mobile.data.model.FileTransferState
import com.alinam.smartconnect.mobile.ui.component.GlassCard
import com.alinam.smartconnect.mobile.ui.theme.*
import com.alinam.smartconnect.mobile.ui.viewmodel.FileTransferViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileTransferScreen(
    navController: NavController,
    viewModel: FileTransferViewModel = hiltViewModel()
) {
    val transferState by viewModel.transferState.collectAsStateWithLifecycle()
    val allTransfers by viewModel.allTransfers.collectAsStateWithLifecycle(initial = emptyList())

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.sendFileFromUri(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("انتقال فایل", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Dark80, titleContentColor = Color.White)
            )
        },
        floatingActionButton = {
            if (transferState is FileTransferState.Idle) {
                FloatingActionButton(
                    onClick = { filePicker.launch("*/*") },
                    containerColor = Purple
                ) {
                    Icon(Icons.Default.Upload, null, tint = Color.White)
                }
            }
        },
        containerColor = Dark90
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // CURRENT TRANSFER
            when (val state = transferState) {
                is FileTransferState.Transferring -> {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.UploadFile, null, tint = Cyan)
                                Spacer(Modifier.width(8.dp))
                                Text(state.fileName, color = Color.White, fontWeight = FontWeight.SemiBold,
                                    maxLines = 1)
                            }
                            LinearProgressIndicator(
                                progress = { state.progress },
                                modifier = Modifier.fillMaxWidth(),
                                color = Cyan,
                                trackColor = Color.White.copy(alpha = 0.2f),
                                strokeCap = StrokeCap.Round
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("${(state.progress * 100).toInt()}%", color = Cyan, fontWeight = FontWeight.Bold)
                                Text("${formatBytes(state.speedBps)}/s", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                                Text("⛳ ${state.remainingSeconds}s", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (state.isPaused) {
                                    OutlinedButton(onClick = { viewModel.resumeTransfer() }, modifier = Modifier.weight(1f)) {
                                        Icon(Icons.Default.PlayArrow, null); Text("ادامه")
                                    }
                                } else {
                                    OutlinedButton(onClick = { viewModel.pauseTransfer() }, modifier = Modifier.weight(1f)) {
                                        Icon(Icons.Default.Pause, null); Text("توقف")
                                    }
                                }
                                OutlinedButton(
                                    onClick = { viewModel.cancelTransfer() },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed)
                                ) {
                                    Icon(Icons.Default.Cancel, null); Text("لغو")
                                }
                            }
                        }
                    }
                }
                is FileTransferState.Completed -> {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SuccessGreen.copy(alpha = 0.2f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, null, tint = SuccessGreen)
                            Spacer(Modifier.width(12.dp))
                            Text("انتقال ${state.fileName} کامل شد", color = Color.White)
                        }
                    }
                }
                is FileTransferState.Failed -> {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = ErrorRed.copy(alpha = 0.2f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Error, null, tint = ErrorRed)
                            Spacer(Modifier.width(12.dp))
                            Text("خطا: ${state.reason}", color = Color.White)
                        }
                    }
                }
                else -> {}
            }

            // HISTORY
            Text("تاریخچه انتقال", color = Color.White, fontWeight = FontWeight.Bold)
            if (allTransfers.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("تاریخچه‌ای وجود ندارد", color = Color.White.copy(alpha = 0.4f))
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(allTransfers) { transfer -> TransferHistoryItem(transfer) }
                }
            }
        }
    }
}

@Composable
fun TransferHistoryItem(transfer: FileTransferEntity) {
    val icon = if (transfer.direction == "SEND") Icons.Default.Upload else Icons.Default.Download
    val statusColor = when (transfer.status) {
        "COMPLETED" -> SuccessGreen
        "FAILED", "CANCELLED" -> ErrorRed
        "IN_PROGRESS" -> Cyan
        else -> Color.Gray
    }
    GlassCard(modifier = Modifier.fillMaxWidth(), padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, tint = statusColor, modifier = Modifier.size(24.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(transfer.fileName, color = Color.White, fontWeight = FontWeight.Medium, maxLines = 1)
                Text(formatBytes(transfer.fileSize) + " | " + transfer.status,
                    color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
            }
        }
    }
}
