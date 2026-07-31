package com.alinam.smartconnect.wear.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import com.alinam.smartconnect.shared.protocol.Message
import com.alinam.smartconnect.shared.protocol.MessageType
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import timber.log.Timber
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WearBluetoothManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val gson = Gson()

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? get() = bluetoothManager.adapter

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var socket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private var inputStream: InputStream? = null
    private var readJob: Job? = null
    private var reconnectJob: Job? = null

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _messages = MutableSharedFlow<Message>(extraBufferCapacity = 64)
    val messages: kotlinx.coroutines.flow.SharedFlow<Message> = _messages.asSharedFlow()

    private var lastConnectedAddress: String? = null

    // Scan delays
    private var scanDelayMs = 5000L

    var onMessageReceived: ((Message) -> Unit)? = null

    fun startSmartReconnect() {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            while (isActive) {
                if (!_isConnected.value) {
                    tryReconnect()
                    scanDelayMs = 5000L
                } else {
                    scanDelayMs = 30000L
                }
                delay(scanDelayMs)
            }
        }
    }

    private suspend fun tryReconnect() {
        val addr = lastConnectedAddress
        if (addr != null) {
            try {
                val device = adapter?.getRemoteDevice(addr) ?: return
                connectToDevice(device)
            } catch (e: Exception) {
                Timber.w("Wear reconnect failed: ${e.message}")
            }
        } else {
            // Scan for phone
            scanForPhone()
        }
    }

    private fun scanForPhone() {
        val paired = adapter?.bondedDevices ?: return
        for (device in paired) {
            scope.launch {
                try {
                    connectToDevice(device)
                } catch (e: Exception) { }
            }
        }
    }

    fun connectToDevice(device: BluetoothDevice) {
        scope.launch {
            try {
                disconnect()
                val sock = device.createRfcommSocketToServiceRecord(SPP_UUID)
                adapter?.cancelDiscovery()
                sock.connect()
                socket = sock
                outputStream = sock.outputStream
                inputStream = sock.inputStream
                lastConnectedAddress = device.address
                _isConnected.value = true
                scanDelayMs = 30000L
                Timber.i("Wear connected to ${device.name}")
                startReading()
                sendHandshake()
            } catch (e: IOException) {
                Timber.w("Wear connect failed: ${e.message}")
                _isConnected.value = false
            }
        }
    }

    private fun sendHandshake() {
        sendMessage(Message(MessageType.HANDSHAKE, ""))
    }

    private fun startReading() {
        readJob?.cancel()
        readJob = scope.launch {
            try {
                val stream = inputStream ?: return@launch
                val buffer = StringBuilder()
                val byteArray = ByteArray(4096)
                while (isActive && _isConnected.value) {
                    val bytesRead = stream.read(byteArray)
                    if (bytesRead > 0) {
                        val received = String(byteArray, 0, bytesRead, Charsets.UTF_8)
                        buffer.append(received)
                        while (buffer.contains("\n")) {
                            val idx = buffer.indexOf("\n")
                            val json = buffer.substring(0, idx)
                            buffer.delete(0, idx + 1)
                            if (json.isNotBlank()) {
                                handleLine(json)
                            }
                        }
                        if (buffer.length > 1_048_576) buffer.clear()
                    } else if (bytesRead == -1) break
                }
            } catch (e: Exception) {
                Timber.w("Wear read error: ${e.message}")
                handleDisconnect()
            }
        }
    }

    private fun handleLine(json: String) {
        try {
            val msg = gson.fromJson(json, Message::class.java) ?: return
            _messages.tryEmit(msg)
            onMessageReceived?.invoke(msg)
        } catch (e: Exception) {
            Timber.w(e, "Wear parse error: $json")
        }
    }

    fun sendMessage(message: Message) {
        scope.launch {
            try {
                val out = outputStream ?: return@launch
                val json = (gson.toJson(message) + "\n").toByteArray(Charsets.UTF_8)
                synchronized(out) {
                    out.write(json)
                    out.flush()
                }
            } catch (e: Exception) {
                Timber.w("Wear send failed: ${e.message}")
                handleDisconnect()
            }
        }
    }

    private fun handleDisconnect() {
        _isConnected.value = false
        disconnect()
        startSmartReconnect()
    }

    fun disconnect() {
        try {
            readJob?.cancel()
            outputStream?.close()
            inputStream?.close()
            socket?.close()
        } catch (e: Exception) { } finally {
            socket = null
            outputStream = null
            inputStream = null
            _isConnected.value = false
        }
    }
}
