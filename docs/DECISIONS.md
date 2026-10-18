# Decision log

Append-only. Each entry: what was decided, why, and what it rules out. Supersede an entry by adding
a new one that references it; don't edit old ones except to mark them superseded.

## D-001 — Milestone 0 first: scaffold + spikes (2026-10-18)
First session delivers the multi-module scaffold and the three de-risking spikes as debug screens,
not a usable translator. Real features start at milestone 1.

## D-002 — minSdk 30 instead of 29 (2026-10-18)
`AccessibilityService.takeScreenshot()` needs API 30. With minSdk 30 there is one default capture
path and no MediaProjection fallback is required for basic OCR. Drops Android 10.

## D-003 — Test device: Pixel 9 (2026-10-18)
Tensor G4 (1× X4, 3× A720, 4× A520), 12 GB RAM, ARMv9 with dotprod/i8mm. Defaults are tuned for
it: 4 llama.cpp threads, Q4_K_M model.

## D-004 — Default translator build: Hy-MT2 1.8B Q4_K_M (2026-10-18)
1.13 GB, runs on mainline llama.cpp. The 1.25-bit build (462 MB) needs the STQ1_0 kernel from
llama.cpp PR #22836, which was still open on 2026-10-18. Revisit when it merges.

## D-005 — OCR model: PP-OCRv6 small ONNX from Hugging Face (2026-10-18)
`PaddlePaddle/PP-OCRv6_small_{det,rec}_onnx`, Apache-2.0. The recognizer dictionary
(18,708 entries, in `inference.yml`) contains ä ö ü Ä Ö Ü ß but not `„` or `ẞ`. The `inference.yml`
is downloaded alongside and parsed at runtime instead of bundling a dictionary.

## D-006 — Native deps: llama.cpp submodule + ONNX Runtime AAR (2026-10-18)
llama.cpp is a shallow git submodule at `third_party/llama.cpp`, pinned to release tag `v0.7.0`
(commit 620d2225), compiled by the NDK with our JNI bridge. ONNX Runtime comes from Maven Central
(`com.microsoft.onnxruntime:onnxruntime-android:1.31.0`). F-Droid generally accepts Maven Central
AARs of FOSS libraries. If F-Droid or APK size becomes a problem, switch ORT to a minimal source
build (only the ops the OCR models use, ~2–4 MB) without app code changes. Checked-in `.so` files
are ruled out.

## D-007 — GitHub Actions instead of GitLab CI (2026-10-18)
The repo is public on GitHub (`oowais/veil`). The plan's GitLab CI line is replaced by
`.github/workflows/ci.yml`.

## D-008 — Toolchain versions (2026-10-18)
AGP 9.4.1 (built-in Kotlin; `org.jetbrains.kotlin.android` is only on the classpath to pin KGP),
Kotlin 2.4.21, Gradle 9.8.1 (wrapper pinned by SHA-256), JDK 21 (Temurin), NDK 29.0.14206865,
CMake 3.31.6. compileSdk 37 because Compose BOM 2026.09.00 requires it; targetSdk stays 35 as planned.

## D-009 — Develop on Windows (2026-10-18)
User asked about switching to Linux; decided to stay on Windows since the toolchain was already
installed and building. Linux would mainly give faster native builds and match CI.
Local SDK: `%LOCALAPPDATA%\Android\Sdk`; `local.properties` (gitignored) points at it.

