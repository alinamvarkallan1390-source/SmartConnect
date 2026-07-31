package com.alinam.smartconnect.mobile.data.model

import android.bluetooth.BluetoothDevice

data class WatchDevice(
    val name: String,
    val address: String,
    val rssi: Int = 0,
    val lastSeen: Long = System.currentTimeMillis(),
    val device: BluetoothDevice? = null
)
