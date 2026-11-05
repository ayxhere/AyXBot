package com.ayx.bot

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

/** Chat history persisted locally as a JSON file. Nothing leaves the device. */
object ChatStore {
    private const val FILE_NAME = "chat_history.json"
    private val gson = Gson()
    private const val MAX_MESSAGES = 200

    data class StoredMessage(val role: String, val content: String)

    fun load(context: Context): MutableList<StoredMessage> {
        return try {
            val file = File(context.filesDir, FILE_NAME)
            if (!file.exists()) return mutableListOf()
            val type = object : TypeToken<List<StoredMessage>>() {}.type
            val list: List<StoredMessage> = gson.fromJson(file.readText(), type) ?: emptyList()
            list.toMutableList()
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    fun save(context: Context, messages: List<StoredMessage>) {
        try {
            val trimmed = messages.takeLast(MAX_MESSAGES)
            File(context.filesDir, FILE_NAME).writeText(gson.toJson(trimmed))
        } catch (e: Exception) {
            // History save is best-effort; chat must never crash over it.
        }
    }

    fun clear(context: Context) {
        try {
            File(context.filesDir, FILE_NAME).delete()
        } catch (e: Exception) {
        }
    }

    fun toBrainMessages(messages: List<StoredMessage>): List<ChatMsg> =
        messages.map { ChatMsg(it.role, it.content) }
}
