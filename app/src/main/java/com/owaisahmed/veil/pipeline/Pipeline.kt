package com.owaisahmed.veil.pipeline

import android.app.ActivityManager
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.LruCache
import android.view.WindowInsets
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Toast
import com.owaisahmed.veil.capture.AccessibilityTextSource
import com.owaisahmed.veil.capture.GermanFilter
import com.owaisahmed.veil.capture.TextNode
import com.owaisahmed.veil.capture.VeilAccessibilityService
import com.owaisahmed.veil.capture.screenshot
import com.owaisahmed.veil.models.ModelRegistry
import com.owaisahmed.veil.models.ModelSpec
import com.owaisahmed.veil.models.ModelStore
import com.owaisahmed.veil.overlay.TranslationOverlay
import com.owaisahmed.veil.history.TranslationStore
import com.owaisahmed.veil.translate.Translation
import com.owaisahmed.veil.translate.Translator
import com.owaisahmed.veil.trigger.TriggerController
import com.owaisahmed.veil.trigger.TriggerEvent
import com.owaisahmed.veil.trigger.TriggerSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Assistant gesture → screenshot + accessibility text (+ OCR for text in images) → translate or
 * just show → overlay (D-035, D-038–D-040). Accessibility text is shown first; OCR runs alongside
 * and its boxes follow. Main thread, except the work inside coroutines.
 */
object Pipeline {

    /**
     * Safety cap only: long texts are translated sentence by sentence (D-054); a node with more than
     * this is a whole page dumped into one view, not something to draw in one box.
     */
    private const val MAX_CHARS = 10_000
    /** Window events right after showing are often the app settling, not the user leaving. */
    private const val EVENT_GRACE_MS = 800L

    // Hy-MT2: generation 2 threads, prompt 4 (D-033). OPUS-MT: 4 ONNX Runtime threads.
    private fun threads(engine: Engine) = if (engine == Engine.HY_MT2) 2 else 4

    private lateinit var app: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var overlay: TranslationOverlay? = null
    private var shownAt = 0L
    /** The app being translated (for History). */
    private var appPackage: CharSequence? = null
    /** Every app on screen (both halves in split screen); events from others mean the user left (D-056). */
    private var visiblePackages: Set<String> = emptySet()

    private val loadLock = Mutex()
    private var translator: Pair<Engine, Translator>? = null
    /** In-memory cache in front of [store], keyed by engine and text. */
    private val memory = LruCache<String, String>(500)
    private lateinit var store: TranslationStore
    private lateinit var ocr: OcrReader

