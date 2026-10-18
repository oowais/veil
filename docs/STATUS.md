# Status

Last updated: 2026-10-18. Update this file in the same commit as any change that moves work forward.

## Where things stand

- **Milestone 0 (spikes): code complete, not yet run on the Pixel 9.**
- Builds locally (Windows) and in GitHub Actions: lint, 12 unit tests, debug + release APKs.
- CI uploads `veil-apks-<sha>` per run: `app-debug.apk` (crash traces) and `app-profile.apk`
  (optimised, for timings), both debug-signed (D-014, D-025).
- App version: `versionName 0.0.1`, `versionCode 1` (not yet driven by commits — see open questions).

## Next up

1. Install the CI debug APK on the Pixel 9 and run the three spikes. Record results below.
   - **Trigger:** does Veil appear as a selectable assistant? Does the corner swipe log
     `Shown → Hidden → Fired`? What capture delay hides the scrim reliably?
   - **OCR:** download the model in-app, pick German screenshots (UI, article, dark mode).
     Record cold/warm ms at 960/1280/1600, XNNPACK on vs off, umlaut correctness.
   - **Translate:** download Hy-MT2 Q4_K_M (1.1 GB), load with 4 threads (try 2/6/8).
     Record load time, PSS, prompt and generation tok/s, translation quality on the 10 samples.
2. Decide from the results: OCR detector size default, thread counts, whether sampling should
   be greedy, whether 1.8B quality is good enough.
3. Milestone 0.5: in-app updates (see PLAN.md §9, D-019–D-021).
4. Start milestone 1 (trigger → accessibility text → translator → basic overlay).

## Spike results

Device: Pixel 9 (`google/tokay/tokay:17/CP3A.261005.005`), CI debug APK from `dbf0b2d`.

**OCR (spike b), 2026-10-18.**
- Run 1, defaults (det 1280, XNNPACK on): native SIGSEGV in `libonnxruntime.so` under
  `OrtSession.run` from `OcrEngine.recognizeCrops` (recognizer). → D-023.
- Run 2, XNNPACK off, debug APK, German Bonn city website screenshot 1080×2424 (det input 576×1280):
  cold det 699 + rec 3336 ms; warm det 706 + rec 3562 = 4268 ms. 21 boxes → 20 lines → 14 blocks.
  Accuracy very good: umlauts correct, scores 0.97–1.00, `Veranstaltungs-`+`kalender` joined,
  6-line paragraph grouped into one block. Junk: status-bar icons read as CJK (`『三刀` 0.58,
  `月` 0.97) and `800` → D-024.
- Too slow vs the 3 s target, recognizer dominates. Debug build, so Kotlin parts are inflated (D-025).
- Next: profile APK with per-stage timings; compare detector XNNPACK on/off; if recognizer
  inference itself is slow → try batch size, width bucketing, fewer threads on big cores, PP-OCRv6
  tiny rec, or skipping recognition of boxes outside the region of interest.

**Trigger (spike a) — button bug, 2026-10-18.** "Set as assistant" did nothing: the assistant role
is not requestable (D-022). Fixed to open system settings (next CI APK). Workaround: Settings → Apps →
Default apps → Digital assistant app → Veil. Whether Veil is listed and the swipe fires: not reported yet.

Translate (spike c): not reported yet.

## TODO (known gaps, by milestone)

Milestone 0.5
- Port befikr's `next-version.sh`, `release-notes.sh`, release job and `Updater.kt` (D-019).
- Create and commit the sideload keystore; versionName/versionCode from CI.
- Daily check toggle + privacy text (D-020); update README's Privacy section when it ships.

Milestone 1
- Create `:capture` with `TextSource`, `AccessibilityTextSource`, `VeilAccessibilityService`.
- Create `:overlay` (`TYPE_ACCESSIBILITY_OVERLAY`, basic block rendering, dismiss on trigger).
- Wire `TriggerController.onTrigger` to capture → translate → overlay.
- German detection for accessibility nodes (heuristic vs tiny langid: undecided).
- Load translator on first trigger, keep warm, unload on `onTrimMemory`.

Milestone 2
- `ScreenshotSource` via `AccessibilityService.takeScreenshot()`; hide own overlay before capture.
- Feed OCR lines through `BlockBuilder`.
- UI-element guard in `BlockBuilder` (short isolated lines stay single blocks).
- Tell the model the text came from OCR in the prompt.

Milestone 3
- Batched numbered-segment prompt + parser with per-block fallback.
- LRU translation cache persisted to disk.
- Overlay color sampling, auto-fit text, tap/long-press toggles, shimmer.
- Model manager: import from file, custom mirror, Wi-Fi-only warning; downloads outside the
  composable scope (currently cancelled when leaving the tab).
- `rec.yml` has no published hash — consider pinning our own SHA-256.

Milestone 4–6
- Settings UI, onboarding, deeplink `veil://translate`, QS tile.
- Hardening (rotation, multi-window, keyguard, FLAG_SECURE, low RAM).
- Verify the R8-minified release build works at runtime (only checked that it assembles).
- (Release signing moved to milestone 0.5 — committed sideload key, D-019. F-Droid parked, D-021.)

## Open questions

In-app updates: none open (settled by D-019, D-020, D-021).

Other:

- Versioning is settled by D-019 (CI computes 0.x.y; first release 0.1.0).
- Glossary storage format and UI.
- When llama.cpp PR #22836 merges: switch default to the 1.25-bit model?

## Environment notes (Windows dev box)

- JDK: `C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot` (set `JAVA_HOME`; not on PATH).
- Android SDK: `%LOCALAPPDATA%\Android\Sdk` with platforms 36/37.0, build-tools 36.0.0,
  NDK 29.0.14206865, CMake 3.31.6. adb: `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`.
- The bundled `sdkmanager` now uses `/` in package names (`ndk/29.0.14206865`).
- `gh` CLI is logged in as `oowais`; use `gh run list` / `gh run watch` to follow CI.
- Fresh clone: `git submodule update --init` (shallow) before building.
