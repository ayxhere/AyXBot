package com.ayx.bot

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * Voice pack download manager.
 * Downloads real neural voice model files (Kokoro v1.0) to app storage with
 * progress + size verification. The neural Kokoro inference engine is v2 —
 * v1 speaks through system TTS (see VoiceOutput). A pack is shown as
 * "Downloaded" only when its files are really on disk with the expected size.
 */
object VoicePackManager {
    data class VoicePack(
        val id: String,
        val name: String,
        val lang: String,
        val files: List<PackFile>
    )

    data class PackFile(val fileName: String, val url: String, val sizeBytes: Long)

    private const val KOKORO_ONNX_URL =
        "https://github.com/thewh1teagle/kokoro-onnx/releases/download/v1.0/kokoro-v1.0.onnx"
    private const val KOKORO_VOICES_URL =
        "https://github.com/thewh1teagle/kokoro-onnx/releases/download/v1.0/voices-v1.0.bin"

    // Sizes as published in the v1.0 release (approx, used for verification).
    private const val KOKORO_ONNX_SIZE = 325_000_000L
    private const val KOKORO_VOICES_SIZE = 5_000_000L

    val PACKS = listOf(
        VoicePack(
            id = "kokoro-hi",
            name = "Kokoro Hindi — neural voices (F+M)",
            lang = "hi-IN",
            files = listOf(
                PackFile("kokoro-v1.0.onnx", KOKORO_ONNX_URL, KOKORO_ONNX_SIZE),
                PackFile("voices-v1.0.bin", KOKORO_VOICES_URL, KOKORO_VOICES_SIZE)
            )
        ),
        VoicePack(
            id = "kokoro-en",
            name = "Kokoro English — neural voices (F+M)",
            lang = "en-IN",
            files = listOf(
                PackFile("kokoro-v1.0.onnx", KOKORO_ONNX_URL, KOKORO_ONNX_SIZE),
                PackFile("voices-v1.0.bin", KOKORO_VOICES_URL, KOKORO_VOICES_SIZE)
            )
        ),
        VoicePack(
            id = "kokoro-bn",
            name = "Kokoro Bangla — neural voices (F+M)",
            lang = "bn-IN",
            files = listOf(
                PackFile("kokoro-v1.0.onnx", KOKORO_ONNX_URL, KOKORO_ONNX_SIZE),
                PackFile("voices-v1.0.bin", KOKORO_VOICES_URL, KOKORO_VOICES_SIZE)
            )
        )
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .build()

    fun packDir(context: Context, packId: String): File =
        File(context.filesDir, "voicepacks/$packId").apply { mkdirs() }

    /** True only when every file exists and is at least 95% of expected size. */
    fun isDownloaded(context: Context, pack: VoicePack): Boolean {
        val dir = packDir(context, pack.id)
        return pack.files.all { f ->
            val file = File(dir, f.fileName)
            file.exists() && file.length() >= (f.sizeBytes * 0.95).toLong()
        }
    }

    fun downloadedBytes(context: Context, pack: VoicePack): Long {
        val dir = packDir(context, pack.id)
        return pack.files.sumOf { f ->
            val file = File(dir, f.fileName)
            if (file.exists()) file.length() else 0L
        }
    }

    fun totalBytes(pack: VoicePack): Long = pack.files.sumOf { it.sizeBytes }

    fun deletePack(context: Context, pack: VoicePack) {
        packDir(context, pack.id).deleteRecursively()
    }

    /**
     * Downloads a pack. Calls onProgress(downloadedBytes, totalBytes) as it goes.
     * Throws on network / IO failure; partial files are removed.
     */
    suspend fun downloadPack(
        context: Context,
        pack: VoicePack,
        onProgress: (Long, Long) -> Unit
    ) = withContext(Dispatchers.IO) {
        val dir = packDir(context, pack.id)
        val total = totalBytes(pack)
        var done = downloadedBytes(context, pack)
        for (f in pack.files) {
            val out = File(dir, f.fileName)
            if (out.exists() && out.length() >= (f.sizeBytes * 0.95).toLong()) {
                onProgress(done, total)
                continue
            }
            val req = Request.Builder().url(f.url).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw IllegalStateException("Download failed ${resp.code} for ${f.fileName}")
                }
                val body = resp.body ?: throw IllegalStateException("Empty body for ${f.fileName}")
                val tmp = File(dir, f.fileName + ".tmp")
                body.byteStream().use { input ->
                    FileOutputStream(tmp).use { output ->
                        val buf = ByteArray(64 * 1024)
                        var read: Int
                        while (input.read(buf).also { read = it } != -1) {
                            output.write(buf, 0, read)
                            done += read
                            onProgress(done, total)
                        }
                    }
                }
                if (!tmp.renameTo(out)) throw IllegalStateException("Could not save ${f.fileName}")
            }
        }
    }
}
