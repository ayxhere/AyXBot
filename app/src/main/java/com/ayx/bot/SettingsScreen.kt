package com.ayx.bot

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private val Accent = Color(0xFF4ADE80)
private val Bg = Color(0xFF0B0F14)
private val Surface = Color(0xFF111827)
private val TextMain = Color(0xFFE5E7EB)
private val TextDim = Color(0xFF9CA3AF)

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()

    var groqKey by remember { mutableStateOf(SecureKeys.getApiKey(context, Provider.GROQ)) }
    var openRouterKey by remember { mutableStateOf(SecureKeys.getApiKey(context, Provider.OPENROUTER)) }
    var showGroq by remember { mutableStateOf(false) }
    var showOr by remember { mutableStateOf(false) }
    var provider by remember { mutableStateOf(SecureKeys.getProvider(context)) }
    var model by remember { mutableStateOf(SecureKeys.getModel(context)) }
    var voiceLang by remember { mutableStateOf(SecureKeys.getVoiceLang(context)) }
    var voiceName by remember { mutableStateOf(SecureKeys.getVoiceName(context)) }
    var pitch by remember { mutableStateOf(SecureKeys.getPitch(context)) }
    var rate by remember { mutableStateOf(SecureKeys.getRate(context)) }
    var autoSpeak by remember { mutableStateOf(SecureKeys.getAutoSpeak(context)) }
    var savedMsg by remember { mutableStateOf("") }
    var packStates by remember { mutableStateOf(mapOf<String, String>()) }
    var packProgress by remember { mutableStateOf(mapOf<String, Pair<Long, Long>>()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = TextMain)
            }
            Text("Settings", color = TextMain, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }

        SectionTitle("API Keys")
        Text(
            "Keys stay on this device (encrypted). Get a Groq key at console.groq.com, an OpenRouter key at openrouter.ai/keys.",
            color = TextDim, fontSize = 13.sp
        )
        Spacer(Modifier.height(8.dp))
        KeyField(
            label = "Groq API key",
            value = groqKey,
            show = showGroq,
            onShowChange = { showGroq = it },
            onValueChange = { groqKey = it }
        )
        KeyField(
            label = "OpenRouter API key",
            value = openRouterKey,
            show = showOr,
            onShowChange = { showOr = it },
            onValueChange = { openRouterKey = it }
        )
        Button(
            onClick = {
                SecureKeys.setApiKey(context, Provider.GROQ, groqKey)
                SecureKeys.setApiKey(context, Provider.OPENROUTER, openRouterKey)
                savedMsg = "Keys saved."
            },
            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.Black)
        ) { Text("Save keys") }

        SectionTitle("Brain")
        Text("Provider", color = TextMain, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Provider.values().forEach { p ->
                OutlinedButton(
                    onClick = {
                        provider = p
                        SecureKeys.setProvider(context, p)
                        model = p.models.first()
                        SecureKeys.setModel(context, model)
                    },
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (provider == p) Accent else Color.Transparent,
                        contentColor = if (provider == p) Color.Black else TextMain
                    )
                ) { Text(p.label) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Model", color = TextMain, fontWeight = FontWeight.Bold)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            provider.models.forEach { m ->
                OutlinedButton(
                    onClick = {
                        model = m
                        SecureKeys.setModel(context, m)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (model == m) Surface else Color.Transparent,
                        contentColor = if (model == m) Accent else TextMain
                    )
                ) { Text(m) }
            }
        }

        SectionTitle("Voice")
        Text("Language", color = TextMain, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("hi-IN" to "Hindi", "en-IN" to "English", "bn-IN" to "Bangla").forEach { (code, label) ->
                OutlinedButton(
                    onClick = {
                        voiceLang = code
                        SecureKeys.setVoiceLang(context, code)
                        voiceName = ""
                        SecureKeys.setVoiceName(context, "")
                    },
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (voiceLang == code) Accent else Color.Transparent,
                        contentColor = if (voiceLang == code) Color.Black else TextMain
                    )
                ) { Text(label) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Voice (on this device)", color = TextMain, fontWeight = FontWeight.Bold)
        Text("Pick a voice in the chat screen's speaker menu — system voices differ per phone.", color = TextDim, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        Text("Pitch (soft tune): ${"%.2f".format(pitch)}", color = TextMain)
        Slider(
            value = pitch, onValueChange = {
                pitch = it
                SecureKeys.setPitch(context, it)
            },
            valueRange = 0.5f..1.5f,
            colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent)
        )
        Text("Speed: ${"%.2f".format(rate)}", color = TextMain)
        Slider(
            value = rate, onValueChange = {
                rate = it
                SecureKeys.setRate(context, it)
            },
            valueRange = 0.5f..1.5f,
            colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Auto-speak replies", color = TextMain)
            Switch(
                checked = autoSpeak,
                onCheckedChange = {
                    autoSpeak = it
                    SecureKeys.setAutoSpeak(context, it)
                },
                colors = SwitchDefaults.colors(checkedThumbColor = Accent, checkedTrackColor = Accent.copy(alpha = 0.5f))
            )
        }

        SectionTitle("Voice Packs")
        Text(
            "Neural Kokoro models — downloaded to this device. The neural engine is v2; v1 speaks through system TTS.",
            color = TextDim, fontSize = 13.sp
        )
        Spacer(Modifier.height(8.dp))
        VoicePackManager.PACKS.forEach { pack ->
            val downloaded = VoicePackManager.isDownloaded(context, pack)
            val progress = packProgress[pack.id]
            val state = packStates[pack.id].orEmpty()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
            ) {
                Text(pack.name, color = TextMain, fontWeight = FontWeight.Bold)
                val totalMb = VoicePackManager.totalBytes(pack) / 1_000_000
                val doneMb = VoicePackManager.downloadedBytes(context, pack) / 1_000_000
                Text(
                    if (downloaded) "Downloaded ($doneMb MB)"
                    else if (progress != null) "Downloading… ${progress.first / 1_000_000} / ${progress.second / 1_000_000} MB"
                    else "Not downloaded — about $totalMb MB",
                    color = TextDim, fontSize = 13.sp
                )
                if (state.isNotEmpty()) Text(state, color = Accent, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!downloaded) {
                        Button(
                            onClick = {
                                scope.launch {
                                    try {
                                        packStates = packStates + (pack.id to "")
                                        VoicePackManager.downloadPack(context, pack) { d, t ->
                                            packProgress = packProgress + (pack.id to (d to t))
                                        }
                                        packStates = packStates + (pack.id to "Download complete.")
                                        packProgress = packProgress - pack.id
                                    } catch (e: Exception) {
                                        packStates = packStates + (pack.id to "Failed: ${e.message}")
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.Black)
                        ) { Text("Download") }
                    } else {
                        OutlinedButton(onClick = {
                            VoicePackManager.deletePack(context, pack)
                            packStates = packStates + (pack.id to "Deleted.")
                        }) { Text("Delete", color = TextMain) }
                    }
                }
            }
        }

        SectionTitle("Permissions")
        PermissionRow(context, "Draw over other apps (floating ball)") {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + context.packageName))
            )
        }
        PermissionRow(context, "Microphone (voice listen)") {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName))
            )
        }

        SectionTitle("About")
        Text("AyXBot 1.0.0 — by imayx", color = TextMain)
        Text("github.com/ayxhere/AyXBot", color = TextDim, fontSize = 13.sp)
        Text(
            "Your keys and chat history never leave this device. Voice audio for speech-to-text goes to Groq Whisper only when you tap the mic.",
            color = TextDim, fontSize = 13.sp
        )
        if (savedMsg.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(savedMsg, color = Accent)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Spacer(Modifier.height(20.dp))
    Text(text, color = Accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun KeyField(
    label: String,
    value: String,
    show: Boolean,
    onShowChange: (Boolean) -> Unit,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = TextDim) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            IconButton(onClick = { onShowChange(!show) }) {
                Icon(
                    if (show) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = null, tint = TextDim
                )
            }
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = TextMain,
            unfocusedTextColor = TextMain,
            focusedBorderColor = Accent,
            unfocusedBorderColor = TextDim
        )
    )
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun PermissionRow(context: Context, label: String, onOpen: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = TextMain, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = onOpen) { Text("Open", color = TextMain) }
    }
}
