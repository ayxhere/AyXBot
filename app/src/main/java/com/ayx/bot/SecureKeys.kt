package com.ayx.bot

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** API keys + user preferences. Keys live in EncryptedSharedPreferences, never in code. */
object SecureKeys {
    private const val PREFS = "ayxbot_secure"
    private const val KEY_GROQ = "groq_key"
    private const val KEY_OPENROUTER = "openrouter_key"

    // Plain prefs for non-secret settings.
    private const val PLAIN = "ayxbot_prefs"
    const val PREF_PROVIDER = "provider"
    const val PREF_MODEL = "model"
    const val PREF_VOICE_LANG = "voice_lang"
    const val PREF_VOICE_NAME = "voice_name"
    const val PREF_PITCH = "pitch"
    const val PREF_RATE = "rate"
    const val PREF_AUTO_SPEAK = "auto_speak"

    private fun securePrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            PREFS,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun plainPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PLAIN, Context.MODE_PRIVATE)

    fun getApiKey(context: Context, provider: Provider): String =
        securePrefs(context).getString(
            if (provider == Provider.GROQ) KEY_GROQ else KEY_OPENROUTER, ""
        ).orEmpty()

    fun setApiKey(context: Context, provider: Provider, key: String) {
        securePrefs(context).edit().putString(
            if (provider == Provider.GROQ) KEY_GROQ else KEY_OPENROUTER, key.trim()
        ).apply()
    }

    fun getProvider(context: Context): Provider =
        Provider.fromName(plainPrefs(context).getString(PREF_PROVIDER, Provider.GROQ.name).orEmpty())

    fun setProvider(context: Context, provider: Provider) {
        plainPrefs(context).edit().putString(PREF_PROVIDER, provider.name).apply()
    }

    fun getModel(context: Context): String {
        val provider = getProvider(context)
        val saved = plainPrefs(context).getString(PREF_MODEL, "").orEmpty()
        return if (saved.isNotEmpty() && provider.models.contains(saved)) saved
        else provider.models.first()
    }

    fun setModel(context: Context, model: String) {
        plainPrefs(context).edit().putString(PREF_MODEL, model).apply()
    }

    fun getVoiceLang(context: Context): String =
        plainPrefs(context).getString(PREF_VOICE_LANG, "hi-IN").orEmpty()

    fun setVoiceLang(context: Context, lang: String) {
        plainPrefs(context).edit().putString(PREF_VOICE_LANG, lang).apply()
    }

    fun getVoiceName(context: Context): String =
        plainPrefs(context).getString(PREF_VOICE_NAME, "").orEmpty()

    fun setVoiceName(context: Context, name: String) {
        plainPrefs(context).edit().putString(PREF_VOICE_NAME, name).apply()
    }

    fun getPitch(context: Context): Float =
        plainPrefs(context).getFloat(PREF_PITCH, 1.0f)

    fun setPitch(context: Context, pitch: Float) {
        plainPrefs(context).edit().putFloat(PREF_PITCH, pitch).apply()
    }

    fun getRate(context: Context): Float =
        plainPrefs(context).getFloat(PREF_RATE, 1.0f)

    fun setRate(context: Context, rate: Float) {
        plainPrefs(context).edit().putFloat(PREF_RATE, rate).apply()
    }

    fun getAutoSpeak(context: Context): Boolean =
        plainPrefs(context).getBoolean(PREF_AUTO_SPEAK, false)

    fun setAutoSpeak(context: Context, on: Boolean) {
        plainPrefs(context).edit().putBoolean(PREF_AUTO_SPEAK, on).apply()
    }
}