    fun init(context: Context) {
        app = context.applicationContext
        ocr = OcrReader(app)
        store = TranslationStore.get(app)
        TriggerController.onTrigger = { onTrigger() }
        VeilAccessibilityService.onEvent = ::onEvent
        VeilAccessibilityService.onConnected = ::preload
        // Window events from the lock screen come from System UI, which is ignored, so the screen
        // going off has to close the overlay itself (D-056).
        app.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (overlay?.isShowing == true || job?.isActive == true) dismiss("screen off")
            }
        }, IntentFilter(Intent.ACTION_SCREEN_OFF))
        logConnections()
    }

    /**
     * Every swipe and every service (dis)connect goes to the activity log, so an empty log after a
     * swipe means Android never delivered it (D-047).
     */
    private fun logConnections() {
        scope.launch {
            TriggerController.events.collect { e ->
                PipelineLog.log(
                    when (e) {
                        is TriggerEvent.Shown -> "swipe: received"
                        is TriggerEvent.Hidden -> "swipe: assistant window gone after ${e.afterMs} ms"
                        is TriggerEvent.Fired -> when (e.source) {
                            TriggerSource.ASSIST_GESTURE -> "swipe: starting"
                            TriggerSource.QS_TILE -> "tile: starting"
                            TriggerSource.LINK -> "link: starting"
                        }
                    }
                )
            }
        }
        scope.launch {
            TriggerController.assistantConnected.drop(1).collect {
                PipelineLog.log("assistant service ${if (it) "connected" else "disconnected"}")
            }
        }
        scope.launch {
            VeilAccessibilityService.connected.drop(1).collect {
                PipelineLog.log("accessibility service ${if (it) "connected" else "disconnected"}")
            }
        }
    }

    /** Loads the small models as soon as the service runs, so the first swipe is fast (D-041). */
    private fun preload() {
        scope.launch {
            try {
                if (Mode.selected(app) == Mode.TRANSLATE && Engine.selected(app) == Engine.OPUS_MT) translator(null)
                if (ocr.available) {
                    val t0 = SystemClock.elapsedRealtime()
                    ocr.preload()
                    PipelineLog.log("ocr: loaded in ${SystemClock.elapsedRealtime() - t0} ms")
                }
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                PipelineLog.log("preload failed: ${e::class.simpleName}: ${e.message}")
            }
        }
    }

    private fun onTrigger() {
        if (overlay?.isShowing == true || job?.isActive == true) {
            dismiss("trigger again")
            return
        }
        // On the lock screen there's nothing of the user's to translate, and the boxes' copy buttons
        // would expose notification text without unlocking (D-056).
        if (app.getSystemService(KeyguardManager::class.java).isKeyguardLocked) {
            PipelineLog.log("trigger: phone is locked")
            Toast.makeText(app, "Unlock your phone first", Toast.LENGTH_SHORT).show()
            return
        }
        val service = VeilAccessibilityService.instance
        if (service == null) {
            PipelineLog.log("trigger: accessibility service is off")
            Toast.makeText(app, "Turn on Veil under Settings → Accessibility (see the Veil app)", Toast.LENGTH_LONG).show()
            return
        }
        job = scope.launch { run(service) }
    }

    private suspend fun run(service: VeilAccessibilityService) {
        val t0 = SystemClock.elapsedRealtime()
        val mode = Mode.selected(app)
        // The screenshot must be taken before Veil's own window is on screen.
        val screen = service.screenshot()
        appPackage = service.rootInActiveWindow?.packageName
        visiblePackages = service.windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            .mapNotNull { it.root?.packageName?.toString() }.toSet() + listOfNotNull(appPackage?.toString())
        val ov = TranslationOverlay(service, screen, mode == Mode.TRANSLATE, Appearance.style(app)) { dismiss(it) }
        overlay = ov
        ov.show()
        shownAt = SystemClock.elapsedRealtime()
        try {
            val source = TextSource.selected(app)
            val nodes = if (source == TextSource.OCR) emptyList() else AccessibilityTextSource(service).capture()
            val ocrJob = if (source == TextSource.ACCESSIBILITY) null else startOcr(service, screen, nodes)
            if (source == TextSource.OCR && ocrJob == null) {
                return finish(ov, if (screen == null) "This app doesn't allow screenshots" else "Download text recognition first: Veil app → Models")
            }
            val picked = pick(mode, nodes)
            PipelineLog.log(
                "capture ${mode.name}, ${source.name}: ${nodes.size} texts, ${picked.size} picked, screenshot ${if (screen != null) "ok" else "refused"}" +
                    " (${SystemClock.elapsedRealtime() - t0} ms)"
            )
            logSkipped("a11y skip", nodes - picked.toSet())
            val blocks = picked.mapNotNull { n -> ov.addBlock(n.bounds, n.text)?.let { n to it } }.toMutableList()

            if (mode == Mode.TRANSLATE) {
                val tr = translator(ov) ?: return
                val engine = translator?.first ?: Engine.selected(app)
                val glossary = Glossary.terms(app)
                tr.setGlossary(glossary)
                val source = appPackage?.toString()
                var firstAt = 0L
                var done = 0
                /**
                 * Known texts are filled in at once; the rest go to the engine in one batch (D-048, D-049).
                 * [fromOcr]: the texts were read from the screenshot (D-051).
                 */
                suspend fun translateAll(list: List<Pair<TextNode, TranslationOverlay.Block>>, fromOcr: Boolean) {
                    // Texts with a glossary term skip the cache: the glossary may have changed since (D-055).
                    val usesGlossary = { t: String -> engine == Engine.HY_MT2 && glossary.any { (de, _) -> t.contains(de, ignoreCase = true) } }
                    val known = cached(engine, list.map { it.first.text }.filterNot(usesGlossary))
                    val todo = list.filter { (node, block) ->
                        val hit = known[node.text] ?: return@filter true
                        block.setTranslation(hit)
                        done++
                        if (firstAt == 0L) firstAt = SystemClock.elapsedRealtime() - t0
                        false
                    }
                    if (known.isNotEmpty()) PipelineLog.log("cache: ${known.size} of ${list.size} known")
                    if (todo.isEmpty()) return
                    ov.setStatus("Translating $done/${blocks.size}…")
                    tr.translateAll(todo.map { it.first.text }, fromOcr = fromOcr) { i, r ->
                        val (node, block) = todo[i]
                        block.setTranslation(r.text)
                        ov.setStatus("Translating ${++done}/${blocks.size}…")
                        if (firstAt == 0L) firstAt = SystemClock.elapsedRealtime() - t0
                        logTranslation(done, node.text, r)
                        remember(engine, node.text, r.text, source)
                    }
                }
                translateAll(blocks.toList(), fromOcr = false)
                ocrJob?.await()?.let { found ->
                    val more = pick(mode, found, screenGerman(nodes + found)).mapNotNull { n -> ov.addBlock(n.bounds, n.text)?.let { n to it } }
                    blocks += more
                    translateAll(more, fromOcr = true)
                }
                ov.setStatus(null)
                if (blocks.isEmpty()) return finish(ov, "No German text found")
                PipelineLog.log("done: ${blocks.size} blocks, first after $firstAt ms, all after ${SystemClock.elapsedRealtime() - t0} ms")
            } else {
                ocrJob?.await()?.let { found -> blocks += pick(mode, found, null).mapNotNull { n -> ov.addBlock(n.bounds, n.text)?.let { n to it } } }
                if (blocks.isEmpty()) return finish(ov, "No text found")
                ov.setStatus("Tap a box to copy it")
                PipelineLog.log("done: ${blocks.size} blocks after ${SystemClock.elapsedRealtime() - t0} ms")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            PipelineLog.log("failed: ${e::class.simpleName}: ${e.message}")
            finish(ov, "Veil failed: ${e.message}")
        }
    }

    /** German texts to translate, or every text with letters to copy. */
    private fun pick(mode: Mode, nodes: List<TextNode>, screenGerman: Boolean? = null): List<TextNode> {
        val withText = nodes.filter { n -> n.text.length <= MAX_CHARS && n.text.any(Char::isLetter) }
        val picked = if (mode == Mode.TRANSLATE) GermanFilter.select(withText, screenGerman) { it.text } else withText
        return picked.sortedWith(compareBy<TextNode>({ it.bounds.top }, { it.bounds.left }))
    }

    /** The language verdict for the whole screen: accessibility and OCR text together (D-034). */
    private fun screenGerman(all: List<TextNode>) = GermanFilter.isGerman(all.filter { n -> n.text.any(Char::isLetter) }) { it.text }

    /** OCR of the screenshot, alongside the accessibility text; null if no model or no screenshot. */
    private fun startOcr(service: VeilAccessibilityService, screen: Bitmap?, known: List<TextNode>): Deferred<List<TextNode>>? {
        if (screen == null || !ocr.available) return null
        val bars = service.getSystemService(WindowManager::class.java).currentWindowMetrics.windowInsets
            .getInsets(WindowInsets.Type.systemBars())
        return scope.async {
            val t0 = SystemClock.elapsedRealtime()
            runCatching { ocr.read(screen, known, bars.top, bars.bottom) }
                .onSuccess {
                    PipelineLog.log(
                        "ocr: ${it.lines} lines (${it.inBars} in status/nav bar) → ${it.found.size} new blocks, " +
                            "${it.covered.size} already covered (${SystemClock.elapsedRealtime() - t0} ms)"
                    )
                    logSkipped("ocr covered", it.covered)
                    logSkipped("ocr new", it.found)
                }
                .onFailure { if (it is CancellationException) throw it; PipelineLog.log("ocr failed: ${it.message}") }
                .map { it.found }
                .getOrDefault(emptyList())
        }
    }

    /** Up to 15 texts with their box, so missed text can be traced from the copied log. */
    private fun logSkipped(tag: String, nodes: Collection<TextNode>) {
        nodes.take(15).forEach { n ->
            val b = n.bounds
            PipelineLog.log("  $tag [${b.left},${b.top} ${b.width()}×${b.height()}] ${n.text.take(40).replace('\n', ' ')}")
        }
        if (nodes.size > 15) PipelineLog.log("  $tag … ${nodes.size - 15} more")
    }

    private fun logTranslation(n: Int, text: String, r: Translation) {
        val s = r.stats
        PipelineLog.log(
            "#$n ${text.length} chars: in %d tok %.0f ms, out %d tok %.0f ms (%.1f tok/s)".format(
                s.promptTokens, s.promptMs, s.generatedTokens, s.generateMs, s.generateTokensPerSec,
            )
        )
    }

    /** Translations already known for [engine]: memory first, then the saved ones on disk. */
    private suspend fun cached(engine: Engine, texts: List<String>): Map<String, String> {
        val out = HashMap<String, String>()
        val missing = texts.filter { text -> memory.get(key(engine, text))?.also { out[text] = it } == null }
        if (missing.isNotEmpty() && store.saving) {
            val saved = withContext(Dispatchers.IO) { runCatching { store.cached(engine.name, missing) }.getOrDefault(emptyMap()) }
            saved.forEach { (text, en) -> memory.put(key(engine, text), en); out[text] = en }
        }
        return out
    }

    /** Keeps a new translation in memory and, when saving is on, in the cache and history on disk. */
    private fun remember(engine: Engine, text: String, translation: String, sourceApp: String?) {
        memory.put(key(engine, text), translation)
        scope.launch(Dispatchers.IO) {
            runCatching { store.save(engine.name, text, translation, sourceApp) }
                .onFailure { PipelineLog.log("history: save failed: ${it.message}") }
        }
    }

    private fun key(engine: Engine, text: String) = "${engine.name}\u0000$text"

    /** Called after history items are deleted, so deleted texts aren't served from memory. */
    fun forgetTranslations() = memory.evictAll()

    /**
     * Loads the selected engine on first use and keeps it warm (switching engines replaces it);
     * null if its model isn't downloaded (with a message on [ov], if any).
     */
    private suspend fun translator(ov: TranslationOverlay?): Translator? = loadLock.withLock {
        val engine = Engine.selected(app)
        translator?.let { (e, t) -> if (e == engine) return@withLock t else t.close() }
        translator = null
        if (!ModelStore(app).isInstalled(engine.spec)) {
            PipelineLog.log("translator: ${engine.name} model not downloaded")
            ov?.let { finish(it, "Download a translation model first: Veil app → Models") }
            return@withLock null
        }
        ov?.setStatus("Loading translation model…")
        val mem = ActivityManager.MemoryInfo().also { app.getSystemService(ActivityManager::class.java).getMemoryInfo(it) }
        PipelineLog.log(
            "translator: loading ${engine.name}, ${threads(engine)} threads, %.1f of %.1f GB RAM free%s".format(
                mem.availMem / 1e9, mem.totalMem / 1e9, if (mem.lowMemory) " (low)" else "",
            )
        )
        val t0 = SystemClock.elapsedRealtime()
        engine.load(app, threads(engine)) { PipelineLog.log("translator: $it") }.also {
            translator = engine to it
            PipelineLog.log("translator: ${engine.name} loaded in ${SystemClock.elapsedRealtime() - t0} ms, ${threads(engine)} threads")
        }
    }

    /** Shows [message] briefly, then removes the overlay. */
    private suspend fun finish(ov: TranslationOverlay, message: String) {
        ov.setStatus(message)
        delay(2500)
        if (overlay === ov) dismiss(null)
    }

    /** Leaving the app closes the overlay; notifications, the shade and screenshots (System UI) don't. */
    private fun onEvent(event: AccessibilityEvent) {
        if (overlay?.isShowing != true) return
        val pkg = event.packageName ?: return
        if (pkg == app.packageName || pkg == "com.android.systemui" || pkg.toString() in visiblePackages) return
        if (SystemClock.elapsedRealtime() - shownAt < EVENT_GRACE_MS) return
        dismiss("left the app (${AccessibilityEvent.eventTypeToString(event.eventType)} in $pkg)")
    }

    private fun dismiss(reason: String?) {
        job?.cancel()
        job = null
        overlay?.dismiss()
        overlay = null
        if (reason != null) PipelineLog.log("dismissed: $reason")
    }

    /** Unloads [spec] if it is in use and drops its cached optimized graphs, before it is deleted. */
    suspend fun forget(spec: ModelSpec) {
        loadLock.withLock {
            if (translator?.first?.spec == spec) {
                translator?.second?.close()
                translator = null
            }
        }
        if (spec == ModelRegistry.ocrSmall) ocr.close()
        File(app.cacheDir, "ort/${spec.id}").deleteRecursively()
        PipelineLog.log("models: ${spec.id} removed")
    }

    /** Frees the models when Android asks for memory, unless a translation is running. */
    fun onTrimMemory(level: Int) {
        if (level < ComponentCallbacks2.TRIM_MEMORY_BACKGROUND || job?.isActive == true) return
        scope.launch {
            loadLock.withLock {
                translator?.second?.close()?.also { PipelineLog.log("translator: unloaded (trim memory $level)") }
                translator = null
            }
            ocr.close()
        }
    }
}
