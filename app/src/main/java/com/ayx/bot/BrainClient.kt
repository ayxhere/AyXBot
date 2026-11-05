package com.ayx.bot

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** One chat provider: Groq or OpenRouter. Both speak OpenAI-compatible chat completions. */
enum class Provider(val label: String, val baseUrl: String, val models: List<String>) {
    GROQ(
        "Groq",
        "https://api.groq.com/openai/v1",
        listOf("llama-3.3-70b-versatile", "llama-3.1-8b-instant")
    ),
    OPENROUTER(
        "OpenRouter",
        "https://openrouter.ai/api/v1",
        listOf("openai/gpt-4o-mini", "meta-llama/llama-3.3-70b-instruct:free")
    );

    companion object {
        fun fromName(name: String): Provider =
            values().firstOrNull { it.name == name } ?: GROQ
    }
}

data class ChatMsg(val role: String, val content: String)

private data class ChatRequest(
    val model: String,
    val messages: List<ChatMsg>,
    val temperature: Double = 0.7
)

private data class ChatChoice(val message: ChatMsg?)
private data class ChatResponse(val choices: List<ChatChoice>?)

object BrainClient {
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private const val SYSTEM_PROMPT =
        "You are AyXBot, a helpful on-device AI assistant by imayx. " +
            "Reply in the same language the user speaks (Roman Hinglish, Hindi, English or Bangla). " +
            "Keep replies short and useful unless the user asks for detail."

    /** Sends the conversation and returns the assistant reply text. Throws on failure. */
    suspend fun chat(
        provider: Provider,
        model: String,
        apiKey: String,
        history: List<ChatMsg>
    ): String = withContext(Dispatchers.IO) {
        val messages = mutableListOf(ChatMsg("system", SYSTEM_PROMPT))
        messages.addAll(history.takeLast(20))
        val body = gson.toJson(ChatRequest(model, messages))
            .toRequestBody(jsonType)
        val reqBuilder = Request.Builder()
            .url(provider.baseUrl + "/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(body)
        if (provider == Provider.OPENROUTER) {
            reqBuilder.header("HTTP-Referer", "https://github.com/ayxhere/AyXBot")
                .header("X-Title", "AyXBot")
        }
        val resp = client.newCall(reqBuilder.build()).execute()
        resp.use {
            val text = it.body?.string().orEmpty()
            if (!it.isSuccessful) throw IllegalStateException("Brain error ${it.code}: ${text.take(300)}")
            val parsed = gson.fromJson(text, ChatResponse::class.java)
            val reply = parsed.choices?.firstOrNull()?.message?.content?.trim().orEmpty()
            if (reply.isEmpty()) throw IllegalStateException("Brain gave an empty reply")
            reply
        }
    }
}
