# Veil — implementation plan

Offline, open-source Android app: swipe the assistant gesture → German text on screen is replaced in place by English. No Google dependencies, no network after model download.

This is the original plan with later decisions applied. Why each change was made is in
[DECISIONS.md](DECISIONS.md); where things stand is in [STATUS.md](STATUS.md).

- **App ID:** `com.owaisahmed.veil` · **Package:** `com.owaisahmed.veil`
- **Stack:** Kotlin, Jetpack Compose (settings UI only), Coroutines, ONNX Runtime Mobile, llama.cpp (JNI)
- **minSdk 30** (D-002) · **targetSdk 35** · **compileSdk 37** (D-008) · arm64-v8a only
- **License:** Apache-2.0 (own code). Model licenses listed in `MODELS.md`.
- **Hard rule:** no Google Play Services, no ML Kit, no Firebase, no analytics.

---

## 1. Architecture

```
Assistant gesture ──► VeilSession (VoiceInteractionSession, invisible)
                         │
                         ▼
                   TriggerController ─────────────► Overlay (dismiss if already shown)
                         │
              ┌──────────┴───────────┐
              ▼                      ▼
     A) Accessibility text     B) Screenshot (a11y takeScreenshot / MediaProjection)
        (no OCR, exact)              │
              │                      ▼
              │                 OCR: detect → recognize (PP-OCRv6, ONNX)
              │                      │
              └──────────┬───────────┘
                         ▼
                 Block builder: lines → paragraphs (+ de-hyphenate)
                         ▼
                 Translator: Hy-MT2 1.8B GGUF (llama.cpp), batched, cached
                         ▼
                 Overlay renderer: cover source box, draw English, auto-fit
```

### Modules (Gradle)
- `:app` — Application, settings UI, onboarding, DI (manual for now; avoid Hilt to keep build light). *Exists (spike screens).*
- `:trigger` — VoiceInteractionService/Session, RecognitionService stub, deeplink activity, QS tile. *Exists (services, controller).*
- `:capture` — `TextSource` interface: `AccessibilityTextSource`, `ScreenshotSource` (a11y / MediaProjection / Shizuku). *Exists: `AccessibilityTextSource`, `VeilAccessibilityService`, `GermanFilter` (milestone 1); screenshot in milestone 2.*
- `:ocr` — ONNX Runtime wrapper, DB detector, SVTR/CTC recognizer, angle cls (optional). *Exists.*
- `:layout` — line→paragraph grouping, reading order, hyphen join. *Exists.*
- `:translate` — `Translator` interface; `LlamaTranslator` (JNI llama.cpp); prompt + glossary; LRU cache. *Exists (no glossary/cache yet).*
- `:overlay` — `TYPE_ACCESSIBILITY_OVERLAY` window, block views, auto-fit text, color sampling. *Exists: `TranslationOverlay` (plain boxes, auto-size text, status line).*
- `:app` also holds `pipeline/` (`Pipeline`: trigger → capture → translate → overlay; `PipelineLog`; Veil setup tab).
- `:models` — model registry, download (resumable, SHA-256 verified), storage, import from file. *Exists (no import yet).*

---

## 2. Trigger: assistant gesture (core feature)

Make Veil selectable as **Default digital assistant app**; corner swipe / long-press power / assist button fires it.

Components (`:trigger`):
- `VeilInteractionService : VoiceInteractionService` (empty)
- `VeilSessionService : VoiceInteractionSessionService` → `VeilSession`
- `VeilSession : VoiceInteractionSession`
  - `onShow()` → `hide()` immediately; `onHide()` → `TriggerController.fire()`. No UI (`setUiEnabled(false)`), no assist structure/screenshot (`setDisabledShowContext(SHOW_WITH_ASSIST or SHOW_WITH_SCREENSHOT)`).
  - Wait for the session window to be gone before capturing (`TriggerController.captureDelayMs`, default 200 ms, tunable in the spike screen) — otherwise the dim scrim gets captured.
- `VeilRecognitionService : RecognitionService` stub — required for the role to be selectable on many OEMs.

Onboarding button: opens the system default-assistant screen (`Settings.ACTION_VOICE_INPUT_SETTINGS`, with fallbacks) — the assistant role cannot be requested via `RoleManager` (D-022); status via `isRoleHeld`.

Secondary triggers (cheap, add in v1):
- Deeplink `veil://translate` (transparent `noHistory` activity) — for Tasker/Key Mapper/adb.
- Quick Settings tile "Veil".
- **No floating ball.** Ever.

