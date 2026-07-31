package com.alinam.smartconnect.mobile.data.model

enum class ConnectionQuality {
    EXCELLENT,
    GOOD,
    FAIR,
    POOR,
    UNKNOWN;
}

fun ConnectionQuality.toLabel(): String = when (this) {
    ConnectionQuality.EXCELLENT -> "عالی"
    ConnectionQuality.GOOD -> "خوب"
    ConnectionQuality.FAIR -> "متوسط"
    ConnectionQuality.POOR -> "ضعیف"
    ConnectionQuality.UNKNOWN -> "نامشخص"
}

fun ConnectionQuality.toColor(): Int = when (this) {
    ConnectionQuality.EXCELLENT -> 0xFF00E676.toInt()
    ConnectionQuality.GOOD -> 0xFF76FF03.toInt()
    ConnectionQuality.FAIR -> 0xFFFFD740.toInt()
    ConnectionQuality.POOR -> 0xFFFF1744.toInt()
    ConnectionQuality.UNKNOWN -> 0xFF9E9E9E.toInt()
}

fun rssiToQuality(rssi: Int): ConnectionQuality = when {
    rssi >= -60 -> ConnectionQuality.EXCELLENT
    rssi >= -70 -> ConnectionQuality.GOOD
    rssi >= -80 -> ConnectionQuality.FAIR
    rssi > -90 -> ConnectionQuality.POOR
    else -> ConnectionQuality.UNKNOWN
}