## D-010 — llama.cpp build flags (2026-10-18)
Mirrors upstream `examples/llama.android`: `GGML_BACKEND_DL=ON`, `GGML_CPU_ALL_VARIANTS=ON`,
KleidiAI and OpenMP on, `BUILD_SHARED_LIBS=ON`, `ANDROID_STL=c++_shared`. ggml loads its CPU
variant from `nativeLibraryDir`, so the app sets `jniLibs.useLegacyPackaging = true` (libs extracted).
The JNI bridge uses only `llama.h` (not llama.cpp's `common` library) to keep the surface small.

## D-011 — No OpenCV; DB post-processing in Kotlin (2026-10-18)
Connected components, convex hull, min-area rect and unclip are implemented in `:ocr`
(`Geometry.kt`, `DbPostProcess.kt`). Avoids a large native dependency.

## D-012 — Translation prompt and sampling from the Hy-MT2 model card (2026-10-18)
English "Default Translation" prompt; chat template read from the GGUF; temp 0.7, top_p 0.6,
top_k 20, repetition penalty 1.05. Revisit after the spike measures quality (greedy may be better
for UI strings).

## D-013 — Modules created on demand (2026-10-18)
`:capture` and `:overlay` from the plan are not created until milestone 1–2 needs them; empty
modules would be noise. DI is manual for now (no Koin/Hilt).

## D-014 — Device testing via CI debug APK (2026-10-18)
User prefers installing APKs from CI over connecting the phone with adb. CI uploads
`veil-debug-<sha>` (debug build, 30-day retention). Debug builds are signed with a committed
`app/debug.keystore` (passwords `android`) so successive CI APKs install over each other. This key
must never sign a release.

## D-015 — Git workflow and versioning (2026-10-18)
See `CLAUDE.md`: commit straight to local `main` after checks pass, never push (the user pushes),
Conventional Commits, semver 0.x.y (feat/breaking → minor, else patch).

## D-016 — Everything durable lives in the repo (2026-10-18)
Every decision, the plan and all TODOs are written to `docs/` and committed with the change, so
a fresh Claude Code session can continue from the repo alone.

## D-017 — Docs-only pushes skip CI (2026-10-18)
`ci.yml` ignores pushes and PRs that only change `docs/**`, `*.md` or `LICENSE`. Mixed pushes still
run CI. Consequence: a docs-only push produces no new debug APK.

## D-018 — In-app updates, after milestone 0 (2026-10-18)
User wants the app to update itself from within the app, to be built once the spikes are done
(milestone 0.5 in PLAN.md). Design is proposed, not final; open questions are in STATUS.md.

## D-019 — In-app updates follow befikr's pipeline (2026-10-18)
Refines D-018. User pointed at `oowais/befikr` (Tauri v2, but its Android updater is plain Kotlin).
Adopted from there:
- **Release on push, no manual tags.** CI runs `next-version.sh` on every push to `main`: next
  0.x.y from Conventional Commits since the last `v0.*` tag (first release 0.1.0). It prints `none`
  when nothing since the tag is user-facing (only `feat`, `fix`, `perf`, `refactor`, `revert`,
  breaking, `chore(deps)` count), and then no release is made. The release step creates tag
  `v0.x.y`; never tag by hand. Needs `fetch-depth: 0` and a `concurrency` group so queued builds
  version after the previous release tagged.
- **versionName/versionCode come from CI**, passed into Gradle (befikr gets this from Tauri; Veil
  must derive versionCode from 0.x.y itself, e.g. `minor * 1000 + patch`). Not edited by hand.
- **Signing:** release APK signed with a committed sideload keystore (password in the workflow, not
  a secret) — same as befikr. Never change it, or updates stop installing over the old app. This
  replaces "release keystore as GitHub secret" from D-018. The debug keystore (D-014) stays for
  debug artifacts only.
- **Release assets:** `veil.apk` on a GitHub Release, so `releases/latest/download/veil.apk` is a
  stable link. Release notes generated from `feat`/`fix`/`perf`/`refactor`/`chore(deps)` subjects
  (`release-notes.sh`); optionally a bundled "What's new" (`whats-new.mjs`).
- **Commit subjects become user-facing:** `feat:`/`fix:` subjects appear in release notes as
  written, so they must be short user-facing sentences.
- **App side (port of befikr `Updater.kt`):** query
  `https://api.github.com/repos/oowais/veil/releases/latest`, compare 0.x.y with the installed
  version, keep the last result; download the APK to cache over https only, following redirects
  manually; install via a `PackageInstaller` session; a `BroadcastReceiver` launches the system
  confirm screen on `STATUS_PENDING_USER_ACTION`. Requires `REQUEST_INSTALL_PACKAGES`; if
  `canRequestPackageInstalls()` is false, open `ACTION_MANAGE_UNKNOWN_APP_SOURCES` first.
- One reinstall when moving from the debug-signed CI artifacts to sideload-signed releases.
Still open (STATUS.md): automatic daily check vs manual only (privacy), F-Droid flavor timing.

## D-020 — Daily update check on by default, can be turned off (2026-10-18)
The app checks GitHub's latest release at most once a day, plus a manual "Check for updates".
This is a second network use besides model downloads: the settings/privacy page must say so
plainly, and a toggle turns the automatic check off (manual check still works).

## D-021 — F-Droid deferred (2026-10-18)
F-Droid is out of scope for now. No `github`/`fdroid` flavors; the updater ships in the only build.
Revisit if F-Droid distribution comes back (it would need a build without the self-updater).
Milestone 6's F-Droid items are parked.

## D-022 — Assistant role is set via system settings, not RoleManager (2026-10-18)
Found on the Pixel 9: `RoleManager.createRequestRoleIntent(ROLE_ASSISTANT)` is cancelled without a
dialog because the assistant role is not requestable by apps. Onboarding must open
`Settings.ACTION_VOICE_INPUT_SETTINGS` (Pixel: "Digital assistant app"), falling back to
`ACTION_MANAGE_DEFAULT_APPS_SETTINGS`, then `ACTION_SETTINGS`, and tell the user to pick Veil.
`RoleManager.isRoleHeld(ROLE_ASSISTANT)` still reports the status. Supersedes the onboarding line
in the original plan.

## D-023 — OCR recognizer on the default CPU provider; XNNPACK detector-only (2026-10-18)
On the Pixel 9 the recognizer crashed natively inside ONNX Runtime 1.31.0 with XNNPACK and ran fine
without it. The recognizer now always uses the default CPU EP; `OcrConfig.detXnnpack` (default off)
applies XNNPACK to the detector only, pending a timing comparison.

## D-024 — Latin-script filter on OCR lines (2026-10-18)
The multi-script recognizer dictionary turns status-bar icons into CJK characters. Lines with any
non-Latin letter are dropped (`OcrConfig.latinOnly`, default on). Revisit when source languages
other than Latin-script ones are supported. Real captures should also exclude the status/nav bar
areas (milestone 2).

## D-025 — "profile" build type for timings (2026-10-18)
Debuggable builds run Kotlin pre/post-processing much slower, so spike timings from the debug APK
overstate cost. New `profile` build type: release settings, not minified, signed with the debug key.
CI uploads `app-debug.apk` and `app-profile.apk` together as `veil-apks-<sha>` (replaces
`veil-debug-<sha>` from D-014).

## D-026 — New secret release key; supersedes the committed-key part of D-019 (2026-10-18)
Releases are signed with a new key that is **not** in the repo: only its owner can sign Veil
updates. Reason: Veil will run an accessibility service that reads the whole screen; with a
public key (befikr's approach), anyone could build an APK that Android accepts as a Veil update
and inherits that access. The befikr Android key was considered and rejected (it is public too, and
one key per app is cleaner).
- The user generates it on their Linux machine, next to their other keys (consistency), and keeps a
  backup. Losing it means every user must uninstall and reinstall.
- CI gets it from GitHub secrets `VEIL_KEYSTORE_B64` (base64 of the .jks) and
  `VEIL_KEYSTORE_PASSWORD`. Alias `veil`, PKCS12 (store and key password are the same).
- Whatever key signs the first release is Veil's key forever (0.x, 1.0 and beyond).
- One-time cost: the currently installed debug-signed app must be uninstalled once (models are
  downloaded again).
- `app/debug.keystore` (D-014) stays public and signs only debug/profile builds; it must never sign
  a release.

## D-027 — Milestone 0.5 (release pipeline + in-app updates) moves ahead of the remaining spikes (2026-10-18)
The user doesn't want to keep downloading CI artifacts and installing by hand. Order now: finish
milestone 0.5 first, then continue spike measurements (profile timings, trigger, translate) using
in-app updates. Work continues on the user's Linux machine.

## D-028 — Debug/profile builds get applicationId suffix `.dev` (2026-10-18)
They are signed with the public debug key, so they cannot update the release app. With
`applicationIdSuffix = ".dev"` (`com.owaisahmed.veil.dev`) they install next to the release app,
for crash traces and experiments. Their models are separate (different app storage).
