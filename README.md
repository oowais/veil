# Veil

Offline Android app: swipe the assistant gesture and the German text on screen is replaced,
in place, by English. No Google dependencies, no network after the models are downloaded.

> **Status: early (0.x).** Works on a Pixel 9; expect rough edges on other phones.

## How it works

1. Veil is set as the **default digital assistant**, so the corner swipe / long-press power fires it.
2. It reads on-screen text from the accessibility tree and, optionally, runs OCR on a screenshot
   for text inside images (PP-OCRv6 on ONNX Runtime).
3. Wrapped lines are grouped into paragraphs and translated on the phone: by default with OPUS-MT
   (small and fast, ONNX Runtime), or with Hy-MT2 1.8B in llama.cpp (better, slower to load).
4. The English text is drawn over the original in an accessibility overlay.

## Getting started

Requires Android 11+ (arm64). Install the latest release:
[`veil.apk`](https://github.com/oowais/veil/releases/latest/download/veil.apk) (allow "install
unknown apps" for your browser or file manager). Later versions install from inside the app
(Settings → Updates).

1. **Home** shows a setup checklist: make Veil the digital assistant, turn on its accessibility
   service, and download a translation model (on **Models**). Text recognition for images is optional.
2. In any app, swipe up diagonally from a bottom corner. Tap a box to see the German, long-press to
   copy it, drag the toolbar aside; Back or a tap outside closes it.
3. **When I swipe** on Home switches between *Translate* and *Copy text* (outlines text so you can
   copy it, no translation).
4. **History** lists everything translated (with app and time); copy, delete one, or delete all.
   Turn off *Save translations* there to keep nothing.
5. Something wrong? Settings → Troubleshooting → Activity log → Copy, and attach it to an issue.

To build locally: `./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`. Debug
and profile builds are "Veil dev" (`com.owaisahmed.veil.dev`), signed with a public debug key; they
install next to the release app and don't update themselves.

## Privacy

Veil does no analytics and sends no telemetry. Saved translations (History, and the cache that makes
repeated screens instant) stay in the app's private storage on the phone; turn saving off or delete
them on the History screen. It uses the network for two things only:

- **Model downloads**, which you start yourself. Models can be side-loaded instead; see
  [MODELS.md](MODELS.md).
- **Update check:** once a day, when you open the app, Veil asks GitHub
  (`api.github.com/repos/oowais/veil/releases/latest`) for the latest version. GitHub sees your IP
  address; nothing else is sent. Turn it off in Settings → Updates; "Check now" still works by
  hand. Updates are only downloaded and installed when you tap Update, and Android asks you to
  confirm. They are signed with a key only the maintainer holds.

## License

Apache-2.0 for Veil's own code. Model licenses are listed in [MODELS.md](MODELS.md).
