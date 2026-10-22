# Status

Last updated: 2026-10-20. Update this file in the same commit as any change that moves work forward.

## Where things stand

- **Milestone 0 (spikes):** all three pass on the Pixel 9 with release v0.1.0 (results below).
  Trigger: fires over any app, invisible. OCR: 2.0 s warm, accurate. Translate: 15 tok/s, good
  quality. Thread counts measured (D-033).
- **Milestone 1** (gesture → accessibility text → translate → overlay): works on the Pixel 9
  (v0.3.0–v0.4.1). OPUS-MT is the default engine (D-036/D-037).
- **Ease-of-use batch, 2026-10-18 (not on the phone yet):** Lens-style overlay with blurred
  original and toolbar (Show original, Copy German, Copy English, Close), Back / tap outside closes,
  stays on for notifications and screenshots (D-038); modes Translate / Copy text (D-039); OCR of
  the screenshot alongside the accessibility text (D-040); preload + cached optimized graphs
  (D-041); Models section to pick, download and delete (D-042). Test plan: "Ease-of-use test".

## Quick wins (2026-10-19, not on the phone yet)

Batched OPUS-MT (D-048, ~2× on desktop), persistent cache + History (D-049), downloads survive
leaving Models (D-050). Builds, lint and tests pass (batch test against the real model on desktop).
Check on the phone: swipe a German page twice (second time: log `cache: N of N known`, instant);
restart the phone or force-stop Veil, swipe again (still instant); History lists the texts with app
name and time, Copy works, delete one (then that text is translated again on the next swipe), Delete
all; Save translations off → nothing new appears; start the Hy-MT2 download, switch to Home and back:
still running. Compare first-block time with the log from before (batching).

## OCR-aware translation (2026-10-20, not on the phone yet, D-051)

OCR blocks are flagged to the translator (Hy-MT2 prompt says the text came from OCR; OPUS-MT can't
take instructions) and short stacked labels (menus, buttons, a heading above body text) stay
separate boxes. Builds, lint and tests pass. Check on the phone: a screenshot-only screen with a
menu or button column (e.g. an image with text, a game or canvas app) shows one box per entry,
while wrapped paragraphs still come out as one box.
The guard can be switched off under Models → Text in pictures → "Keep short lines apart"
(D-052); compare both on the same screen and note which reads better.

## Firefox Translations engine (2026-10-21, not on a phone yet, D-062)

Bergamot runs in the emulator: ~10× faster than OPUS-MT there and clearly better on a Wikipedia
page. Default for new installs. Check on the phone: Models → Firefox Translations → Get (37 MB; the
CDN may cut the download, tap Resume), pick it, swipe on a German page (log: `BERGAMOT loaded in`,
`done: … all after`), compare with OPUS-MT on the same page; photo mode with it; memory (Veil should
stay alive with OCR too). If it crashes, logcat tag `VeilBergamot` / the activity log.

## Engine choice needs a downloaded model (2026-10-22, not on a phone yet)

Bug: Models let you pick an engine that wasn't downloaded (Hy-MT2 selected, showing Resume). Now
the radio is disabled until the model is on the phone, and if the selected engine isn't there
(deleted, never finished), the first downloaded one is selected instead. Check on the phone: Models
opens with OPUS-MT or Firefox Translations selected; Hy-MT2 can't be tapped; delete the selected
engine → another downloaded one gets selected.

## Emulator (2026-10-21, D-061)

