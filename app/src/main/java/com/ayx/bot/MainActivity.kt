package com.ayx.bot

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch

private val Accent = Color(0xFF4ADE80)
private val Bg = Color(0xFF0B0F14)
private val Surface = Color(0xFF111827)
private val BubbleUser = Color(0xFF1F2937)
private val BubbleBot = Color(0xFF14532D)
private val TextMain = Color(0xFFE5E7EB)
private val TextDim = Color(0xFF9CA3AF)

class MainActivity : ComponentActivity() {
    private lateinit var voiceOutput: VoiceOutput
    private var recorder: MediaRecorder? = null

    private val micPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) Toast.makeText(this, "Mic permission needed for voice", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        voiceOutput = VoiceOutput(this)
        voiceOutput.init() // Pre-warm TTS so first speak has no delay.
        setContent { AyXBotApp(voiceOutput, ::onMicTap, ::toggleBall) }
    }

    override fun onDestroy() {
        voiceOutput.shutdown()
        super.onDestroy()
    }

    private fun toggleBall() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
            Toast.makeText(this, "Allow 'Draw over other apps', then tap the ball icon again", Toast.LENGTH_LONG).show()
            return
        }
        val intent = Intent(this, FloatingBallService::class.java)
        if (FloatingBallService.isRunning) {
            stopService(intent)
            Toast.makeText(this, "Ball off", Toast.LENGTH_SHORT).show()
        } else {
            startForegroundService(intent)
            Toast.makeText(this, "Ball on — tap it to chat from anywhere", Toast.LENGTH_SHORT).show()
        }
    }

    /** Called from the chat UI when the mic button is tapped. Returns via callback. */
    private fun onMicTap(onResult: (String?) -> Unit) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
            onResult(null)
            return
        }
        // Toggle: if recording, stop + transcribe; else start recording.
        if (recorder != null) {
            VoiceInput.stopRecording(recorder)
            recorder = null
            onResult("")
            return
        }
        try {
            recorder = VoiceInput.startRecording(this)
            onResult("__recording__")
        } catch (e: Exception) {
            Toast.makeText(this, "Mic failed: ${e.message}", Toast.LENGTH_SHORT).show()
            onResult(null)
        }
    }

    /** Transcribes the last recording with Groq Whisper. */
    suspend fun transcribeLast(groqKey: String, lang: String): String {
        return VoiceInput.transcribe(this, groqKey, lang)
    }
}

