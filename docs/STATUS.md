# Status

Last updated: 2026-10-18. Update this file in the same commit as any change that moves work forward.

## Where things stand

- **Milestone 0 (spikes): code complete, not yet run on the Pixel 9.**
- Builds locally (Windows) and in GitHub Actions: lint, 12 unit tests, debug + release APKs.
- CI uploads an installable debug APK per run (commit `dbf0b2d` adds this; first run with it
  pending at time of writing).
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
3. Start milestone 1 (trigger → accessibility text → translator → basic overlay).

## Spike results

_None yet._

## TODO (known gaps, by milestone)

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
- Release signing (real keystore as a GitHub secret), F-Droid metadata.

## Open questions

- How should Conventional Commits drive `versionName`/`versionCode`? Manual bumps, or a script/CI
  step that computes 0.x.y from commit history? Under the rule, `feat:` initial commit implies 0.1.0.
- Glossary storage format and UI.
- When llama.cpp PR #22836 merges: switch default to the 1.25-bit model?

## Environment notes (Windows dev box)

- JDK: `C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot` (set `JAVA_HOME`; not on PATH).
- Android SDK: `%LOCALAPPDATA%\Android\Sdk` with platforms 36/37.0, build-tools 36.0.0,
  NDK 29.0.14206865, CMake 3.31.6. adb: `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`.
- The bundled `sdkmanager` now uses `/` in package names (`ndk/29.0.14206865`).
- `gh` CLI is logged in as `oowais`; use `gh run list` / `gh run watch` to follow CI.
- Fresh clone: `git submodule update --init` (shallow) before building.
