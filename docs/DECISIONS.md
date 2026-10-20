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

## D-029 — Daily update check runs on app start; dev builds don't self-update (2026-10-18)
Settles the open question from D-020. The check runs when the app opens, at most once a day
(`UpdateCheck`, last result kept in SharedPreferences), not in WorkManager: no new dependency and
no background network use. Veil is opened rarely once it works from the assistant gesture, so
this may be revisited then (e.g. check from the assistant service, or WorkManager).
Debug/profile builds (`.dev`, D-028) cannot install `veil.apk` (other app id), so they only show the
latest version and point to GitHub releases; the Install button is release-only.
Release `versionCode = minor × 1000 + patch` (D-019), so patch must stay below 1000.

## D-030 — Phone testing without adb (2026-10-18)
The user does not connect the Pixel to the PC. Veil is installed from the release link and updated
in-app (milestone 0.5); results come from what the app shows on screen. So spike and debug output
must be visible in the app (on-screen logs, copy/share buttons), not only in logcat. No adb steps
in instructions.

## D-031 — OCR detector stays on the default CPU provider, no XNNPACK (2026-10-18)
Measured on the Pixel 9 with release v0.1.0 (same screenshot, det input 576×1280): detector
inference 315 ms on the default CPU EP vs 584 ms with XNNPACK. Together with the recognizer crash
(D-023), XNNPACK brings nothing; `OcrConfig.detXnnpack` stays default off and can be removed with
the spike UI.

## D-032 — One CI workflow: build once, release after it passes (2026-10-18)
`ci.yml` and `release.yml` ran in parallel on every push: the native code compiled twice, and a
release could publish even when lint or tests failed. Merged into `ci.yml`: job `build` (every push
and PR) computes the version, runs lint and unit tests and builds the release APK once — signed
with the secret key only on a push to `main` that releases. Job `release` needs `build` and only
publishes the APK it produced. The `veil-apks-<sha>` dev artifacts (D-014, D-025) are dropped: the
user tests release builds installed on the phone without adb (D-030), and release builds are
optimised, so profile builds aren't needed for timings. Debug/profile stay for local builds.
Docs-only pushes still skip CI; `workflow_dispatch` can retry a release by hand.

## D-033 — Translator uses at most 4 threads (2026-10-18)
Measured on the Pixel 9 (Tensor G4: 1× X4, 3× A720, 4× A520), release v0.2.0, Hy-MT2 1.8B Q4_K_M,
10 samples: generation 16.7 / 15.3 / 10.9 / 7.5 tok/s at 2 / 4 / 6 / 8 threads, prompt 49 / 67 /
54 / 50 tok/s. Threads beyond the 4 big+mid cores land on A520s and slow every step. Default stays
4 (fastest overall, 21.9 s). Generation is memory-bound (2 threads generate fastest) and prompt
processing compute-bound (4 is best), so milestone 1 tries `n_threads = 2`,
`n_threads_batch = 4`. Other devices: cap at the number of non-little cores, not the core count.

## D-034 — German detection: word-list heuristic, judged per screen (2026-10-18)
No language-ID model for now. `GermanFilter` scores each text: umlauts/ß and German function
words count for German, English function words against. Single UI labels ("Einstellungen") carry
little evidence, so the screen is judged first: if the sum over all texts is German, every text
with letters is translated unless it scores English; otherwise nothing is. Texts without letters
(times, numbers) are skipped. Cheap and unit-tested; revisit with a tiny langid if mixed-language
screens go wrong.

## D-035 — Milestone 1 pipeline shape (2026-10-18)
- Lives in `:app` (`pipeline/Pipeline`), set up in `VeilApp.onCreate`, so it works when the gesture
  starts the process.
- Text from all `TYPE_APPLICATION` windows (status bar, keyboard and Veil's own overlay are other
  types); editable and password fields skipped; duplicates (same text and bounds) dropped.
- Translates one text at a time, top to bottom, drawing each as soon as it's done (batching is
  milestone 3). In-memory LRU cache (500). Texts over 1500 chars skipped for now.
- KV-cache prefix reuse in the JNI bridge: tokens shared with the previous call (chat template +
  instruction) stay in the cache; only the rest is processed.
