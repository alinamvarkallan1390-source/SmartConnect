package com.alinam.smartconnect.shared.protocol

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException

data class Message(
    val type: String,
    val payload: String = "",
    val timestamp: Long = System.currentTimeMillis()
) {
    private val gson = Gson()

    fun toJson(): String = gson.toJson(this)

    companion object {
        private val gsonStatic = Gson()

        @JvmStatic
        @Throws(JsonSyntaxException::class)
        fun fromJson(json: String): Message {
            return gsonStatic.fromJson(json, Message::class.java)
                ?: throw JsonSyntaxException("Cannot parse Message from: $json")
        }
    }
}
