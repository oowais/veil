package com.owaisahmed.veil.pipeline

import android.content.ComponentCallbacks2
import android.content.Context
import android.os.SystemClock
import android.util.LruCache
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.owaisahmed.veil.capture.AccessibilityTextSource
import com.owaisahmed.veil.capture.GermanFilter
import com.owaisahmed.veil.capture.TextNode
import com.owaisahmed.veil.capture.VeilAccessibilityService
import com.owaisahmed.veil.models.ModelStore
import com.owaisahmed.veil.overlay.TranslationOverlay
import com.owaisahmed.veil.translate.Translator
import com.owaisahmed.veil.trigger.TriggerController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Milestone 1: assistant gesture → accessibility text → German filter → translator → overlay.
 * Blocks are translated one by one, top to bottom, and drawn as soon as each is done. Triggering
 * again, or the screen underneath changing, removes the overlay. Main thread, except the work
 * inside coroutines.
 */
object Pipeline {

    /** Longer node texts (whole articles) don't fit the 2048-token context; skipped for now. */
    private const val MAX_CHARS = 1500
    /** Events right after showing are often the app settling, not the user moving on. */
    private const val EVENT_GRACE_MS = 800L
    // Hy-MT2: generation 2 threads, prompt 4 (D-033), to be confirmed from the logged tok/s.
    // OPUS-MT: 4 ONNX Runtime threads (the big and mid cores).
    private fun threads(engine: Engine) = if (engine == Engine.HY_MT2) 2 else 4

    private lateinit var app: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var overlay: TranslationOverlay? = null
    private var shownAt = 0L

    private val loadLock = Mutex()
    private var translator: Pair<Engine, Translator>? = null
    private val cache = LruCache<String, String>(500)

    fun init(context: Context) {
        app = context.applicationContext
        TriggerController.onTrigger = { onTrigger() }
        VeilAccessibilityService.onEvent = ::onEvent
    }

    private fun onTrigger() {
        if (overlay?.isShowing == true) {
            dismiss("trigger again")
            return
        }
        val service = VeilAccessibilityService.instance
        if (service == null) {
            PipelineLog.log("trigger: accessibility service is off")
            Toast.makeText(app, "Turn on Veil under Settings → Accessibility (see the Veil app)", Toast.LENGTH_LONG).show()
            return
        }
        val ov = TranslationOverlay(service).also { it.show() }
        overlay = ov
        shownAt = SystemClock.elapsedRealtime()
        job = scope.launch { run(service, ov) }
    }

    private suspend fun run(service: VeilAccessibilityService, ov: TranslationOverlay) {
        val t0 = SystemClock.elapsedRealtime()
        try {
            val nodes = withContext(Dispatchers.Default) { AccessibilityTextSource(service).capture() }
            val german = GermanFilter.select(nodes) { it.text }
            val (fit, tooLong) = german.partition { it.text.length <= MAX_CHARS }
            PipelineLog.log(
                "capture: ${nodes.size} texts, ${german.size} German" +
                    (if (tooLong.isNotEmpty()) ", ${tooLong.size} too long" else "") +
                    " (${SystemClock.elapsedRealtime() - t0} ms)"
            )
            if (fit.isEmpty()) {
                finish(ov, "No German text found")
                return
            }
            val tr = translator(ov) ?: return
            val ordered = fit.sortedWith(compareBy<TextNode>({ it.bounds.top }, { it.bounds.left }))
            var firstAt = 0L
            for ((i, node) in ordered.withIndex()) {
                ov.setStatus("Translating ${i + 1}/${ordered.size}…")
                val en = cache.get(node.text) ?: tr.translate(node.text).let { r ->
                    val s = r.stats
                    PipelineLog.log(
                        "#${i + 1} ${node.text.length} chars: in %d tok %.0f ms, out %d tok %.0f ms (%.1f tok/s)".format(
                            s.promptTokens, s.promptMs, s.generatedTokens, s.generateMs, s.generateTokensPerSec,
                        )
                    )
                    r.text.also { cache.put(node.text, it) }
                }
                ov.addBlock(node.bounds, en)
                if (firstAt == 0L) firstAt = SystemClock.elapsedRealtime() - t0
            }
            ov.setStatus(null)
            PipelineLog.log("done: ${ordered.size} blocks, first after $firstAt ms, all after ${SystemClock.elapsedRealtime() - t0} ms")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            PipelineLog.log("failed: ${e::class.simpleName}: ${e.message}")
            finish(ov, "Veil failed: ${e.message}")
        }
    }

    /**
     * Loads the selected engine on first use and keeps it warm (switching engines replaces it);
     * null (with a message shown) if its model isn't downloaded.
     */
    private suspend fun translator(ov: TranslationOverlay): Translator? = loadLock.withLock {
        val engine = Engine.selected(app)
        translator?.let { (e, t) -> if (e == engine) return@withLock t else t.close() }
        translator = null
        if (!ModelStore(app).isInstalled(engine.spec)) {
            PipelineLog.log("translator: ${engine.name} model not downloaded")
            finish(ov, "Download the translation model in the Veil app first (Veil tab)")
            return@withLock null
        }
        ov.setStatus("Loading translation model…")
        val t0 = SystemClock.elapsedRealtime()
        engine.load(app, threads(engine)).also {
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

    private fun onEvent(event: AccessibilityEvent) {
        if (overlay?.isShowing != true) return
        if (event.packageName == app.packageName) return
        // Screenshots, the notification shade and volume panel aren't the app moving on.
        if (event.packageName == "com.android.systemui") return
        if (SystemClock.elapsedRealtime() - shownAt < EVENT_GRACE_MS) return
        dismiss("${AccessibilityEvent.eventTypeToString(event.eventType)} in ${event.packageName}")
    }

    private fun dismiss(reason: String?) {
        job?.cancel()
        job = null
        overlay?.dismiss()
        overlay = null
        if (reason != null) PipelineLog.log("dismissed: $reason")
    }

    /** Frees the ~2.5 GB model when Android asks for memory, unless a translation is running. */
    fun onTrimMemory(level: Int) {
        if (level < ComponentCallbacks2.TRIM_MEMORY_BACKGROUND || job?.isActive == true) return
        scope.launch {
            loadLock.withLock {
                translator?.second?.close()?.also { PipelineLog.log("translator: unloaded (trim memory $level)") }
                translator = null
            }
        }
    }
}
