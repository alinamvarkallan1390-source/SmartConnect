package com.alinam.smartconnect.wear.util

import android.app.ActivityManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import com.alinam.smartconnect.shared.model.DeviceInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.RandomAccessFile
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceInfoCollectorWear @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun collect(): DeviceInfo {
        val bm = context.getSystemService(BatteryManager::class.java)
        val am = context.getSystemService(ActivityManager::class.java)
        val memInfo = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val stat = StatFs(Environment.getDataDirectory().path)

        return DeviceInfo(
            deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "",
            deviceName = Build.MODEL,
            model = "${Build.MANUFACTURER} ${Build.MODEL}",
            androidVersion = Build.VERSION.RELEASE,
            firmwareVersion = Build.DISPLAY,
            batteryPercent = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY),
            isCharging = bm.isCharging,
            temperature = getBatteryTemp(),
            ramTotal = memInfo.totalMem,
            ramAvailable = memInfo.availMem,
            cpuUsage = getCpuUsage(),
            storageTotal = stat.blockCountLong * stat.blockSizeLong,
            storageAvailable = stat.availableBlocksLong * stat.blockSizeLong,
            signalStrength = 0,
            lastSyncTimestamp = System.currentTimeMillis(),
            wifiEnabled = (context.getSystemService(WifiManager::class.java)).isWifiEnabled,
            bluetoothEnabled = (context.getSystemService(BluetoothManager::class.java)).adapter?.isEnabled == true,
            internetConnected = isInternetConnected(),
            gpsEnabled = (context.getSystemService(LocationManager::class.java))
                .isProviderEnabled(LocationManager.GPS_PROVIDER),
            screenOn = (context.getSystemService(android.os.PowerManager::class.java)).isInteractive,
            topApp = "",
            currentTime = System.currentTimeMillis()
        )
    }

    private fun getBatteryTemp(): Float {
        return try {
            val intent = context.registerReceiver(null,
                android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
            (intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10.0f
        } catch (e: Exception) { 0f }
    }

    private fun getCpuUsage(): Float {
        return try {
            val r1 = RandomAccessFile("/proc/stat", "r")
            val l1 = r1.readLine(); r1.close()
            val t1 = l1.split(" +".toRegex())
            val idle1 = t1[4].toLong()
            val total1 = t1.drop(1).take(7).sumOf { it.toLong() }
            Thread.sleep(200)
            val r2 = RandomAccessFile("/proc/stat", "r")
            val l2 = r2.readLine(); r2.close()
            val t2 = l2.split(" +".toRegex())
            val idle2 = t2[4].toLong()
            val total2 = t2.drop(1).take(7).sumOf { it.toLong() }
            val dIdle = idle2 - idle1
            val dTotal = total2 - total1
            if (dTotal == 0L) 0f else ((dTotal - dIdle).toFloat() / dTotal * 100)
        } catch (e: Exception) { 0f }
    }

    private fun isInternetConnected(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val net = cm.activeNetwork ?: return false
        val cap = cm.getNetworkCapabilities(net) ?: return false
        return cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
