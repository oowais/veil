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

Requires Android 11+ (arm64). Install the latest release:
[`veil.apk`](https://github.com/oowais/veil/releases/latest/download/veil.apk) (allow "install
unknown apps" for your browser or file manager). Later versions install from inside the app, on the
**Updates** tab.

For development, every CI run on `main` also uploads `app-debug.apk` (readable crash traces) and
`app-profile.apk` (optimised, for timings) as the `veil-apks-<sha>` artifact. These are "Veil dev"
(`com.owaisahmed.veil.dev`), signed with a public debug key; they install next to the release app
and don't update themselves.

To build locally instead: `./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`.

- **Trigger:** tap *Set as assistant*, then swipe up from a bottom corner. Each gesture is logged
  on screen and in logcat (`adb logcat -s VeilTrigger`).
- **OCR:** download the OCR model (~31 MB), pick a German screenshot and check the recognised text,
  umlauts and timings.
- **Translate:** download Hy-MT2 (~1.1 GB), load it and run the ten German samples. Shows load
  time, memory use and tokens per second.

## Privacy

Veil does no analytics and sends no telemetry. It uses the network for two things only:

- **Model downloads**, which you start yourself. Models can be side-loaded instead; see
  [MODELS.md](MODELS.md).
- **Update check:** once a day, when you open the app, Veil asks GitHub
  (`api.github.com/repos/oowais/veil/releases/latest`) for the latest version. GitHub sees your IP
  address; nothing else is sent. Turn it off on the Updates tab; "Check for updates" still works by
  hand. Updates are only downloaded and installed when you tap Install, and Android asks you to
  confirm. They are signed with a key only the maintainer holds.

## License

Apache-2.0 for Veil's own code. Model licenses are listed in [MODELS.md](MODELS.md).
