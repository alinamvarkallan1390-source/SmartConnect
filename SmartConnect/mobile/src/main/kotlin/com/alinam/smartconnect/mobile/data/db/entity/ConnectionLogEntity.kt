package com.alinam.smartconnect.mobile.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "connection_logs")
data class ConnectionLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deviceAddress: String,
    val deviceName: String,
    val eventType: String, // CONNECTED, DISCONNECTED
    val timestamp: Long = System.currentTimeMillis(),
    val rssi: Int = 0,
    val durationSeconds: Long = 0
)
