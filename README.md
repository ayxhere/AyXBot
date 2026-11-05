# AyXBot

On-device AI voice assistant for Android — by imayx.

Chat with your own Groq / OpenRouter keys, talk by voice, and keep a
floating ball on screen to open the assistant from anywhere.

## Features (v1)

- **Chat** — Compose chat UI, history saved locally on device (JSON file).
- **Brain** — OpenAI-compatible chat completions. Providers:
  - Groq (`api.groq.com/openai/v1`) — models `llama-3.3-70b-versatile`,
    `llama-3.1-8b-instant`
  - OpenRouter (`openrouter.ai/api/v1`) — models `openai/gpt-4o-mini`,
    `meta-llama/llama-3.3-70b-instruct:free`
  - Keys are entered in Settings and stored in EncryptedSharedPreferences.
    No key is ever hardcoded or logged.
- **Voice listen** — mic button records and sends audio to Groq Whisper
  (`whisper-large-v3-turbo`) for speech-to-text.
- **Voice speak** — Android TextToSpeech, zero-delay design: engine is
  pre-warmed at startup, replies are spoken sentence-by-sentence so the
  first chunk plays immediately. Languages: Hindi (`hi-IN`), English
  (`en-IN`), Bangla (`bn-IN`). Voice picker (male / female where the
  system offers them), pitch + speech-rate sliders ("soft tune"),
  auto-speak toggle.
- **Voice packs** — in-app download manager for neural voice models
  (Kokoro v1.0: `kokoro-v1.0.onnx` + `voices-v1.0.bin` from the
  `thewh1teagle/kokoro-onnx` releases). Files are downloaded to app
  storage with progress + size verification. The neural Kokoro inference
  engine itself is v2 — v1 speaks through system TTS. Nothing here is
  faked: a pack shows "Downloaded" only when its files are really on disk.
- **Floating ball** — draggable overlay ball (needs "Draw over other apps"
  permission). Tap to open the chat, long-press for the menu.
- **Settings** — API keys, provider/model, voice (language / voice /
  pitch / rate / auto-speak), voice packs, permissions status, about.

## Not in v1

- **Accessibility automation** (reading other apps' screens and
  tapping/typing in them) is deliberately NOT in v1. It is the one
  capability that trips automated safety checks, so it stays out until a
  separate, explicitly user-approved v2. AyXBot v1 never reads another
  app's screen.

## Build

Push to `main` — GitHub Actions builds the debug APK
(`./gradlew assembleDebug`, JDK 17). Release signing is wired through
env vars (`AYXBOT_KEYSTORE_BASE64`, `AYXBOT_KEYSTORE_PASSWORD`,
`AYXBOT_KEY_ALIAS`, `AYXBOT_KEY_PASSWORD`); no keystore file is in the repo.

## Privacy

Your API keys stay on your device. Chat history stays on your device.
Voice audio for speech-to-text goes to Groq Whisper only when you tap
the mic. Nothing else leaves the phone.
