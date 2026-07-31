package com.alinam.smartconnect.shared.protocol

data class Message(
    val type: String,
    val payload: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
