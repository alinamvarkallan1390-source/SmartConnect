package com.alinam.smartconnect.shared.protocol

data class HandshakePayload(
    val deviceId: String,
    val deviceName: String,
    val publicKey: String,
    val appVersion: String = "1.0.0"
)

data class RemoteControlPayload(
    val action: String,
    val value: String = ""
)

data class NotificationPayload(
    val id: Int,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val ticker: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

data class CallPayload(
    val number: String,
    val name: String,
    val state: String // INCOMING, ONGOING, ENDED
)

data class SmsPayload(
    val sender: String,
    val body: String,
    val timestamp: Long
)

data class MediaControlPayload(
    val action: String // PLAY, PAUSE, NEXT, PREV, VOLUME_UP, VOLUME_DOWN
)

data class MediaStatusPayload(
    val title: String,
    val artist: String,
    val album: String,
    val isPlaying: Boolean,
    val duration: Long,
    val position: Long
)

data class FileTransferStartPayload(
    val transferId: String,
    val fileName: String,
    val fileSize: Long,
    val mimeType: String,
    val checksum: String,
    val chunkSize: Int,
    val totalChunks: Int
)

data class FileTransferChunkPayload(
    val transferId: String,
    val chunkIndex: Int,
    val totalChunks: Int,
    val data: String, // Base64-encoded chunk
    val checksum: String
)

data class FileTransferAckPayload(
    val transferId: String,
    val chunkIndex: Int,
    val success: Boolean
)

data class AlertPayload(
    val level: String, // INFO, WARNING, ERROR
    val title: String,
    val message: String
)