Toggle behavior: trigger while overlay visible → dismiss overlay.

---

## 3. Text acquisition — two sources, best first

### A) Accessibility text (preferred, zero OCR)
Most German on screen in normal apps (browsers/WebView, Messages, settings, mail) is real text exposed in the accessibility tree.
- `VeilAccessibilityService` walks `rootInActiveWindow` (all windows via `getWindows()`), collects nodes with non-empty `text`/`contentDescription`, `getBoundsInScreen`, visible-to-user.
- Each node's text is already a full paragraph → solves "sentence split across lines" perfectly, no OCR errors.
- Language check (cheap: char/word heuristics or a tiny langid) → only translate German nodes.
- Gaps: images, video, games, canvas/Flutter apps without semantics, PDFs rendered as images → fall back to B.
- Strategy setting: `Auto` (a11y first; if coverage of screen text is low → OCR), `Accessibility only`, `OCR only`.

### B) Screenshot + OCR
Capture backends (`ScreenshotSource`):
1. `AccessibilityService.takeScreenshot()` (API 30+) — no per-session consent dialog. **Default.** Always available since minSdk is 30.
2. MediaProjection — fallback (Android 14+ asks every session).
3. Shizuku `screencap` — optional, power users.

Hide Veil's own overlay before capture.

---

## 4. OCR engine

| Option | Size | Fit for Veil | Verdict |
|---|---|---|---|
| **PP-OCRv6 small** (det + rec) | 7.7M params | 50 langs incl. 46 Latin-script; ONNX on Hugging Face | **Default** |
| PP-OCRv6 tiny | 1.5M | ~3× faster, less accurate | "Fast" option for low-end phones |
| PP-OCRv6 medium | 34.5M | most accurate, ~5× slower than small | "Accurate" option, flagship phones |
| PP-OCRv5 mobile (ONNX) | ~small | Proven on Android via ONNX Runtime Mobile | Fallback if v6 has issues |
| Tesseract | — | Accuracy clearly worse on screen text | Skip |
| ML Kit | — | Google Play Services, closed | Excluded |
| OCR VLMs (PaddleOCR-VL etc.) | ~1B | Seconds per screen on phone CPU; RAM-competes with translator | v2 "hard image" mode only |

**Decision:** PP-OCRv6 small via ONNX Runtime Mobile (XNNPACK EP; try NNAPI/QNN later). German umlauts and ß are in the recognizer dictionary (verified, D-005).

