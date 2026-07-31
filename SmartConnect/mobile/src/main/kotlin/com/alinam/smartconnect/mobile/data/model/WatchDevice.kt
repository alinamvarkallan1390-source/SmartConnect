package com.alinam.smartconnect.mobile.data.model

data class WatchDevice(
    val name: String,
    val address: String,
    val rssi: Int,
    val lastSeen: Long = System.currentTimeMillis()
)
