# Veil

Offline Android app: swipe the assistant gesture and the German text on screen is replaced,
in place, by English. No Google dependencies, no network after the models are downloaded.

> **Status: milestone 0.** The app currently contains three spike screens that de-risk the core
> pieces on a real phone. It does not translate your screen yet.

## How it will work

1. Veil is set as the **default digital assistant**, so the corner swipe / long-press power fires it.
2. It reads on-screen text from the accessibility tree, or takes a screenshot and runs OCR
   (PP-OCRv6 on ONNX Runtime).
3. Wrapped lines are grouped into paragraphs and translated by Hy-MT2 1.8B running in llama.cpp.
4. The English text is drawn over the original in an accessibility overlay.

## Trying the spikes

Requires Android 11+ (arm64). Every CI run on `main` uploads the APKs: open the run under
**Actions**, download the `veil-apks-<sha>` artifact, unzip it and install `app-profile.apk`
(optimised; `app-debug.apk` is only for readable crash traces). Allow "install unknown apps" for
your browser or file manager. CI builds share a signing key, so newer APKs install over older ones.

To build locally instead: `./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`.

- **Trigger:** tap *Set as assistant*, then swipe up from a bottom corner. Each gesture is logged
  on screen and in logcat (`adb logcat -s VeilTrigger`).
- **OCR:** download the OCR model (~31 MB), pick a German screenshot and check the recognised text,
  umlauts and timings.
- **Translate:** download Hy-MT2 (~1.1 GB), load it and run the ten German samples. Shows load
  time, memory use and tokens per second.

## Privacy

Veil does no analytics and sends no telemetry. The INTERNET permission is used only for model
downloads, which you start yourself. Models can be side-loaded instead; see [MODELS.md](MODELS.md).

## License

Apache-2.0 for Veil's own code. Model licenses are listed in [MODELS.md](MODELS.md).
