package com.alinam.smartconnect.mobile.data.repository

import com.alinam.smartconnect.shared.model.DeviceInfo
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceInfoRepository @Inject constructor() {
    private val gson = Gson()
    private val _watchInfo = MutableStateFlow<DeviceInfo?>(null)
    val watchInfo: StateFlow<DeviceInfo?> = _watchInfo.asStateFlow()

    fun updateWatchInfo(json: String) {
        try {
            _watchInfo.value = gson.fromJson(json, DeviceInfo::class.java)
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse watch device info")
        }
    }
}