- Threads 2 (generate) / 4 (prompt) to try D-033; the log reports tok/s to compare.
- Overlay is not touchable (the app underneath stays usable). It goes away on the next gesture or
  on a window-state/scroll event from another app (ignored for 800 ms after showing). A status
  line at the top shows loading/progress/errors.
- Model loads on the first trigger and stays loaded; unloaded on `TRIM_MEMORY_BACKGROUND` or worse
  when no translation runs.
- Veil tab: setup checklist (assistant, accessibility service, model) and the pipeline log with a
  copy button, since testing happens without adb (D-030).

## D-036 — OPUS-MT on ONNX Runtime is the default translator; Hy-MT2 stays as the quality option (2026-10-18)
The first milestone-1 run took 16 s to load Hy-MT2 Q4_K_M (1.1 GB). The 1.25-bit build (462 MB)
would help but needs llama.cpp PR #22836, still unmerged; we wait for it (the user tracks it).
Until then the default is OPUS-MT de→en (Marian, Apache-2.0), int8 ONNX from Xenova/opus-mt-de-en
at a pinned revision, ~108 MB. Chosen over Firefox Translations (Bergamot): similar quality, but it
runs on ONNX Runtime, which Veil already ships for OCR, so no second native engine. NLLB-200
(non-commercial license) and M2M100 (weaker on de→en) were rejected.
- Own Kotlin SentencePiece unigram tokenizer (source.spm + vocab.json), checked id-for-id against
  the real SentencePiece library on 14 texts; the translator is checked against a Python
  onnxruntime reference using desktop ONNX Runtime in unit tests.
