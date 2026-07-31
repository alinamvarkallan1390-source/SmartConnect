package com.alinam.smartconnect.mobile.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alinam.smartconnect.mobile.data.repository.ConnectionRepository
import com.alinam.smartconnect.mobile.util.BatteryOptimizationHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val connectionRepository: ConnectionRepository,
    private val batteryHelper: BatteryOptimizationHelper,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val connectionLogs = connectionRepository.getConnectionLogs()

    private val _isOptimized = MutableStateFlow(false)
    val isOptimized: StateFlow<Boolean> = _isOptimized.asStateFlow()

    private val _isMiui = MutableStateFlow(false)
    val isMiui: StateFlow<Boolean> = _isMiui.asStateFlow()

    init {
        checkBatteryStatus()
    }

    private fun checkBatteryStatus() {
        _isOptimized.value = !batteryHelper.isIgnoringBatteryOptimizations()
        _isMiui.value = batteryHelper.isMiuiDevice()
    }

    /** Re-evaluate battery optimization / MIUI state. Call from screen onResume. */
    fun refresh() {
        checkBatteryStatus()
    }

    fun requestIgnoreBatteryOptimizations() {
        batteryHelper.requestIgnoreBatteryOptimizations(context)
    }

    fun openAutoStartSettings() {
        batteryHelper.openAutoStartSettings(context)
    }

    fun clearLogs() {
        viewModelScope.launch { connectionRepository.clearOldLogs() }
    }

    fun exportLogs(): String {
        // Build log string for export
        return "SmartConnect Logs exported at ${java.util.Date()}"
    }
}
