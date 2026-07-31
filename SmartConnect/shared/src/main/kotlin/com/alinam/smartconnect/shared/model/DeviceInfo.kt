package com.alinam.smartconnect.shared.model

data class DeviceInfo(
    val deviceId: String = "",
    val deviceName: String = "",
    val model: String = "",
    val androidVersion: String = "",
    val firmwareVersion: String = "",
    val batteryPercent: Int = 0,
    val isCharging: Boolean = false,
    val temperature: Float = 0f,
    val ramTotal: Long = 0L,
    val ramAvailable: Long = 0L,
    val cpuUsage: Float = 0f,
    val storageTotal: Long = 0L,
    val storageAvailable: Long = 0L,
    val signalStrength: Int = 0,
    val lastSyncTimestamp: Long = 0L,
    val wifiEnabled: Boolean = false,
    val bluetoothEnabled: Boolean = false,
    val internetConnected: Boolean = false,
    val gpsEnabled: Boolean = false,
    val screenOn: Boolean = false,
    val topApp: String = "",
    val currentTime: Long = 0L
)