Veil runs in an Android 15 x86_64 emulator on the Windows box (AVD `veil_demo`, build with
`-PveilAbi=x86_64`; models copied into the app's external files dir as root). Translate via
`adb shell am start -d veil://translate` and photo mode work end to end; README screenshots come
from there. Found and fixed: merged sign paragraphs, see-through photo covers, oversized photo text.
Open, seen there:
- OPUS-MT: "FREUDE." → "FRIEND." (all-caps slogan words; try lower-casing all-caps words first).
- Chrome's address bar ("bonn.de") gets a box and is "translated" to "Bonn.de": skip browser URL
  bars (resource ids `url_bar`/`location_bar`), or text that looks like a domain.
- A hidden "Start content" skip link gets a box over the page header.

## Hardening round 2 (2026-10-20, not on the phone yet, D-059)

Check: photo translated, turn the phone → photo and translation still there; switch to History and
back → still there (and a running translation finishes). Force-stop Veil, open it → Settings →
activity log still shows earlier lines.

## Photo mode (2026-10-20, not on the phone yet, D-057)

New Photo tab: take a photo (camera app) or choose one, Veil reads and translates the German and
draws the English into the picture; save translated (and original for camera photos) to
Pictures/Veil. Builds, lint and tests pass; the drawing and the camera hand-off are untested.
Check on the phone: photo of a sign or letter (upright, boxes in the right places, text readable),
a picked screenshot, Translated/Original switch and tap, both saves appear in Gallery → Veil, Copy
English, a photo without German ("No German text found"), Photo with no OCR model (message +
Open Models). Zoom and rotate (D-058): pinch, pan while zoomed, double-tap, Fit; page still
scrolls when not zoomed; Rotate on a sideways photo finds the text. D-060: Share (to a chat app),
tap a box (German + English below, highlighted, also when zoomed), Select part on small text
(finds more than the whole photo).

## Hardening (2026-10-20, not on the phone yet, D-056)

Check on the phone: rotate with boxes showing (they close); split screen with two apps, swipe,
then scroll the other app (boxes stay); swipe on the lock screen ("Unlock your phone first");
boxes showing, press power (gone after unlock); a banking app (screenshot refused, boxes still
appear for app text); Models on a < 6 GB phone shows the Hy-MT2 warning.

## Features batch (2026-10-20, not on the phone yet, D-055)

Text source setting, Quick Settings tile + `veil://translate`, glossary (Hy-MT2), import model
from files, mobile-data warning, box text size and cover. Builds, lint and tests pass. Check on
the phone: add the Veil tile and tap it over a German page (shade closes, boxes appear, not Veil's
own screen); `adb shell am start -d veil://translate` or a Tasker task; Settings → Images only on a
page (only OCR boxes) and App text only (no OCR log lines); glossary "Sitzung = session" with
Hy-MT2 on a page saying "Sitzung"; import the OCR files downloaded on a computer; text size Large
and cover 20 %/90 %.

## App icon (2026-10-20, not on the phone yet, D-053)

New launcher icon: half-frosted pane on purple. Check on the phone: home screen, app drawer, and
with themed icons on (Wallpaper and style → Themed icons).

## App UI polish (2026-10-18, not on the phone yet)

Spike tabs removed; the app is Home / Models / Settings with a bottom bar (D-046). Builds, lint and
unit tests pass; not yet looked at on a device. Check after updating: Home checklist reflects the
real state (and turns Ready), buttons open the right system screens, Models download/delete and
engine choice work, Settings → Update/Check now, activity log copy, update dot on Settings,
light + dark mode.

## Ease-of-use test (next)

First feedback on v0.5.0: OCR "didn't catch everything" (page title, search field text) and the
toolbar covered text. Fixed: the toolbar can be dragged anywhere (position remembered); input
fields (text or placeholder) are now included, since the modal overlay no longer gets in the way
of typing (passwords still skipped). Not explained yet: the missed title → the log now lists
skipped accessibility texts and OCR blocks (new / already covered) with their boxes.
The screenshot (bonn.de) showed the missed title is text in the photo ("FREUDE. JOY. JOIE. BONN."):
OCR blocks were judged German on their own and that one scores 0 → dropped. Now OCR text uses the
verdict for the whole screen (accessibility + OCR text together).

Toolbar squeezed to a narrow pill with letter-by-letter labels (reported after d62481f): the new
drag handle has a fixed width, and in a WRAP_CONTENT vertical LinearLayout MATCH_PARENT rows then
don't count toward the width, so the bar shrank to the handle. Fixed by giving the rows WRAP_CONTENT.
Not yet confirmed on the phone.
Boxes made lighter (less blur, more transparent, smaller text; D-045) — check readability.

After updating (check the accessibility service is still on; it now also takes screenshots):
1. Veil tab → Models: download OCR (31 MB). Try Delete + Download on one model.
2. Translate mode on a German page: boxes appear at once and pulse, English fills in, the German
   behind is blurred. Tap a box (original ↔ English), long-press (copies). Toolbar: Show original,
   Copy German, Copy English, Close. Pull down notifications / swipe one away: overlay stays.
   Back or a tap outside: gone. Home: gone.
3. A page with German text inside an image: an extra box appears ~2 s later (OCR).
4. Copy text mode: outlined boxes; tap one copies it; Copy all.
5. Copy log. Look for: `ocr: loaded in`, `translator: … optimized graph from cache` from the second
   app start on, `screenshot ok`, time to first block.
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
- v0.4.0 on the phone: OPUS-MT never finished loading (Veil and Translate tab both stuck > 90 s,
  no error). Cause: `readVocab` used Kotlin `Regex.findAll`, which makes a new matcher per match; on
  Android each copies the 1.4 MB vocab.json into ICU → quadratic (58k matches). Desktop JVM tests
  didn't show it. Fixed with a one-pass scanner; the load steps (tokenizer, encoder, decoder) are
  now logged with times on the Veil tab and the Translate tab. Lesson: avoid `Regex.findAll` on
  big strings on Android.
- After the in-app update to v0.4.0 the accessibility service was off; watch whether updates
  always turn it off (then warn about it in the app).
- Again after updating to v0.6.0 (2026-10-19): settings showed Veil as assistant and the
  accessibility service on, but swipes did nothing. Switching the service off/on and re-picking
  Veil as assistant fixed it (done together, so unclear which one was stuck). Likely Android didn't
  re-bind the services after the package was replaced. Now detected on Home and logged (D-047);
  not yet checked on the phone. After the next in-app update: open Veil before swiping and see
  whether Home flags either service; if it does, the toggle fix applies and we know which one.
- OPUS-MT on the phone, v0.4.1, 2026-10-18 — works (D-036 confirmed):
  - Load 1.2–1.7 s (tokenizer 161 ms, encoder 391 ms, decoder 1144 ms). PSS 160 → 356 MB after
    load, 663 MB after the samples (Hy-MT2: ~2.5–2.9 GB).
  - 10 samples in 5.3 s wall (Hy-MT2 at 4 threads: 21.9 s): 0.15–0.9 s each, ~36–50 tok/s.
  - Swipe on a German page: 3 texts, first block after 3.2 s incl. the 1.7 s load, all after 3.4 s.
    Same page again: 77 ms (cache).
  - Quality: good, more literal than Hy-MT2. Misses: #2 Sitzung → "meeting" (should be session),
    #3 "anbei" lost, #7 "Hauptbahnhof" left untranslated and "ausnahmsweise" dropped, #9 clunky.
  - Ideas: save the ORT-optimized decoder to disk on first load (`setOptimizedModelFilePath`) to
    cut the 1.1 s; preloading is now cheap (~360 MB) if the first swipe should be instant.
- Model tests: `VEIL_OPUS_DIR` (folder with source.spm + vocab.json) and `VEIL_OPUS_MODEL` (folder
  laid out like the app's: encoder.onnx, decoder.onnx, source.spm, vocab.json) enable the
  tokenizer and translator tests that use the real model; without them they are skipped (CI).
- Phone test (section above); fix what it finds.
- Hy-MT2 now greedy, batched and chunked (D-054), not on the phone yet. Check with Hy-MT2 selected:
  log has no `didn't come back numbered` lines (else batching falls back), time to first/all
  blocks vs before, translations still good; a long article paragraph (> 1500 chars) now gets
  translated.

Milestone 2 (screenshot + OCR are in, D-040)
- OCR runs on every swipe when its model is there (~2 s, in parallel); measure the CPU cost while
  translating. Skipping it by coverage was rejected (D-054); a "Text source" setting is planned.
- (Done with D-051: UI-label guard in `BlockBuilder`; OCR text marked in the Hy-MT2 prompt.)

Milestone 3
- Batching: done for OPUS-MT (D-048). Hy-MT2: numbered segments with per-batch fallback (D-054).
- Persistent translation cache + History screen: done (D-049).
- (Done earlier with D-038: color sampling, auto-fit text, tap/long-press, pulsing boxes.)
- Model manager (Models screen exists, D-042/D-046): downloads keep running when leaving the screen
  (D-050; not when Android ends the process — not worth a foreground service, D-059). Import from file
  and the mobile-data warning: done (D-055). Custom mirror skipped.
- (`rec.yml` hash pinned, D-056.)

Milestone 4–6
- Settings UI and onboarding checklist: done (D-046). Deeplink `veil://translate` and QS tile: done (D-055).
- Hardening: done in code (D-056), not on the phone yet.
- (Release signing moved to milestone 0.5 — secret key, D-026. F-Droid parked, D-021.)

## Open questions

In-app updates (D-019–D-021, D-026–D-028):
- (Settled by D-029: daily check on app start, no WorkManager.)

Other:

- Versioning is settled by D-019 (CI computes 0.x.y; first release 0.1.0).
- (Glossary: done, D-055 — prefs text, Hy-MT2 only.)
- When llama.cpp PR #22836 merges: does the 1.25-bit build replace Hy-MT2 Q4_K_M as the quality
  option? (Not the default: D-037.) The user tracks the PR.

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
