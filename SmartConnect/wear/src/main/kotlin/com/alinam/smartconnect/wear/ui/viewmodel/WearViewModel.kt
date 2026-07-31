package com.alinam.smartconnect.wear.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alinam.smartconnect.shared.model.DeviceInfo
import com.alinam.smartconnect.shared.protocol.Message
import com.alinam.smartconnect.shared.protocol.MessageType
import com.alinam.smartconnect.wear.bluetooth.WearBluetoothManager
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WearViewModel @Inject constructor(
    private val btManager: WearBluetoothManager
) : ViewModel() {
    private val gson = Gson()

    val isConnected = btManager.isConnected

    private val _phoneInfo = MutableStateFlow<DeviceInfo?>(null)
    val phoneInfo: StateFlow<DeviceInfo?> = _phoneInfo.asStateFlow()

    init {
        viewModelScope.launch {
            btManager.messages.collect { msg ->
                msg ?: return@collect
                when (msg.type) {
                    MessageType.DEVICE_INFO_RESPONSE -> {
                        try {
                            _phoneInfo.value = gson.fromJson(msg.payload, DeviceInfo::class.java)
                        } catch (e: Exception) { }
                    }
                }
            }
        }
    }

    fun findPhone() {
        btManager.sendMessage(Message(MessageType.FIND_PHONE))
    }

    fun stopFindPhone() {
        btManager.sendMessage(Message(MessageType.FIND_PHONE_STOP))
    }
}
