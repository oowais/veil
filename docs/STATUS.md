# Status

Last updated: 2026-10-18. Update this file in the same commit as any change that moves work forward.

## Where things stand

- **Milestone 0 (spikes):** all three pass on the Pixel 9 with release v0.1.0 (results below).
  Trigger: fires over any app, invisible. OCR: 2.0 s warm, accurate. Translate: 15 tok/s, good
  quality. Thread counts measured (D-033).
- **Milestone 1** (gesture → accessibility text → translate → overlay): released in v0.3.0 and
  **works on the Pixel 9** (first run below). Fixes from that run are in the next release.
- **Milestone 0.5** (release pipeline + in-app updates, D-027): done and released 2026-10-18 —
  [v0.1.0](https://github.com/oowais/veil/releases/tag/v0.1.0) (run 39141696534, `veil.apk`
  40.3 MB, versionCode 1000). Cut-over done; the R8-minified build runs OCR and Translate fine.
  In-app update tested end to end: v0.1.0 → v0.2.0 from the Updates tab. **Milestone 0.5 complete.**
- **Release certificate** (Veil's identity, must never change, D-026): `CN=Veil`,
  SHA-256 `14fed88f6e503f269bff85f375a8c55fa18743600a0b7adc0f71e6cd0f30f4e2`
  (SHA-1 `475d631ffed94a2e6cd365e1876bd527de321b0f`), APK Signature Scheme v2. Checked locally on
  the downloaded `veil.apk` with `apksigner verify --print-certs`.
- Builds locally (Windows, and Linux since 2026-10-18) and in GitHub Actions: lint, 16 unit tests, debug/profile/release APKs.
  Locally checked: a release signed with a throwaway key gets versionCode 3007 for `-PveilVersion=0.3.7`
  and passes `apksigner verify`; debug/profile are `com.owaisahmed.veil.dev`, label "Veil dev".
- One workflow, `ci.yml` (D-032): job `build` runs lint, unit tests and the release APK once; on a
  push to `main` with a user-facing commit it signs `veil.apk`, and job `release` (only after
  `build` passed) publishes GitHub release `v0.x.y`. No dev APK artifacts anymore. Local builds
  are `0.0.1` / `1`; CI passes `-PveilVersion`.

## Milestone 1 phone test (next)

After updating from the Updates tab:
1. Veil tab: all three steps ✓ (assistant, accessibility service, model). The accessibility switch
   may be blocked as "Restricted setting" → App info → ⋮ → Allow restricted settings.
2. Open a German page or app (e.g. bonn.de in Chrome, German system settings), swipe the gesture.
   Expect "Loading translation model…" (first time, ~3–6 s), then "Translating n/N…" while
   white/dark boxes with English appear top to bottom. Swipe again → overlay gone.
3. Scroll or switch apps → overlay disappears by itself.
4. Veil tab → Copy log → paste in chat. Look at: German count, per-block prompt tokens (should be
   small after the first block: prefix reuse), gen tok/s at threads 2/4, time to first block.
Things that may need fixing: box positions off by the status bar height, overlay vanishing on its
own (scroll events from the app itself), English or numbers being translated, long pages being slow.

**First run, v0.3.0, 2026-10-18** — bonn.de in Chrome:
- Works end to end: boxes in the right places (no status-bar offset), English correct.
- Log: 9 texts, all German, capture 81 ms. Model load **16.2 s** (cold, first after the update;
  spike loads took 3–6 s with a warm page cache). First block after 18.4 s, all 9 after 25.4 s.
- Prefix reuse works: after the first call (33 prompt tokens, 897 ms) prompts are 3–7 tokens,
  150–350 ms. Generation 7–16 tok/s at 2/4 threads (short outputs are dominated by per-call cost).
- Bugs found and fixed for the next release:
  - The WebView node's own text is the page title, with bounds = whole page → a white box covered
    the page. Now nodes containing other text nodes are dropped (containers).
  - Image alt text ("Logo of the federal city Bonn") and icon-button labels ("Select language",
    "Open the sidebar") were drawn over the images/icons. Now image nodes and clickable nodes
    ≤ 64 dp are skipped.
  - Taking a screenshot dismissed the overlay (System UI window event). System UI events are now
    ignored.
- Open: "visibl e" box (unknown hidden text); English text is auto-sized up to 20 sp, larger than
  the original (fit to original size in milestone 3); slow first trigger (model load) → decide on
  preloading.

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
   with `gh run list --workflow ci.yml` (`release.yml` was merged into it, D-032).
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
   - **Translate:** done at 2, 4, 6 and 8 threads (results below, D-033).
7. Decide from the results: ~~OCR detector size~~ (1280, ~315 ms; 960 saves ≤ 100 ms), ~~thread
   counts~~ (D-033), ~~whether 1.8B quality is enough~~ (yes, all runs), greedy vs sampled decoding
   (not measured; greedy also makes output repeatable — wording varied between runs). Then
   milestone 1.

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

**Translate threads — release v0.2.0, 2026-10-18.** Same 10 samples (output pasted via the copy button).

| Threads | Load | PSS after load | Prompt tok/s | Generate tok/s | Wall (10 samples) |
|---|---|---|---|---|---|
| 2 | 3361 ms | 2544 MB | 49 | **16.7** | 23.6 s |
| 4 | 5931 ms | 2895 MB | **67** | 15.3 | **21.9 s** |
| 6 | 3747 ms | 2542 MB | 54 | 10.9 | 29.1 s |
| 8 | 3363 ms | 2542 MB | 50 | 7.5 | 38.2 s |

- Generation is memory-bound: 2 threads generate slightly faster than 4. Prompt processing is
  compute-bound: 4 threads are 37 % faster. → Idea: separate counts, `n_threads` (generate) small
  and `n_threads_batch` (prompt) larger; the JNI `load` currently sets both to the same value.
  Matters less once the instruction prefix is cached (prompt shrinks to the new text only).
- 8 threads halve generation speed (7.5 tok/s, per sample 5–11) and don't help prompt
  processing. The Tensor G4 has 8 cores (1 Cortex-X4, 3 A720, 4 A520): with 8 threads the slow A520
  cores hold up every step. 6 threads (10.9 tok/s) confirm it: every thread past 4 lands on an
  A520. Never use more threads than big+mid cores (4) → D-033.
- Load time and PSS vary run to run (page cache), not clearly a thread effect.
- Quality unchanged; #10 → "documents" again, #8 still "disable the optimization".
- First 6-thread paste was the 2-thread results again (Reload kept the old list, samples not re-run);
  fixed: Reload now clears the results; the real 6-thread run is in the table.

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

Milestone 0.5: done.

Milestone 1 (code done 2026-10-18, D-034, D-035, D-036)
- OPUS-MT engine (D-036): try on the phone — Veil tab → Fast, download (~108 MB), swipe; Translate
  tab → OPUS_MT → Load → Run 10 samples (load time, ms per sample, quality vs Hy-MT2).
- Model tests: `VEIL_OPUS_DIR` (folder with source.spm + vocab.json) and `VEIL_OPUS_MODEL` (folder
  laid out like the app's: encoder.onnx, decoder.onnx, source.spm, vocab.json) enable the
  tokenizer and translator tests that use the real model; without them they are skipped (CI).
- Phone test (section above); fix what it finds.
- Measure threads 2/4 (generate/prompt) vs 4/4 from the pipeline log (D-033).
- Translator: greedy decoding; compare speed and quality with the current sampler.
- Texts over 1500 chars are skipped (context 2048): split long texts into sentences instead.
- The Translate spike tab loads its own model copy (~2.5 GB more); share the pipeline's instance
  or remove the tab once milestone 1 works.

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
