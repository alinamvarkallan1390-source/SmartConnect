package com.alinam.smartconnect.mobile.data.model

sealed class FileTransferState {
    object Idle : FileTransferState()
    data class Transferring(
        val fileName: String,
        val progress: Float,
        val speedBps: Long,
        val remainingSeconds: Long,
        val transferId: String,
        val isPaused: Boolean = false
    ) : FileTransferState()
    data class Completed(val fileName: String, val transferId: String) : FileTransferState()
    data class Failed(val reason: String) : FileTransferState()
}