Pipeline (implemented in Kotlin, no OpenCV — D-011):
1. DB detector (BGR, ImageNet mean/std, longest side ≤ 1280 by default, multiples of 32) → probability map → threshold 0.2 → 8-connected components → min-area rotated rect → score ≥ 0.45 → unclip ratio 1.4 (values from the model's `inference.yml`).
2. Perspective-crop each box straight to rec height 48 (`Matrix.setPolyToPoly`), pad batch width (min 320).
3. Recognizer → greedy CTC decode with the dictionary from `inference.yml` (+ trailing space class).
4. Skip angle classifier (screens are upright).
5. Batch recognition crops (8 per batch, sorted by aspect ratio); XNNPACK with 4 threads.

---

## 5. Layout: lines → paragraphs

OCR output = lines. `:layout` groups them (implemented, unit-tested):
- Sort lines top→bottom, left→right.
- Merge line *b* into block of line *a* when:
  - vertical gap ≤ 0.8 × median line height,
  - line heights within ±25 %,
  - horizontal overlap ≥ 50 % of narrower line OR same left edge ±1 line height,
  - *a* doesn't end with sentence-final punctuation followed by a gap > 0.5 × median height.
- Columns stay apart because overlapping/side-by-side lines can't join (no x-projection pass yet).
- Join text: line ends with `-` after a letter and next starts lowercase → remove hyphen, no space; else join with space.
- TODO: UI-element guard (very short isolated lines stay single blocks) — currently implicit only.
- Optional v2: small layout model (PP-DocLayout) for complex pages.

Translation prompt should tell the model the text came from OCR and may contain line-break artifacts (not done yet).

---

## 6. Translation engine

**Default (D-036): OPUS-MT de→en** (Helsinki-NLP Marian, Apache-2.0), int8 ONNX export by Xenova, ~108 MB, on ONNX Runtime (already used for OCR). `MarianTranslator`: own SentencePiece unigram tokenizer (matches the real library in tests), sentence split, greedy decoding with KV cache. Loads in about a second.

**Quality option: Hy-MT2 1.8B Q4_K_M** (Tencent, Apache-2.0, ungated, 1.13 GB). German is one of its languages. Loads in 3–16 s, ~2.5 GB RAM. The 1.25-bit build (462 MB) needs an unmerged llama.cpp kernel (D-004, PR #22836); when it merges it may replace Q4_K_M as the quality option, but OPUS-MT stays the default (D-037). The user picks the engine on the Veil tab.

- Runtime: llama.cpp via own JNI bridge (`translate/src/main/cpp/veil_llama.cpp`), CPU only. Built with `GGML_BACKEND_DL` + `GGML_CPU_ALL_VARIANTS` so the best ARM variant (dotprod/i8mm/SVE) is picked at runtime; KleidiAI and OpenMP on (D-010). Vulkan optional/experimental later.
- Prompt: Hy-MT2 model card "Default Translation" English template, chat template from the GGUF.
- Sampling per model card: temp 0.7, top_p 0.6, top_k 20, repetition penalty 1.05 (D-012).
- Load model on first trigger, keep warm while app process alive; unload on `onTrimMemory`.
- Batch all blocks of a screen into one prompt with numbered segments → parse back (fallback per-block on parse failure). *Not done yet.*
- Stream: show each block as soon as translated.
- Cache: LRU keyed by (normalized source text, model id) — persists to disk.
- Glossary: user terms (DE→EN) injected into prompt (model card has a "Terminology" template).

Alternatives behind the `Translator` interface (later):
- Hy-MT2 7B (for 12–16 GB RAM phones).
- Bergamot/Firefox models — tiny + fast "quick" mode, lower quality.
- OpenAI-compatible endpoint (Ollama on homelab) — optional, off by default, explicitly "not offline".

Model download: Hugging Face URL (+ custom mirror + import from file, TODO); resumable (Range), SHA-256 check, Wi-Fi-only warning (TODO). Side-loading via adb into the app's external files dir works now.

---

## 7. Overlay rendering

- Window type `TYPE_ACCESSIBILITY_OVERLAY` (from the a11y service — no SYSTEM_ALERT_WINDOW needed).
- Per block: filled rect over source bounds; background = sampled dominant color around box edge; text color = max contrast.
- Auto-fit: binary-search text size so English fits box (allow growing box downward up to 30 % if needed).
- Tap block → toggle original/translation; long-press → copy; tap outside / back / trigger again → dismiss all.
- Loading: subtle shimmer on blocks while translating.
- Overlay auto-dismisses when the underlying window changes (a11y `TYPE_WINDOW_STATE_CHANGED` / scroll events).

---

## 8. Settings (Compose)
- Trigger: set as assistant (role button + status), deeplink info, QS tile.
- Source: Auto / Accessibility only / OCR only; capture backend.
- OCR model: tiny / small / medium.
- Translator: model picker + download manager; glossary editor.
- Languages: source (default DE, auto), target (default EN).
- Appearance: font, size min/max, opacity.
- Privacy page: "nothing leaves device". Network is used only for model downloads and the daily update check (toggle to disable, D-020).

---

## 9. Milestones

0. **Spikes (de-risk first):** code done, device results pending — see STATUS.md.
   - a) VoiceInteractionService selectable as assistant on Pixel 9; corner swipe logs a trigger.
   - b) PP-OCRv6 small ONNX on device: German screenshot → text with umlauts; measure ms.
   - c) Hy-MT2 1.8B GGUF via llama.cpp JNI: translate 10 German paragraphs; measure tokens/s, RAM.
