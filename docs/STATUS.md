# Status

Last updated: 2026-10-18. Update this file in the same commit as any change that moves work forward.

## Where things stand

- **Milestone 0 (spikes):** all three pass on the Pixel 9 with release v0.1.0 (results below).
  Trigger: fires over any app, invisible. OCR: 2.0 s warm, accurate. Translate: 15 tok/s, good
  quality. Remaining measurements: translate at 2/6/8 threads.
- **Milestone 0.5** (release pipeline + in-app updates, D-027): done and released 2026-10-18 —
  [v0.1.0](https://github.com/oowais/veil/releases/tag/v0.1.0) (run 39141696534, `veil.apk`
  40.3 MB, versionCode 1000). Cut-over done; the R8-minified build runs OCR and Translate fine.
  Left: one real in-app update (needs the next release).
- **Release certificate** (Veil's identity, must never change, D-026): `CN=Veil`,
  SHA-256 `14fed88f6e503f269bff85f375a8c55fa18743600a0b7adc0f71e6cd0f30f4e2`
  (SHA-1 `475d631ffed94a2e6cd365e1876bd527de321b0f`), APK Signature Scheme v2. Checked locally on
  the downloaded `veil.apk` with `apksigner verify --print-certs`.
- Builds locally (Windows, and Linux since 2026-10-18) and in GitHub Actions: lint, 16 unit tests, debug/profile/release APKs.
  Locally checked: a release signed with a throwaway key gets versionCode 3007 for `-PveilVersion=0.3.7`
  and passes `apksigner verify`; debug/profile are `com.owaisahmed.veil.dev`, label "Veil dev".
- CI uploads `veil-apks-<sha>`: `app-debug.apk` (crash traces) and `app-profile.apk` (optimised,
  for timings), both signed with the public debug key (D-014, D-025).
- `release.yml` (on push to `main`): next version from commits → signed `veil.apk` → GitHub release
  `v0.x.y`. Local builds are `0.0.1` / `1`; CI passes `-PveilVersion`.

## Handoff to Linux (start here)

1. **Before leaving Windows:** the user pushes `main` (it had unpushed commits at handoff).
   On Linux: `git clone --recurse-submodules https://github.com/oowais/veil` (or `git pull` +
   `git submodule update --init`).
2. ~~**Toolchain on Linux**~~ — done 2026-10-18, paths in "Environment notes" below. Local
   `lint testDebugUnitTest assembleDebug` passes (14/14 tests).
3. ~~**Release key (user, D-026)**~~ — done 2026-10-18. `~/.keys/veil-release.jks` (PKCS12, alias
   `veil`, RSA 4096, CN=Veil), random password in the user's password manager, `.jks` backed up.
   Secrets `VEIL_KEYSTORE_B64` and `VEIL_KEYSTORE_PASSWORD` set on `oowais/veil`. Claude never
   handles the password.
4. ~~**Claude implements milestone 0.5**~~ — done 2026-10-18, released as v0.1.0 (certificate in
   "Where things stand"). First push (run 39141588982) failed before building: `release-notes.sh`
   exited 1 when no `v0.*` tag exists (befikr always has one); fixed in `8c7220d`. Follow releases
   with `gh run list --workflow release.yml`.
5. **Cut-over (user):** after the first release is published, uninstall the current Veil once,
   install `https://github.com/oowais/veil/releases/latest/download/veil.apk`, set it as the
   assistant again, re-download models. From then on, updates come through the app (Updates tab).
   Also run OCR and Translate in that release APK: it is the first R8-minified build to run (keep
   rules for ORT and JNI). If it crashes, R8 is the suspect. No adb (D-030): the user reports what
   the screen shows; if needed, add an in-app crash/log view.
   Progress 2026-10-18: v0.1.0 installed from the release link, set as assistant, Trigger tab works.
   OCR and Translate also work in the release build: R8 keep rules for ORT and JNI are fine.
6. **Then resume the spikes** with the release app (it includes the OCR fixes and the
   assistant-settings fix):
   - **OCR:** done (results below); XNNPACK stays off (D-031).
   - **Trigger:** done — works over other apps, invisible, < 10 ms overhead (results below).
     Capture delay is decided in milestone 2.
   - **Translate:** done at 4 threads (results below); still to try 2/6/8 threads.
7. Decide from the results: OCR detector size, thread counts, greedy vs sampled decoding, whether
   1.8B quality is enough. Then milestone 1.

## Spike results

Device: Pixel 9 (`google/tokay/tokay:17/CP3A.261005.005`), CI debug APK from `dbf0b2d`.

**OCR (spike b) — release v0.1.0, 2026-10-18.** Same Bonn screenshot 1080×2424, det input 576×1280.
- XNNPACK off: cold det 315 + rec 1631 = 1946 ms; warm det 334 (pre 12 · infer 315 · post 7) +
  rec 1665 (pre 12 · infer 1593 · post 60) = **1999 ms**.
- XNNPACK on (detector): cold 568 + 1458 = 2026 ms; warm det 605 (infer 584) + rec 1746
  (infer 1674) = 2351 ms. XNNPACK makes the detector ~1.9× slower → D-031.
- 21 boxes → 18 lines (3 non-Latin dropped, D-024) → 12 blocks. Same accuracy as before (scores
  0.98–1.00, umlauts/ß right, `Veranstaltungs-`+`kalender` joined). CJK junk gone; status-bar
  `16:50` and `800` remain (exclude status bar in milestone 2).
- Under the 3 s target. The recognizer is 80 % of the time (1.6 s for 18 lines, ~90 ms/line) →
  next levers are recognizer-side: batching/width buckets, threads, or only recognising lines in
  the region of interest. Detector size 960 would save at most ~100 ms.

**OCR (spike b), debug APK, 2026-10-18.**
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

**Trigger (spike a) — release v0.1.0, 2026-10-18.** Veil is listed under Digital assistant app and
can be set. The corner swipe fires: events `Shown` → `Hidden` → `Fired(source=ASSIST_GESTURE)`.
Second run, 7 swipes from the Trigger tab, capture delay 200 ms:
- Nothing visible on screen (no scrim, no flash): the session has UI disabled and hides itself.
  This is the intended behaviour; Veil's own overlay comes in milestone 1.
- `showFlags=263` (0x107) every time: SHOW_WITH_ASSIST | SHOW_WITH_SCREENSHOT |
  SHOW_SOURCE_ASSIST_GESTURE, plus 0x100.
- `Hidden` 1–8 ms after `Shown` (mostly 3 ms); `Fired` exactly the capture delay (200–201 ms) after
  `Hidden`. So Veil's own trigger overhead is under 10 ms + the capture delay. Swipe → `Shown`
  can't be measured in-app.
- Fires over other apps: 6 of the 7 swipes were made inside another app.
- Not tested yet: capture delay 0 ms. Which delay is enough only shows
  once there is a screenshot to check for scrim (milestone 2); keep 200 ms until then.

**Translate (spike c) — release v0.1.0, 2026-10-18.** Same model, 4 threads.
- Load 5931 ms, PSS 515 → 2895 MB after load, 2791 MB after the run.
- 10 samples: 21.9 s wall (debug: 24.5 s). Prompt 67 tok/s, generation **15.3 tok/s** (debug 13.0).
  Per sample prompt+generate: #1 604+54 ms … #3 1118+1458 ms … #10 823+1161 ms. Prompt time
  still 0.6–1.3 s per call → KV-cache prefix reuse remains the biggest win.
- Quality as before. #10 `Unter- lagen` now → "documents" (correct this time; sampling varies).
  #8 "disable the optimization of the app" is slightly off (means "exclude the app from
  optimization").
- JNI under R8 works.

**Translate (spike c), 2026-10-18.** Debug APK from `dbf0b2d` (llama.cpp is compiled Release in
every build type, so these timings are representative). Hy-MT2 1.8B Q4_K_M, 4 threads.
- Backend: CPU with NEON, FP16, MATMUL_INT8 (i8mm), SVE (SVE_CNT 16), DOTPROD, OpenMP, KleidiAI,
  REPACK all on.
- Load 5006 ms. PSS 187 → 2576 MB right after load (mmapped weights), 1558 MB after the run.
- 10 samples: 24.5 s wall. Prompt 63 tok/s, generation 13.0 tok/s avg (most 14–19, #6 7.6, #8 9.3).
  Per sample prompt+generate: #1 480+52 ms … #6 1698+3033 ms. Even a one-word input costs ~480 ms of
  prompt processing → mostly the fixed instruction + chat template, re-processed every call.
- Quality: very good; 9/10 natural and correct, register preserved (formal letter, casual chat).
  Miss: #10 "Unter- lagen" (artificial OCR hyphen) → "attachments" instead of "documents"; the
  layout join fixes this in the real path, and the prompt can mention OCR artefacts.
- Conclusions: 1.8B Q4_K_M quality is good enough as the default. Speed needs work for the
  "< 1.5 s to first block" target:
  1. Keep the instruction prefix in the KV cache and only process the new text (biggest win).
  2. Batch all blocks of a screen into one numbered prompt (already planned, milestone 3).
  3. Try 2/6/8 threads and greedy decoding (not measured yet).
  4. 1.25-bit build (~1.5× faster) once llama.cpp PR #22836 merges.


## TODO (known gaps, by milestone)

Milestone 0.5 (step list in PLAN.md §9)
- Test an update end to end: the copy-button commit releases v0.2.0 on the next push; update to it
  from the Updates tab in v0.1.0.

Milestone 1
- Translator: reuse the instruction prefix in the KV cache (spike c: ~0.5–1.7 s prompt time per call).
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
- (Settled by D-029: daily check on app start, no WorkManager.)

Other:

- Versioning is settled by D-019 (CI computes 0.x.y; first release 0.1.0).
- Glossary storage format and UI.
- When llama.cpp PR #22836 merges: switch default to the 1.25-bit model?

## Environment notes

Linux (user's machine, CachyOS, fish shell), set up 2026-10-18:

- JDK: `jdk21-openjdk` at `/usr/lib/jvm/java-21-openjdk`. System default `java` is 17 (runtime
  only) and `java-27-openjdk` is also installed, so always build with
  `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew …`.
- Android SDK: `~/Android/Sdk` (`/home/owais/Android/Sdk`) with platforms/android-37.0,
  build-tools 36.0.0, NDK 29.0.14206865, CMake 3.31.6, platform-tools 37.0.1.
  `local.properties`: `sdk.dir=/home/owais/Android/Sdk`.
- Command-line tools: AUR `android-sdk-cmdline-tools-latest` in `/opt/android-sdk/cmdline-tools/latest`
  (root-owned, so packages go to `~/Android/Sdk` via `--sdk`). `sdkmanager` is deprecated there;
  use the new Android CLI with slash package names, no license step:
  `JAVA_HOME=/usr/lib/jvm/java-21-openjdk /opt/android-sdk/cmdline-tools/latest/bin/android --no-metrics --sdk=$HOME/Android/Sdk sdk install ndk/29.0.14206865`.
- adb: `/usr/bin/adb` (`android-tools`). `gh` logged in as `oowais`.
- Release key: `~/.keys/veil-release.jks` (folder mode 700); repo secrets set (handoff step 3).

Windows dev box (first session):

- JDK: `C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot` (set `JAVA_HOME`; not on PATH).
- Android SDK: `%LOCALAPPDATA%\Android\Sdk` with platforms 36/37.0, build-tools 36.0.0,
  NDK 29.0.14206865, CMake 3.31.6. adb: `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`.
- The bundled `sdkmanager` now uses `/` in package names (`ndk/29.0.14206865`).
- `gh` CLI is logged in as `oowais`; use `gh run list` / `gh run watch` to follow CI.
- Fresh clone: `git submodule update --init` (shallow) before building.
