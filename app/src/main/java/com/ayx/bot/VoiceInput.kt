package com.ayx.bot

import android.content.Context
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/** Mic recording -> Groq Whisper speech-to-text. Returns the transcribed text. */
object VoiceInput {
    private const val WHISPER_URL = "https://api.groq.com/openai/v1/audio/transcriptions"
    private const val WHISPER_MODEL = "whisper-large-v3-turbo"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    fun startRecording(context: Context): MediaRecorder {
        val outFile = File(context.cacheDir, "ayxbot_mic.m4a")
        if (outFile.exists()) outFile.delete()
        val recorder = MediaRecorder()
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        recorder.setAudioEncodingBitRate(128000)
        recorder.setAudioSamplingRate(16000)
        recorder.setOutputFile(outFile.absolutePath)
        recorder.prepare()
        recorder.start()
        return recorder
    }

    fun stopRecording(recorder: MediaRecorder?) {
        try {
            recorder?.stop()
        } catch (e: Exception) {
        }
        try {
            recorder?.release()
        } catch (e: Exception) {
        }
    }

    fun recordedFile(context: Context): File = File(context.cacheDir, "ayxbot_mic.m4a")

    /** Uploads the recorded file to Groq Whisper and returns plain text. Throws on failure. */
    suspend fun transcribe(context: Context, groqKey: String, language: String): String =
        withContext(Dispatchers.IO) {
            val file = recordedFile(context)
            if (!file.exists() || file.length() < 1000) {
                throw IllegalStateException("Recording too short — hold the mic and speak")
            }
            val langCode = language.substringBefore("-") // hi / en / bn
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", "voice.m4a", file.asRequestBody("audio/mp4".toMediaType()))
                .addFormDataPart("model", WHISPER_MODEL)
                .addFormDataPart("language", langCode)
                .build()
            val req = Request.Builder()
                .url(WHISPER_URL)
                .header("Authorization", "Bearer $groqKey")
                .post(body)
                .build()
            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw IllegalStateException("Whisper error ${resp.code}: ${text.take(200)}")
                }
                JSONObject(text).optString("text", "").trim()
            }
        }
}
