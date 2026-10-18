# Contributing

## Ground rules

- No Google Play Services, ML Kit, Firebase, analytics or crash reporting. Ever.
- Nothing leaves the device except model downloads the user starts.
- New dependencies must be FOSS and buildable for F-Droid.

## Building

```bash
git clone --recurse-submodules https://github.com/<owner>/veil
./gradlew testDebugUnitTest assembleDebug
```

Needs JDK 17+, Android SDK platform 37, NDK 29.0.14206865 and CMake 3.31.6.
llama.cpp is a pinned git submodule in `third_party/llama.cpp`; to bump it, check out a newer
release tag there and commit the submodule pointer.

## Modules

| Module | Purpose |
|---|---|
| `:app` | App UI (`ui/`: Home, Models, Settings), `pipeline/` (trigger → capture → translate → overlay), `update/` |
| `:trigger` | Assistant role: VoiceInteractionService/Session, RecognitionService stub |
| `:ocr` | PP-OCR detector + recognizer on ONNX Runtime |
| `:layout` | OCR lines → paragraphs, hyphen joining |
| `:translate` | `Translator` interface, llama.cpp JNI bridge |
| `:models` | Model registry, resumable SHA-256-checked downloads |
