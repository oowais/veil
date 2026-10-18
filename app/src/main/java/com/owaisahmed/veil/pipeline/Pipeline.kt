package com.owaisahmed.veil.pipeline

import android.content.ComponentCallbacks2
import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.LruCache
import android.view.WindowInsets
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
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
import com.owaisahmed.veil.translate.Translator
import com.owaisahmed.veil.trigger.TriggerController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * Assistant gesture → screenshot + accessibility text (+ OCR for text in images) → translate or
 * just show → overlay (D-035, D-038–D-040). Accessibility text is shown first; OCR runs alongside
 * and its boxes follow. Main thread, except the work inside coroutines.
 */
object Pipeline {

    /** Longer node texts (whole articles) are skipped for now. */
    private const val MAX_CHARS = 1500
    /** Window events right after showing are often the app settling, not the user leaving. */
    private const val EVENT_GRACE_MS = 800L

    // Hy-MT2: generation 2 threads, prompt 4 (D-033). OPUS-MT: 4 ONNX Runtime threads.
    private fun threads(engine: Engine) = if (engine == Engine.HY_MT2) 2 else 4

    private lateinit var app: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var overlay: TranslationOverlay? = null
    private var shownAt = 0L
    /** The app being translated; leaving it closes the overlay. */
    private var appPackage: CharSequence? = null

    private val loadLock = Mutex()
    private var translator: Pair<Engine, Translator>? = null
    private val cache = LruCache<String, String>(500)
    private lateinit var ocr: OcrReader

    fun init(context: Context) {
        app = context.applicationContext
        ocr = OcrReader(app)
        TriggerController.onTrigger = { onTrigger() }
        VeilAccessibilityService.onEvent = ::onEvent
        VeilAccessibilityService.onConnected = ::preload
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
        val ov = TranslationOverlay(service, screen, mode == Mode.TRANSLATE) { dismiss("closed") }
        overlay = ov
        ov.show()
        shownAt = SystemClock.elapsedRealtime()
        try {
            val nodes = AccessibilityTextSource(service).capture()
            val ocrJob = startOcr(service, screen, nodes)
            val picked = pick(mode, nodes)
            PipelineLog.log(
                "capture ${mode.name}: ${nodes.size} texts, ${picked.size} picked, screenshot ${if (screen != null) "ok" else "refused"}" +
                    " (${SystemClock.elapsedRealtime() - t0} ms)"
            )
            val blocks = picked.mapNotNull { n -> ov.addBlock(n.bounds, n.text)?.let { n to it } }.toMutableList()

            if (mode == Mode.TRANSLATE) {
                val tr = translator(ov) ?: return
                var firstAt = 0L
                var done = 0
                suspend fun translateAll(list: List<Pair<TextNode, TranslationOverlay.Block>>) {
                    for ((node, block) in list) {
                        ov.setStatus("Translating ${++done}/${blocks.size}…")
                        block.setTranslation(translate(tr, node.text, done))
                        if (firstAt == 0L) firstAt = SystemClock.elapsedRealtime() - t0
                    }
                }
                translateAll(blocks.toList())
                ocrJob?.await()?.let { found ->
                    val more = pick(mode, found).mapNotNull { n -> ov.addBlock(n.bounds, n.text)?.let { n to it } }
                    blocks += more
                    translateAll(more)
                }
                ov.setStatus(null)
                if (blocks.isEmpty()) return finish(ov, "No German text found")
                PipelineLog.log("done: ${blocks.size} blocks, first after $firstAt ms, all after ${SystemClock.elapsedRealtime() - t0} ms")
            } else {
                ocrJob?.await()?.let { found -> blocks += pick(mode, found).mapNotNull { n -> ov.addBlock(n.bounds, n.text)?.let { n to it } } }
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
    private fun pick(mode: Mode, nodes: List<TextNode>): List<TextNode> {
        val withText = nodes.filter { n -> n.text.length <= MAX_CHARS && n.text.any(Char::isLetter) }
        val picked = if (mode == Mode.TRANSLATE) GermanFilter.select(withText) { it.text } else withText
        return picked.sortedWith(compareBy<TextNode>({ it.bounds.top }, { it.bounds.left }))
    }

    /** OCR of the screenshot, alongside the accessibility text; null if no model or no screenshot. */
    private fun startOcr(service: VeilAccessibilityService, screen: Bitmap?, known: List<TextNode>): Deferred<List<TextNode>>? {
        if (screen == null || !ocr.available) return null
        val bars = service.getSystemService(WindowManager::class.java).currentWindowMetrics.windowInsets
            .getInsets(WindowInsets.Type.systemBars())
        return scope.async {
            val t0 = SystemClock.elapsedRealtime()
            runCatching { ocr.read(screen, known, bars.top, bars.bottom) }
                .onSuccess { PipelineLog.log("ocr: ${it.size} new blocks (${SystemClock.elapsedRealtime() - t0} ms)") }
                .onFailure { if (it is CancellationException) throw it; PipelineLog.log("ocr failed: ${it.message}") }
                .getOrDefault(emptyList())
        }
    }

    private suspend fun translate(tr: Translator, text: String, n: Int): String = cache.get(text) ?: tr.translate(text).let { r ->
        val s = r.stats
        PipelineLog.log(
            "#$n ${text.length} chars: in %d tok %.0f ms, out %d tok %.0f ms (%.1f tok/s)".format(
                s.promptTokens, s.promptMs, s.generatedTokens, s.generateMs, s.generateTokensPerSec,
            )
        )
        r.text.also { cache.put(text, it) }
    }

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
            ov?.let { finish(it, "Download the translation model in the Veil app first (Veil tab)") }
            return@withLock null
        }
        ov?.setStatus("Loading translation model…")
        PipelineLog.log("translator: loading ${engine.name}, ${threads(engine)} threads")
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
        if (pkg == app.packageName || pkg == "com.android.systemui" || pkg == appPackage) return
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