0.5. **Release pipeline + in-app updates** — before the remaining spike measurements
   (D-018–D-021, D-026–D-029). Steps 1–4 and 6–8 done 2026-10-18; 5 and 9 need the phone. Steps:
   1. **Key (user, on Linux):** see STATUS.md → "Handoff to Linux" for the exact commands.
   2. **Gradle:** `release` signingConfig read from env (`VEIL_KEYSTORE_PATH`,
      `VEIL_KEYSTORE_PASSWORD`, alias `veil`); without them the release stays unsigned (local
      builds). `versionName`/`versionCode` from Gradle properties `-PveilVersion=0.x.y`
      (versionCode = minor × 1000 + patch; default 0.0.1 / 1 locally). Debug and profile get
      `applicationIdSuffix = ".dev"` and a "Veil dev" label.
   3. **Scripts:** port befikr's `.github/scripts/next-version.sh` and `release-notes.sh` (adjust the
      footer: "Arm64, Android 11+"). Use `fetch-depth: 0`.
   4. **Workflow:** (now one `ci.yml` with `build` → `release` jobs, D-032) a release job on
      push to `main`: compute version → if `none`, stop → decode `VEIL_KEYSTORE_B64` to a temp file →
      `./gradlew assembleRelease -PveilVersion=…` → copy to `veil.apk` → `apksigner verify` →
      `softprops/action-gh-release` with tag `v0.x.y`, notes, `make_latest: true`, file `veil.apk`.
      `permissions: contents: write`, `concurrency` group so releases tag in order. Fail loudly if
      the secrets are missing.
   5. **Verify R8:** the release build is minified and has never run on a device — check the ORT and
      JNI keep rules by running the release APK (OCR + translate tabs).
   6. **App (port befikr `Updater.kt` + `UpdateReceiver`, `useUpdate.ts` logic in Kotlin):**
      `REQUEST_INSTALL_PACKAGES`; check `https://api.github.com/repos/oowais/veil/releases/latest`
      at most once a day (toggle, default on, D-020) and on "Check for updates"; compare 0.x.y with
      `BuildConfig.VERSION_NAME`; asset `veil.apk`; download to cache over https with manual
      redirects and progress; `PackageInstaller` session; receiver opens the system confirm screen
      on `STATUS_PENDING_USER_ACTION`; if `canRequestPackageInstalls()` is false, open
      `ACTION_MANAGE_UNKNOWN_APP_SOURCES`. Daily check on app start (D-029). "Updates" tab in the
      spike UI for now (`app/…/update/`).
   7. **Privacy text:** README Privacy section + in-app note that the daily check contacts GitHub.
   8. **Commit messages:** from now on `feat:`/`fix:` subjects are user-facing release notes — write
      them as short sentences a user understands. Add this rule to CLAUDE.md with step 3.
   9. **Cut-over (user):** uninstall the debug-signed Veil once, install from
      `https://github.com/oowais/veil/releases/latest/download/veil.apk`, re-download models.
1. Trigger + a11y text source + translator + basic overlay (no OCR yet). ← usable for most apps. *Code done 2026-10-18, phone test pending.*
2. Screenshot capture + OCR + layout grouping.
3. Overlay polish (color sampling, auto-fit, toggles), caching, model manager.
4. Settings, onboarding, deeplink, QS tile.
5. Hardening: rotation, multi-window, keyguard (don't trigger), FLAG_SECURE (black → message), low-RAM.
6. Release: `MODELS.md`, README. (F-Droid metadata parked — D-021.)

## 10. Testing
- Unit: layout grouping fixtures, CTC decode, geometry, dictionary parsing (done); prompt parse (later).
- Instrumented: OCR golden images (German UI, article, signage photo, dark mode).
- Manual matrix: corner swipe L/R, long-press power, deeplink via `adb shell am start -d veil://translate`, rotate, split screen, WebView, game/canvas app.
- Perf targets (Pixel 9 / Snapdragon 8-class): a11y path < 1.5 s to first translated block; OCR path < 3 s.
- Device testing uses the debug APK uploaded by CI (D-014), not adb.

## 11. Repo setup
- `README.md`, `LICENSE` (Apache-2.0), `MODELS.md`, `CONTRIBUTING.md`, `CLAUDE.md`, `docs/` (done).
- GitHub Actions: `./gradlew lint testDebugUnitTest assembleDebug assembleRelease`, uploads debug APK (D-007, D-014).
- No prior-art project names in code, strings, or docs.

## Research sources
- PP-OCRv6: https://huggingface.co/blog/PaddlePaddle/pp-ocrv6 · https://www.paddleocr.ai/main/en/version3.x/algorithm/PP-OCRv6/PP-OCRv6.html
- PaddleOCR → ONNX on Android in production: https://ente.com/blog/ocr/
- OCR VLM leaderboard (OmniDocBench): https://www.docsumo.com/blog/best-ocr-models
- PaddleOCR-VL GGUF: https://huggingface.co/PaddlePaddle/PaddleOCR-VL-1.5-GGUF
- Hy-MT2 paper: https://arxiv.org/html/2605.22064v1 · model card: https://huggingface.co/tencent/Hy-MT2-1.8B-GGUF
- llama.cpp Android reference build: `third_party/llama.cpp/examples/llama.android`
- Assistant-role trick reference: Key Mapper PR #1351 (keymapperorg/KeyMapper)
