package com.alinam.smartconnect.mobile.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "file_transfers")
data class FileTransferEntity(
    @PrimaryKey val transferId: String,
    val fileName: String,
    val filePath: String,
    val fileSize: Long,
    val mimeType: String,
    val direction: String, // SEND, RECEIVE
    val status: String,    // PENDING, IN_PROGRESS, COMPLETED, FAILED, CANCELLED
    val progress: Float = 0f,
    val timestamp: Long = System.currentTimeMillis(),
    val completedAt: Long = 0L,
    val checksum: String = ""
)
