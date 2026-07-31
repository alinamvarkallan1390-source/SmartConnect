package com.alinam.smartconnect.mobile.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.ActivityCompat
import com.alinam.smartconnect.mobile.data.model.ConnectionQuality
import com.alinam.smartconnect.mobile.data.model.ConnectionState
import com.alinam.smartconnect.mobile.data.model.WatchDevice
import com.alinam.smartconnect.shared.protocol.Message
import com.alinam.smartconnect.shared.protocol.MessageType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Core Bluetooth manager handling both BLE and Classic connections.
 * Implements smart scanning with battery optimization.
 */
@Singleton
class BluetoothManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        // SPP UUID for Classic Bluetooth
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        // Custom service UUID for SmartConnect protocol
        val SC_SERVICE_UUID: UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
        val SC_TX_UUID: UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")
        val SC_RX_UUID: UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")

        private const val SCAN_PERIOD_CONNECTED = 30_000L   // 30s when connected
        private const val SCAN_PERIOD_DISCONNECTED = 5_000L // 5s when disconnected
        private const val RECONNECT_DELAY = 3_000L
        private const val MAX_RECONNECT_ATTEMPTS = 10
        private const val RSSI_WARNING_THRESHOLD = -80
        private const val RSSI_CRITICAL_THRESHOLD = -90
        private const val HEARTBEAT_INTERVAL = 10_000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    val adapter: BluetoothAdapter? get() = btManager.adapter
    private var leScanner: BluetoothLeScanner? = null

    // Classic BT
    private var classicSocket: BluetoothSocket? = null
    private var classicInputStream: InputStream? = null
    private var classicOutputStream: OutputStream? = null
    private var serverSocket: BluetoothServerSocket? = null

    // BLE
    private var bleGatt: BluetoothGatt? = null
    private var txCharacteristic: BluetoothGattCharacteristic? = null

    // State
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _connectedDevice = MutableStateFlow<WatchDevice?>(null)
    val connectedDevice: StateFlow<WatchDevice?> = _connectedDevice.asStateFlow()

    private val _rssi = MutableStateFlow(0)
    val rssi: StateFlow<Int> = _rssi.asStateFlow()

    private val _connectionQuality = MutableStateFlow(ConnectionQuality.UNKNOWN)
    val connectionQuality: StateFlow<ConnectionQuality> = _connectionQuality.asStateFlow()

    private val _latency = MutableStateFlow(0L)
    val latency: StateFlow<Long> = _latency.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<Message>(extraBufferCapacity = 64)
    val incomingMessages: SharedFlow<Message> = _incomingMessages.asSharedFlow()

    private val _scanResults = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val scanResults: StateFlow<List<BluetoothDevice>> = _scanResults.asStateFlow()

    private var reconnectAttempts = 0
    private var targetDeviceAddress: String? = null
    private var isScanning = false
    private var scanJob: Job? = null
    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null
    private var readJob: Job? = null
    private var useClassic = true // Prefer Classic for file transfer; BLE for control

    // ============================================================
    // BLUETOOTH STATE
    // ============================================================

    val isBluetoothEnabled: Boolean
        get() = adapter?.isEnabled == true

    // ============================================================
    // SCANNING
    // ============================================================

    fun startSmartScan() {
        if (!isBluetoothEnabled) {
            Timber.w("Bluetooth disabled, cannot scan")
            return
        }
        if (isScanning) return
        scanJob?.cancel()
        scanJob = scope.launch {
            while (true) {
                performScan()
                val interval = if (_connectionState.value == ConnectionState.CONNECTED)
                    SCAN_PERIOD_CONNECTED else SCAN_PERIOD_DISCONNECTED
                delay(interval)
            }
        }
    }

    private fun performScan() {
        if (!hasPermission(Manifest.permission.BLUETOOTH_SCAN)) return
        leScanner = adapter?.bluetoothLeScanner
        val settings = ScanSettings.Builder()
            .setScanMode(
                if (_connectionState.value == ConnectionState.CONNECTED)
                    ScanSettings.SCAN_MODE_LOW_POWER
                else
                    ScanSettings.SCAN_MODE_BALANCED
            )
            .build()
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN)
            == PackageManager.PERMISSION_GRANTED) {
            leScanner?.startScan(null, settings, leScanCallback)
            isScanning = true
            Timber.d("BLE scan started")
            mainHandler.postDelayed({
                stopScan()
            }, 8_000L)
        }
    }

    private val leScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            _rssi.value = result.rssi
            updateConnectionQuality(result.rssi)
            val current = _scanResults.value.toMutableList()
            if (current.none { it.address == device.address }) {
                current.add(device)
                _scanResults.value = current
            }
            // Auto-connect if this is our saved device
            if (targetDeviceAddress != null && device.address == targetDeviceAddress
                && _connectionState.value == ConnectionState.DISCONNECTED) {
                connectToDevice(device)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Timber.e("BLE scan failed: $errorCode")
            isScanning = false
        }
    }

    fun stopScan() {
        if (!hasPermission(Manifest.permission.BLUETOOTH_SCAN)) return
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN)
            == PackageManager.PERMISSION_GRANTED) {
            leScanner?.stopScan(leScanCallback)
        }
        isScanning = false
    }

    // ============================================================
    // CONNECTION - CLASSIC BT
    // ============================================================

    fun connectToDevice(device: BluetoothDevice) {
        if (_connectionState.value == ConnectionState.CONNECTING ||
            _connectionState.value == ConnectionState.CONNECTED) return
        targetDeviceAddress = device.address
        _connectionState.value = ConnectionState.CONNECTING
        scope.launch {
            connectClassic(device)
        }
    }

    private suspend fun connectClassic(device: BluetoothDevice) {
        try {
            if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) return
            classicSocket?.close()
            val socket = if (ActivityCompat.checkSelfPermission(
                    context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                device.createRfcommSocketToServiceRecord(SPP_UUID)
            } else return
            stopScan()
            socket.connect()
            classicSocket = socket
            classicInputStream = socket.inputStream
            classicOutputStream = socket.outputStream
            val name = if (ActivityCompat.checkSelfPermission(context,
                    Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED)
                device.name ?: "Unknown" else "Unknown"
            _connectedDevice.value = WatchDevice(
                address = device.address,
                name = name,
                device = device
            )
            _connectionState.value = ConnectionState.CONNECTED
            reconnectAttempts = 0
            startHeartbeat()
            startReading()
            Timber.i("Connected to $name via Classic BT")
        } catch (e: Exception) {
            Timber.e(e, "Classic BT connection failed")
            _connectionState.value = ConnectionState.DISCONNECTED
            scheduleReconnect(device)
        }
    }

    // ============================================================
    // BLE GATT CONNECTION
    // ============================================================

    fun connectBle(device: BluetoothDevice) {
        if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) return
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
            == PackageManager.PERMISSION_GRANTED) {
            bleGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Timber.i("BLE GATT connected")
                    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                        == PackageManager.PERMISSION_GRANTED) {
                        gatt.discoverServices()
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Timber.w("BLE GATT disconnected")
                    _connectionState.value = ConnectionState.DISCONNECTED
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val service = gatt.getService(SC_SERVICE_UUID) ?: return
            txCharacteristic = service.getCharacteristic(SC_TX_UUID)
            val rxChar = service.getCharacteristic(SC_RX_UUID)
            if (rxChar != null) {
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                    == PackageManager.PERMISSION_GRANTED) {
                    gatt.setCharacteristicNotification(rxChar, true)
                    val descriptor = rxChar.getDescriptor(CCCD_UUID)
                    descriptor?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(descriptor)
                }
            }
            _connectionState.value = ConnectionState.CONNECTED
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (characteristic.uuid == SC_RX_UUID) {
                val data = characteristic.value ?: return
                processIncomingData(data)
            }
        }

        override fun onReadRemoteRssi(gatt: BluetoothGatt, rssi: Int, status: Int) {
            _rssi.value = rssi
            updateConnectionQuality(rssi)
        }
    }

    // ============================================================
    // DATA TRANSFER
    // ============================================================

    fun sendMessage(message: Message) {
        val data = (message.toJson() + "
").toByteArray(Charsets.UTF_8)
        scope.launch {
            try {
                classicOutputStream?.let {
                    it.write(data)
                    it.flush()
                } ?: sendViaBle(data)
            } catch (e: Exception) {
                Timber.e(e, "Send failed")
                handleDisconnection()
            }
        }
    }

    fun sendRawBytes(data: ByteArray) {
        scope.launch {
            try {
                classicOutputStream?.let {
                    it.write(data)
                    it.flush()
                } ?: sendViaBle(data)
            } catch (e: Exception) {
                Timber.e(e, "Raw send failed")
                handleDisconnection()
            }
        }
    }

    private fun sendViaBle(data: ByteArray) {
        if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) return
        val gatt = bleGatt ?: return
        val tx = txCharacteristic ?: return
        // BLE MTU is typically 512 bytes; chunk if necessary
        val mtu = 512
        data.toList().chunked(mtu).forEach { chunk ->
            tx.value = chunk.toByteArray()
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED) {
                gatt.writeCharacteristic(tx)
            }
        }
    }

    private fun startReading() {
        readJob?.cancel()
        readJob = scope.launch {
            val buffer = StringBuilder()
            val byteArray = ByteArray(4096)
            while (_connectionState.value == ConnectionState.CONNECTED) {
                try {
                    val stream = classicInputStream ?: break
                    val bytesRead = stream.read(byteArray)
                    if (bytesRead > 0) {
                        val received = String(byteArray, 0, bytesRead, Charsets.UTF_8)
                        buffer.append(received)
                        // Process complete messages delimited by newline
                        while (buffer.contains("
")) {
                            val newlineIdx = buffer.indexOf("
")
                            val jsonMsg = buffer.substring(0, newlineIdx)
                            buffer.delete(0, newlineIdx + 1)
                            if (jsonMsg.isNotBlank()) {
                                processIncomingData(jsonMsg.toByteArray())
                            }
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Read error")
                    break
                }
            }
            handleDisconnection()
        }
    }

    private fun processIncomingData(data: ByteArray) {
        try {
            val json = data.toString(Charsets.UTF_8).trim()
            val message = Message.fromJson(json)
            scope.launch { _incomingMessages.emit(message) }
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse message")
        }
    }

    // ============================================================
    // RECONNECTION
    // ============================================================

    private fun scheduleReconnect(device: BluetoothDevice) {
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            Timber.w("Max reconnect attempts reached")
            reconnectAttempts = 0
            startSmartScan()
            return
        }
        reconnectAttempts++
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(RECONNECT_DELAY * reconnectAttempts)
            Timber.d("Reconnect attempt $reconnectAttempts")
            connectClassic(device)
        }
    }

    private fun handleDisconnection() {
        classicSocket = null
        classicInputStream = null
        classicOutputStream = null
        _connectionState.value = ConnectionState.DISCONNECTED
        heartbeatJob?.cancel()
        val device = _connectedDevice.value?.device
        _connectedDevice.value = null
        device?.let { scheduleReconnect(it) }
    }

    // ============================================================
    // HEARTBEAT
    // ============================================================

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (_connectionState.value == ConnectionState.CONNECTED) {
                delay(HEARTBEAT_INTERVAL)
                val start = System.currentTimeMillis()
                sendMessage(Message(MessageType.HEARTBEAT))
                // Measure RSSI periodically
                bleGatt?.let {
                    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                        == PackageManager.PERMISSION_GRANTED) {
                        it.readRemoteRssi()
                    }
                }
                _latency.value = System.currentTimeMillis() - start
            }
        }
    }

    // ============================================================
    // UTILITIES
    // ============================================================

    private fun updateConnectionQuality(rssi: Int) {
        _connectionQuality.value = when {
            rssi >= -60 -> ConnectionQuality.EXCELLENT
            rssi >= -70 -> ConnectionQuality.GOOD
            rssi >= -80 -> ConnectionQuality.FAIR
            rssi >= -90 -> ConnectionQuality.WEAK
            else -> ConnectionQuality.POOR
        }
        if (rssi <= RSSI_CRITICAL_THRESHOLD) {
            scope.launch {
                _incomingMessages.emit(Message(MessageType.HEARTBEAT, "RSSI_CRITICAL:$rssi"))
            }
        }
    }

    fun estimateDistance(): Double {
        val rssi = _rssi.value
        if (rssi == 0) return -1.0
        val ratio = rssi * 1.0 / -59.0
        return if (ratio < 1.0) Math.pow(ratio, 10.0)
        else (0.89976) * Math.pow(ratio, 7.7095) + 0.111
    }

    fun disconnect() {
        heartbeatJob?.cancel()
        reconnectJob?.cancel()
        readJob?.cancel()
        scanJob?.cancel()
        try {
            classicSocket?.close()
            if (hasPermission(Manifest.permission.BLUETOOTH_CONNECT) &&
                ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED) {
                bleGatt?.close()
            }
        } catch (e: Exception) { Timber.e(e) }
        classicSocket = null
        bleGatt = null
        _connectionState.value = ConnectionState.DISCONNECTED
        _connectedDevice.value = null
    }

    private fun hasPermission(permission: String): Boolean {
        return ActivityCompat.checkSelfPermission(context, permission) ==
                PackageManager.PERMISSION_GRANTED
    }
}
