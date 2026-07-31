package com.alinam.smartconnect.mobile.data.repository

import com.alinam.smartconnect.mobile.data.db.dao.ConnectionLogDao
import com.alinam.smartconnect.mobile.data.db.dao.DeviceSettingsDao
import com.alinam.smartconnect.mobile.data.db.entity.ConnectionLogEntity
import com.alinam.smartconnect.mobile.data.db.entity.DeviceSettingsEntity
import com.alinam.smartconnect.mobile.bluetooth.BluetoothManager
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConnectionRepository @Inject constructor(
    private val connectionLogDao: ConnectionLogDao,
    private val settingsDao: DeviceSettingsDao,
    private val bluetoothManager: BluetoothManager
) {
    companion object {
        private const val KEY_LAST_ADDRESS = "last_device_address"
        private const val KEY_LAST_NAME = "last_device_name"
    }

    val connectionState = bluetoothManager.connectionState
    val connectedDevice = bluetoothManager.connectedDevice
    val rssi = bluetoothManager.rssi
    val connectionQuality = bluetoothManager.connectionQuality
    val latency = bluetoothManager.latency
    val scanResults = bluetoothManager.scanResults

    fun getConnectionLogs(): Flow<List<ConnectionLogEntity>> =
        connectionLogDao.getAllLogs()

    suspend fun logConnection(address: String, name: String, rssi: Int) {
        connectionLogDao.insert(
            ConnectionLogEntity(
                deviceAddress = address,
                deviceName = name,
                eventType = "CONNECTED",
                rssi = rssi
            )
        )
    }

    suspend fun logDisconnection(address: String, name: String, durationSeconds: Long) {
        connectionLogDao.insert(
            ConnectionLogEntity(
                deviceAddress = address,
                deviceName = name,
                eventType = "DISCONNECTED",
                durationSeconds = durationSeconds
            )
        )
    }

    suspend fun saveLastConnection(address: String) {
        settingsDao.upsert(DeviceSettingsEntity(KEY_LAST_ADDRESS, address))
    }

    suspend fun getLastDeviceAddress(): String? = settingsDao.getValue(KEY_LAST_ADDRESS)

    fun startScan() = bluetoothManager.startSmartScan()
    fun stopScan() = bluetoothManager.stopScan()
    fun disconnect() = bluetoothManager.disconnect()
    fun connectToDevice(device: android.bluetooth.BluetoothDevice) =
        bluetoothManager.connectToDevice(device)

    fun estimateDistance(): Double = bluetoothManager.estimateDistance()

    suspend fun clearOldLogs() {
        val sevenDaysAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000)
        connectionLogDao.deleteOldLogs(sevenDaysAgo)
    }
}
