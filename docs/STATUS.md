# Status

Last updated: 2026-10-18. Update this file in the same commit as any change that moves work forward.

## Where things stand

- **Milestone 0 (spikes):** code complete; partly run on the Pixel 9 (results below). OCR works
  with fixes from `95f7939`, which are not on the phone yet. Trigger and translate not reported yet.
- **Next is milestone 0.5** (release pipeline + in-app updates), ahead of the remaining spike
  measurements (D-027). Work continues on the user's **Linux** machine.
- Builds locally (Windows) and in GitHub Actions: lint, 14 unit tests, debug/profile/release APKs.
- CI uploads `veil-apks-<sha>`: `app-debug.apk` (crash traces) and `app-profile.apk` (optimised,
  for timings), both signed with the public debug key (D-014, D-025).
- App version is still hard-coded `0.0.1` / `1`; milestone 0.5 makes CI set it (D-019).

## Handoff to Linux (start here)

1. **Before leaving Windows:** the user pushes `main` (it had unpushed commits at handoff).
   On Linux: `git clone --recurse-submodules https://github.com/oowais/veil` (or `git pull` +
   `git submodule update --init`).
2. **Toolchain on Linux** (only needed for local builds; CI builds everything): JDK 17+ (21 used
   so far), Android SDK platform `android-37.0`, build-tools 36.0.0, NDK `29.0.14206865`,
   CMake `3.31.6`; `local.properties` with `sdk.dir=…` (gitignored). Record the actual paths in
   "Environment notes" below.
3. **Release key (user does this, D-026)** — next to the user's other keys; path is the user's
   choice, `~/.keys/` used here as a placeholder:
   ```bash
   keytool -genkeypair -v -keystore ~/.keys/veil-release.jks -storetype PKCS12 -alias veil -keyalg RSA -keysize 4096 -validity 36500 -dname "CN=Veil"
   base64 -w0 ~/.keys/veil-release.jks | gh secret set VEIL_KEYSTORE_B64 --repo oowais/veil
   gh secret set VEIL_KEYSTORE_PASSWORD --repo oowais/veil   # prompts; same password as above
   ```
   Back up the `.jks` and the password (password manager). Claude never handles the password.
   Check with `gh secret list --repo oowais/veil`.
4. **Then Claude implements milestone 0.5** — the step list is in PLAN.md §9 (0.5). Commit to local
   `main`, never push (CLAUDE.md).
5. **Cut-over (user):** after the first release is published, uninstall the current Veil once,
   install `https://github.com/oowais/veil/releases/latest/download/veil.apk`, set it as the
   assistant again, re-download models. From then on, updates come through the app.
6. **Then resume the spikes** with the release app (it includes the OCR fixes and the
   assistant-settings fix):
   - **OCR:** same Bonn screenshot, detector XNNPACK off vs on, record the per-stage timing lines.
   - **Trigger:** is Veil listed under Digital assistant app? Does the corner swipe log
     `Shown → Hidden → Fired`? Any visible scrim? Which capture delay works?
   - **Translate:** download Hy-MT2 Q4_K_M (1.1 GB, stay on the tab), load with 4 threads (try
     2/6/8). Record load time, PSS, prompt and generation tok/s, quality of the 10 samples.
7. Decide from the results: OCR detector size, thread counts, greedy vs sampled decoding, whether
   1.8B quality is enough. Then milestone 1.

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

Milestone 0.5 (step list in PLAN.md §9)
- Release key + GitHub secrets (user, see handoff step 3).
- Gradle signing from env, version from `-PveilVersion`, `.dev` suffix for debug/profile (D-028).
- Port `next-version.sh`, `release-notes.sh`, release job (D-019), signed with the secret key (D-026).
- Run the R8-minified release APK on the phone (never tested at runtime).
- Port `Updater.kt` + `UpdateReceiver`; daily check toggle + privacy text (D-020); README Privacy.
- Add the "feat/fix subjects are user-facing" rule to CLAUDE.md.

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
- (Release signing moved to milestone 0.5 — secret key, D-026. F-Droid parked, D-021.)

## Open questions

In-app updates (D-019–D-021, D-026–D-028):
- Daily check: WorkManager (extra dependency, runs without opening the app) or check on app start?
- Where exactly the user keeps `veil-release.jks` on Linux — record the path here once chosen.

Other:

- Versioning is settled by D-019 (CI computes 0.x.y; first release 0.1.0).
- Glossary storage format and UI.
- When llama.cpp PR #22836 merges: switch default to the 1.25-bit model?

## Environment notes

Linux (user's machine): not set up for Veil yet — fill in JDK/SDK paths after handoff step 2.

Windows dev box (first session):

- JDK: `C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot` (set `JAVA_HOME`; not on PATH).
- Android SDK: `%LOCALAPPDATA%\Android\Sdk` with platforms 36/37.0, build-tools 36.0.0,
  NDK 29.0.14206865, CMake 3.31.6. adb: `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`.
- The bundled `sdkmanager` now uses `/` in package names (`ndk/29.0.14206865`).
- `gh` CLI is logged in as `oowais`; use `gh run list` / `gh run watch` to follow CI.
- Fresh clone: `git submodule update --init` (shallow) before building.