@Composable
private fun AyXBotApp(
    voiceOutput: VoiceOutput,
    onMicTap: ((String?) -> Unit) -> Unit,
    onToggleBall: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as MainActivity
    val scope = rememberCoroutineScope()

    var screen by remember { mutableStateOf("chat") }
    var messages by remember { mutableStateOf<List<ChatStore.StoredMessage>>(ChatStore.load(context)) }
    var input by remember { mutableStateOf("") }
    var thinking by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) }
    var showVoicePicker by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    fun save() = ChatStore.save(context, messages)

    fun send(text: String) {
        val clean = text.trim()
        if (clean.isEmpty() || thinking) return
        messages = messages + ChatStore.StoredMessage("user", clean)
        save()
        input = ""
        thinking = true
        status = ""
        val provider = SecureKeys.getProvider(context)
        val model = SecureKeys.getModel(context)
        val key = SecureKeys.getApiKey(context, provider)
        if (key.isEmpty()) {
            messages = messages + ChatStore.StoredMessage(
                "assistant",
                "No ${provider.label} key yet — open Settings and add your API key first."
            )
            save()
            thinking = false
            return
        }
        scope.launch {
            try {
                val reply = BrainClient.chat(provider, model, key, ChatStore.toBrainMessages(messages))
                messages = messages + ChatStore.StoredMessage("assistant", reply)
                save()
                if (SecureKeys.getAutoSpeak(context)) voiceOutput.speak(reply)
            } catch (e: Exception) {
                messages = messages + ChatStore.StoredMessage("assistant", "Error: ${e.message}")
                save()
            } finally {
                thinking = false
            }
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    if (screen == "settings") {
        Box(modifier = Modifier.fillMaxSize().background(Bg)) {
            SettingsScreen(onBack = { screen = "chat" })
        }
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Bg)
    ) {
        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(Accent),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.SmartToy, contentDescription = null, tint = Color.Black)
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("AyXBot", color = TextMain, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                val provider = SecureKeys.getProvider(context)
                Text(
                    "${provider.label} · ${SecureKeys.getModel(context)}",
                    color = TextDim, fontSize = 12.sp
                )
            }
            IconButton(onClick = {
                voiceOutput.stop()
                onToggleBall()
            }) {
                Icon(Icons.Filled.SmartToy, contentDescription = "Ball", tint = if (FloatingBallService.isRunning) Accent else TextDim)
            }
            IconButton(onClick = { showVoicePicker = true }) {
                Icon(Icons.Filled.VolumeUp, contentDescription = "Voice", tint = TextDim)
            }
            IconButton(onClick = {
                messages = mutableListOf()
                ChatStore.clear(context)
                voiceOutput.stop()
            }) {
                Icon(Icons.Filled.Clear, contentDescription = "Clear chat", tint = TextDim)
            }
            IconButton(onClick = { screen = "settings" }) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = TextMain)
            }
        }

        // Messages
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Text(
                        "Namaste! Main AyXBot hun — Groq / OpenRouter se chalta hun.\n\nType karo, ya mic dabake bolo. Settings me apni API key daalo, voice (Hindi / English / Bangla) chuno, aur ball on karke mujhe kahin se bhi bulao.",
                        color = TextDim, fontSize = 15.sp,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }
            }
            items(messages) { msg ->
                val isUser = msg.role == "user"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isUser) BubbleUser else BubbleBot)
                            .padding(12.dp)
                    ) {
                        Column {
                            Text(msg.content, color = TextMain, fontSize = 15.sp)
                            if (!isUser) {
                                Row {
                                    TextButton(onClick = { voiceOutput.speak(msg.content) }) {
                                        Text("▶ Speak", color = Accent, fontSize = 12.sp)
                                    }
                                    TextButton(onClick = { voiceOutput.stop() }) {
                                        Text("■ Stop", color = TextDim, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (thinking) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(color = Accent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Soch raha hun…", color = TextDim, fontSize = 14.sp)
                    }
                }
            }
        }

        if (status.isNotEmpty()) {
            Text(status, color = Accent, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 14.dp))
        }

        // Input row
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(if (recording) "Listening… tap mic to stop" else "Type a message…", color = TextDim) },
                singleLine = false,
                maxLines = 4,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextMain,
                    unfocusedTextColor = TextMain,
                    focusedBorderColor = Accent,
                    unfocusedBorderColor = TextDim,
                    cursorColor = Accent
                )
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = {
                    onMicTap { result ->
                        when (result) {
                            "__recording__" -> {
                                recording = true
                                status = "Listening… tap mic again to send"
                            }
                            "" -> {
                                // Stopped recording — transcribe then send.
                                recording = false
                                status = "Transcribing…"
                                scope.launch {
                                    try {
                                        val groqKey = SecureKeys.getApiKey(context, Provider.GROQ)
                                        if (groqKey.isEmpty()) {
                                            status = "Groq key needed for voice — add it in Settings"
                                            return@launch
                                        }
                                        val text = activity.transcribeLast(groqKey, SecureKeys.getVoiceLang(context))
                                        status = ""
                                        if (text.isNotEmpty()) send(text) else status = "Could not hear anything"
                                    } catch (e: Exception) {
                                        status = "Voice error: ${e.message}"
                                    }
                                }
                            }
                            else -> { /* permission flow */ }
                        }
                    }
                },
                modifier = Modifier.size(48.dp).clip(CircleShape).background(if (recording) Color.Red else Surface)
            ) {
                Icon(
                    if (recording) Icons.Filled.Stop else Icons.Filled.Mic,
                    contentDescription = "Mic",
                    tint = if (recording) Color.White else Accent
                )
            }
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = { send(input) },
                modifier = Modifier.size(48.dp).clip(CircleShape).background(Accent)
            ) {
                Icon(Icons.Filled.Send, contentDescription = "Send", tint = Color.Black)
            }
        }
        Spacer(Modifier.height(4.dp))
    }

    if (showVoicePicker) {
        VoicePickerDialog(
            voiceOutput = voiceOutput,
            onDismiss = { showVoicePicker = false }
        )
    }
}

@Composable
private fun VoicePickerDialog(voiceOutput: VoiceOutput, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val lang = SecureKeys.getVoiceLang(context)
    val voices = remember(lang) { voiceOutput.voicesFor(lang) }
    val saved = SecureKeys.getVoiceName(context)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pick a voice ($lang)", color = TextMain) },
        text = {
            Column {
                if (voices.isEmpty()) {
                    Text(
                        "No system voices found for $lang on this phone. Install the language in Android TTS settings, or pick another language.",
                        color = TextDim
                    )
                } else {
                    voices.forEach { voice ->
                        val female = voice.name.contains("female", ignoreCase = true) ||
                            voice.name.contains("f1", ignoreCase = true)
                        val male = voice.name.contains("male", ignoreCase = true) ||
                            voice.name.contains("m1", ignoreCase = true)
                        val tag = when {
                            female -> " · Female"
                            male -> " · Male"
                            else -> ""
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    SecureKeys.setVoiceName(context, voice.name)
                                    voiceOutput.applySettings()
                                    onDismiss()
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                (if (voice.name == saved) "✓ " else "") + voice.name + tag,
                                color = if (voice.name == saved) Accent else TextMain,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = Accent) }
        },
        containerColor = Surface
    )
}
