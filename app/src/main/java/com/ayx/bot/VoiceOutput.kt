package com.ayx.bot

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import java.util.Locale

/**
 * Voice output via Android TextToSpeech — zero-delay design:
 * - Engine is created + warmed up once at app start (setLanguage immediately).
 * - Long replies are split into sentences and queued (QUEUE_ADD), so the
 *   first sentence starts playing right away instead of waiting for the
 *   whole reply to synthesize.
 * - Pitch + speech rate sliders give the "soft tune" control.
 * Languages: Hindi (hi-IN), English (en-IN), Bangla (bn-IN) only.
 */
class VoiceOutput(private val context: Context) {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pendingSpeak: String? = null

    /** Call once from MainActivity.onCreate — pre-warms the engine. */
    fun init() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ready = true
                applySettings()
                pendingSpeak?.let { speak(it); pendingSpeak = null }
            }
        }
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
        }
        tts = null
        ready = false
    }

    fun stop() {
        try {
            tts?.stop()
        } catch (e: Exception) {
        }
    }

    /** Applies saved language / voice / pitch / rate to the engine. */
    fun applySettings() {
        val engine = tts ?: return
        if (!ready) return
        val lang = SecureKeys.getVoiceLang(context)
        val locale = when (lang) {
            "hi-IN" -> Locale("hi", "IN")
            "bn-IN" -> Locale("bn", "IN")
            else -> Locale("en", "IN")
        }
        engine.language = locale
        val savedVoice = SecureKeys.getVoiceName(context)
        if (savedVoice.isNotEmpty()) {
            engine.voices?.firstOrNull { it.name == savedVoice }?.let { engine.voice = it }
        }
        engine.setPitch(SecureKeys.getPitch(context))
        engine.setSpeechRate(SecureKeys.getRate(context))
    }

    /** Voices available on this device for the given language tag (hi-IN / en-IN / bn-IN). */
    fun voicesFor(lang: String): List<Voice> {
        val engine = tts ?: return emptyList()
        val locale = when (lang) {
            "hi-IN" -> Locale("hi", "IN")
            "bn-IN" -> Locale("bn", "IN")
            else -> Locale("en", "IN")
        }
        return try {
            engine.voices
                ?.filter { it.locale.language == locale.language }
                ?.sortedBy { it.name }
                .orEmpty()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Speaks text with no perceptible start delay (sentence-chunked queue). */
    fun speak(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        val engine = tts
        if (engine == null || !ready) {
            pendingSpeak = clean
            return
        }
        applySettings()
        engine.stop()
        // Split into sentences so playback starts on the first chunk immediately.
        val chunks = clean.split(Regex("""(?<=[.!?।\n])\s+"""))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (chunks.isEmpty()) return
        chunks.forEachIndexed { index, chunk ->
            val mode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            engine.speak(chunk, mode, null, "ayxbot-$index")
        }
    }

    fun isSpeaking(): Boolean = try {
        tts?.isSpeaking == true
    } catch (e: Exception) {
        false
    }
}
