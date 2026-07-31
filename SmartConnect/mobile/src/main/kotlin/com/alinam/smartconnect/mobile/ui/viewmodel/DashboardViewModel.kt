package com.alinam.smartconnect.mobile.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alinam.smartconnect.mobile.data.model.ConnectionState
import com.alinam.smartconnect.mobile.data.repository.ConnectionRepository
import com.alinam.smartconnect.mobile.data.repository.DeviceInfoRepository
import com.alinam.smartconnect.mobile.bluetooth.BluetoothManager
import com.alinam.smartconnect.mobile.sync.SyncManager
import com.alinam.smartconnect.shared.protocol.Message
import com.alinam.smartconnect.shared.protocol.MessageType
import com.alinam.smartconnect.shared.protocol.RemoteControlPayload
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val connectionRepository: ConnectionRepository,
    private val deviceInfoRepository: DeviceInfoRepository,
    private val bluetoothManager: BluetoothManager
) : ViewModel() {

    val connectionState = connectionRepository.connectionState
    val connectedDevice = connectionRepository.connectedDevice
    val watchInfo = deviceInfoRepository.watchInfo
    val rssi = connectionRepository.rssi
    val connectionQuality = connectionRepository.connectionQuality
    val latency = connectionRepository.latency
    val scanResults = connectionRepository.scanResults

    fun startScan() = connectionRepository.startScan()
    fun stopScan() = connectionRepository.stopScan()
    fun disconnect() = connectionRepository.disconnect()

    fun connectToDevice(device: android.bluetooth.BluetoothDevice) {
        connectionRepository.connectToDevice(device)
    }

    fun requestWatchInfo() {
        bluetoothManager.sendMessage(Message(MessageType.DEVICE_INFO_REQUEST))
    }

    fun sendRemoteControl(action: String, value: String = "") {
        bluetoothManager.sendMessage(
            Message(MessageType.REMOTE_CONTROL, Gson().toJson(RemoteControlPayload(action, value)))
        )
    }

    fun findWatch() {
        bluetoothManager.sendMessage(Message(MessageType.FIND_DEVICE))
    }

    fun stopFindWatch() {
        bluetoothManager.sendMessage(Message(MessageType.FIND_DEVICE_STOP))
    }

    fun syncClipboard(text: String) {
        bluetoothManager.sendMessage(Message(MessageType.CLIPBOARD_SYNC, text))
    }

    fun mediaControl(action: String) {
        bluetoothManager.sendMessage(
            Message(MessageType.MEDIA_CONTROL, Gson().toJson(
                com.alinam.smartconnect.shared.protocol.MediaControlPayload(action)
            ))
        )
    }

    fun estimateDistance(): Double = connectionRepository.estimateDistance()
}