- Text is split into sentences: Marian drops sentences from multi-sentence input ("Hast du heute
  Abend Zeit? …" lost the question unsplit). Greedy decoding (the config's 4 beams later, if needed).
- Desktop reference (Ryzen 7700, 4 threads): 28–272 ms per sample; quality good, a bit more literal
  than Hy-MT2 ("sign up again" for "melden Sie sich erneut an").
- The user picks the engine on the Veil tab (Fast: OPUS-MT, default; Quality: Hy-MT2). The
  Translate tab can run either for comparison.

## D-037 — OPUS-MT stays the default even after the 1.25-bit Hy-MT2 runs (2026-10-18)
User's call: speed of the gesture matters most. The 1.25-bit build is estimated at ~6 s cold /
~2 s warm load and ~22 tok/s, still slower than OPUS-MT (< 1 s load, ~0.1–0.3 s per sentence on
desktop). When PR #22836 merges, the 1.25-bit build is only evaluated as a replacement for
Hy-MT2 Q4_K_M as the quality option. Revisit only if OPUS-MT's quality turns out not good enough
in real use.

## D-038 — Overlay works like Lens / Pixel screen translation (2026-10-18)
User feedback on v0.4.1: the overlay vanished when a notification was swiped away, there was no
way to copy, and it should feel as easy as Lens. Now:
- The overlay window is touchable and focusable (modal): tap a box → original ↔ English, long-press
  → copy it; tap outside the boxes, Back, the Close button or the gesture again → close.
- It closes by itself only when the user leaves the app (window-state event from another package);
  System UI (notifications, shade, screenshots) and the app itself don't close it. Scroll events
  are no longer subscribed (nothing can scroll under a modal overlay).
- Box background: the original area from the screenshot, blurred (RenderEffect, API 31+), under a
  70 % veil in the sampled average color; text dark or light by luminance. Without a screenshot
  (secure windows): solid 92 % color.
- Boxes appear at once and pulse until translated (instead of appearing one by one).
- Toolbar at the bottom: "German → English", Show original/English, Copy German, Copy English,
  Close. Copy-text mode: Copy all, Close.

## D-039 — Two modes: Translate and Copy text (2026-10-18)
The user wants Veil also as a quick way to grab text from the screen. Veil tab → "When I swipe":
Translate (default) or Copy text. Copy text shows outlined boxes over all text (no German filter,
no translation); tap a box copies it, Copy all copies everything in reading order.

## D-040 — OCR of a screenshot runs alongside the accessibility text (2026-10-18)
Pulled from milestone 2. On each swipe Veil takes a screenshot first (also needed for the blur,
D-038; `canTakeScreenshot` in the service config), reads the accessibility text and shows it, and
runs PP-OCRv6 on the screenshot in parallel. OCR paragraphs (BlockBuilder) that overlap
accessibility text (center inside, or > 50 % covered) are dropped; the rest are added and
translated after the accessibility boxes. Status bar and navigation bar areas are ignored. OCR is
optional: only when its model is downloaded.

## D-041 — Preload small models; cache ONNX Runtime's optimized graphs (2026-10-18)
When the accessibility service starts (boot, update, switched on), Veil loads OPUS-MT (if it is
the selected engine) and the OCR model (~360 MB together), so the first swipe doesn't wait.
Hy-MT2 is not preloaded (~2.5 GB). OPUS-MT saves ORT's optimized graphs to
`cacheDir/ort/<model id>/` on the first load and loads those afterwards without re-optimizing
(the decoder's optimization took 1.1 s of the 1.7 s load); an unusable cache file is rebuilt.

## D-042 — Models section: pick, download, delete (2026-10-18)
The Veil tab lists every model with size and status: the two translation engines (radio button
picks the one the gesture uses) and OCR. Download (resumable, cancellable) or Delete (with
confirmation; the model is unloaded first and its cached graphs removed). Replaces the download
cards for the gesture's models; the spike tabs keep theirs.

## D-043 — Draggable toolbar; input fields are translated too (2026-10-18)
The toolbar covered text on some pages: it can now be dragged anywhere and keeps that spot
(SharedPreferences) for later swipes; a small handle shows it moves. Input fields were skipped in
milestone 1 so typing wasn't blocked; with the modal overlay (D-038) that reason is gone, so their
text — or their placeholder when empty — is included (search boxes). Password fields stay out.

## D-044 — OCR text uses the whole screen's language verdict (2026-10-18)
Amends D-034. OCR finds a few short texts in images, often mixed-language slogans, too little to
judge alone. The verdict is now taken over accessibility and OCR text together; on a German screen
every OCR text with letters is translated unless it scores clearly English.

## D-045 — Lighter look for translated boxes (2026-10-18)
User feedback on the Lens-style overlay: too blurry, too opaque, text too big. Blur radius now
15 % of the box height (max 8 dp, was 35 % / 16 dp), the page-colored layer over the blurred
original 45 % opaque (was 70 %; stays 92 % where there is no blur), largest text 16 sp or 60 % of
the box height (was 20 sp / 75 %). Constants at the bottom of `TranslationOverlay.kt`. Trade-off:
the German shows through more; raise `VEIL_ALPHA_BLURRED` if it competes with the English.

## D-046 — Spike screens removed; app UI is Home / Models / Settings (2026-10-18)
The milestone-0 spikes have served their purpose (results in STATUS.md), so the Trigger, OCR and
Translate tabs and their code (`spikes/`) are gone. The app now has three screens behind a bottom
navigation bar, in `app/…/ui/`:
- **Home:** status card (Ready / steps left), setup checklist (assistant, accessibility service,
  translation model, optional text recognition) with a button per missing step, "When I swipe"
  (Translate / Copy text, segmented), tips. Copy text mode doesn't require a translation model.
- **Models:** translation engine choice + download/delete, text recognition model.
- **Settings:** Updates (version, update button, daily check, check now), Privacy, Troubleshooting
  (activity log, collapsed, copy/clear), About (source, model licenses, release notes).
The update dot moved to the Settings icon. Material You colours on Android 12+ (`ui/Theme.kt`).
Re-measuring OCR/translation speed now needs a dev build or the activity log; the spike timing
screens can be restored from git (before this commit) if needed.

## D-047 — Check that the services actually run, and log every swipe (2026-10-19)
After the in-app update to 0.6.0 (and once before, at 0.4.0) swipes did nothing although settings
showed Veil as assistant and its accessibility service on; toggling both fixed it. Android had not
reconnected the services after the package was replaced. Now:
- `VeilAccessibilityService.connected` (true between `onServiceConnected` and unbind/destroy) and
  `TriggerController.assistantConnected` (set in `VeilInteractionService.onReady`/`onShutdown`).
- Home: if a setting is on but its service isn't connected 2 s after the app opens, the step turns
  red: "Switched on, but not running. Turn Veil off and on again." / "Chosen, but Android hasn't
  started it. Pick another assistant, then Veil again."
- The activity log records each swipe as it arrives (`swipe: received / assistant window gone /
  starting`) and every service connect/disconnect. An empty log after a swipe = Android never
  delivered it.
Assumption to verify on the phone: Android calls `onReady` whenever it binds the assistant service
in a fresh process, so a healthy setup never shows the red state.

## D-048 — OPUS-MT translates a screen in batches (2026-10-19)
`Translator.translateAll(texts, onEach)` (default: one by one; Hy-MT2 keeps that). OPUS-MT splits
all texts into sentences and runs them 8 at a time in reading order: padded encoder input, batched
greedy decoding with the KV cache; a text is reported when its last sentence is done.
- Measured on the desktop JVM with the real model, 12 bonn.de texts: ~1010 ms one by one vs ~520 ms
  batched (≈2×).
- Trade-off: the int8 model quantizes activations per batch, so a sentence's exact wording can
  depend on what else is in its batch (seen: "Your session is over" vs "has expired"). Quality was
  comparable in the comparison, and the persistent cache makes repeated screens stable. Single-text
  `translate()` is a batch of one and still matches the Python reference.

## D-049 — Saved translations: persistent cache + History screen (2026-10-19)
User asked for a history of everything translated, with delete one / delete all, and for the cache
to survive restarts. One SQLite file (`translations.db`, app-private) via plain `SQLiteOpenHelper`
(no Room, to keep the build light), `app/…/history/TranslationStore.kt`:
- `cache` (engine, source → translation, used_at; capped at 5000, least recently used dropped).
  Keyed by engine too — the old in-memory cache wasn't, so switching engines could show the other
  engine's translation. An in-memory LRU (500) sits in front.
- `history`: one row per *new* translation (time, app package, engine, German, English). Texts
  served from the cache aren't added again, so re-swiping a page doesn't duplicate it.
- Deleting a history item also deletes that text from the cache (all engines), and the in-memory
  cache is cleared, so the text is really gone. "Delete all" clears both.
- "Save translations" switch on the History screen (default on): off → nothing is written; the
  in-memory cache still works until the app process ends.
- History shows English, German, app name and time, grouped by day; tap expands, copy, delete.
  App names need `<queries>` for launcher apps (package visibility).
- Copy text mode isn't translation and isn't saved.

## D-050 — Model downloads belong to the app, not the screen (2026-10-19)
`ModelDownloads` (in `:models`) runs downloads in an app-wide scope and publishes progress per
model as a `StateFlow`; `ModelRow` only shows it. Leaving the Models screen no longer cancels a
download. Not covered: Android ending the app process (then the next tap resumes via HTTP Range).
A foreground service with a notification would cover that; not worth it yet.

## D-051 — OCR text is marked for the translator; UI labels stay their own OCR blocks (2026-10-20)
User asked for the app to tell OCR text apart from accessibility text and handle each well.
- `Translator.translate/translateAll` take `fromOcr`. The pipeline passes true for blocks found by
  OCR. Hy-MT2 gets one extra sentence in its prompt ("read from a screenshot by OCR … may contain
  wrong line breaks, split words or misread characters; translate what was meant"). OPUS-MT takes no
  instructions, so it ignores the flag; for it, the layout-side clean-up (BlockBuilder, `joinLines`)
  is what counts. The cache stays keyed by text only.
- `BlockBuilder` UI guard: short isolated lines (buttons, tabs, menu entries) stay single blocks.
  Two lines don't join when both have ≤ 3 words, or when the upper one is narrower than 0.6 × the
  lower one (it didn't wrap: a heading or label above body text) — unless the text clearly
  continues (upper line ends with `-` or `,`, or the lower one starts lowercase). Side effect: a
  narrow column whose wrapped lines start with a capital (German nouns) can split; accepted, since
  merged menu entries were the worse failure.

## D-052 — The short-lines guard is a user switch (2026-10-20)
User wants to decide whether splitting short lines (D-051) works better for them. Models → Text in
pictures → "Keep short lines apart" (default on; `OcrSettings`, prefs `pipeline`). Off = OCR lines
join as before D-051. Read on every swipe, so it applies to the next one. Placed next to the text
recognition model because it only affects text read from the screenshot.
