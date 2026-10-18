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
