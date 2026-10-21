package com.owaisahmed.veil.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.owaisahmed.veil.capture.TextNode
import com.owaisahmed.veil.pipeline.Pipeline
import com.owaisahmed.veil.pipeline.PipelineLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The photo being translated, kept by the app rather than the screen (D-059): turning the phone
 * (which rebuilds the screen) or switching tabs keeps the photo, and a running translation goes on.
 * Lost only when Android ends the app process.
 */
object PhotoSession {
    private const val DETECTOR_SIDE = 1280
    private const val MAX_ENLARGE = 2.5f

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var renderJob: Job? = null

    var photo by mutableStateOf<Bitmap?>(null)
        private set
    var fromCamera by mutableStateOf(false)
        private set
    var blocks by mutableStateOf<List<TextNode>>(emptyList())
        private set
    val english = mutableStateListOf<String?>()
    var translated by mutableStateOf<Bitmap?>(null)
        private set
    var showTranslated by mutableStateOf(true)
    var status by mutableStateOf<String?>(null)
        private set
    var error by mutableStateOf<String?>(null)
    var needsModel by mutableStateOf(false)
        private set
    /** The part of the photo that was read (photo pixels), or null for the whole photo (D-060). */
    var area by mutableStateOf<Rect?>(null)
        private set
    /** Index into [blocks] of the box tapped to see its German, or null. */
    var selected by mutableStateOf<Int?>(null)
    /** Goes up with every new or rotated photo, so the screen can reset its zoom. */
    var generation by mutableIntStateOf(0)
        private set

    fun open(context: Context, uri: Uri, camera: Boolean) {
        val app = context.applicationContext
        process(camera) { withContext(Dispatchers.IO) { PhotoFiles.load(app, uri) } }
    }

    /**
     * Turns the photo a quarter clockwise and reads it again: OCR only finds upright text, and
     * translations already made come from the cache.
     */
    fun rotate() {
        val p = photo ?: return
        process(fromCamera) { withContext(Dispatchers.Default) { PhotoFiles.rotate(p, 90f) } }
    }

    /** Reads only [part] of the photo (photo pixels), or the whole photo again for null (D-060). */
    fun translateArea(part: Rect?) {
        val p = photo ?: return
        process(fromCamera, part) { p }
    }

    /** The box at [x],[y] (photo pixels), if any; the smallest one when boxes overlap. */
    fun blockAt(x: Int, y: Int): Int? =
        blocks.indices.filter { blocks[it].bounds.contains(x, y) }.minByOrNull { blocks[it].bounds.width() * blocks[it].bounds.height() }

    /** Reads and translates a photo; [load] produces it (open a file, or rotate the current one). */
    private fun process(camera: Boolean, part: Rect? = null, load: suspend () -> Bitmap) {
        job?.cancel()
        renderJob?.cancel()
        generation++
        job = scope.launch {
            error = null
            needsModel = false
            translated = null
            blocks = emptyList()
            english.clear()
            showTranslated = true
            selected = null
            area = part
            try {
                status = "Opening photo…"
                val bmp = load()
                photo = bmp
                fromCamera = camera
                status = "Reading text…"
                val found = if (part == null) Pipeline.photoTexts(bmp) else withContext(Dispatchers.Default) { readPart(bmp, part) }
                if (found.isEmpty()) {
                    error = if (part != null) "No German text found in this part." else "No German text found in this photo. If it's sideways, rotate it."
                    return@launch
                }
                blocks = found
                english.addAll(List(found.size) { null })
                var done = 0
                status = "Translating 0/${found.size}…"
                Pipeline.translatePhotoTexts(found.map { it.text }) { i, en ->
                    english[i] = en
                    status = "Translating ${++done}/${found.size}…"
                    render(bmp, found)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalStateException) {
                error = e.message
                needsModel = true
            } catch (e: Exception) {
                PipelineLog.log("photo failed: ${e::class.simpleName}: ${e.message}")
                error = "Couldn't translate this photo: ${e.message}"
            } catch (e: OutOfMemoryError) {
                PipelineLog.log("photo failed: out of memory")
                error = "Not enough memory for this photo. Close some apps and try again."
            } finally {
                status = null
            }
        }
    }

    /**
     * OCR of just [part]: cut out and, if small, enlarged so small text reaches the detector's
     * working size (1280 px); boxes are mapped back to the whole photo.
     */
    private suspend fun readPart(bmp: Bitmap, part: Rect): List<TextNode> {
        val r = Rect(part).apply { intersect(0, 0, bmp.width, bmp.height) }
        if (r.width() < 8 || r.height() < 8) return emptyList()
        val f = (DETECTOR_SIDE.toFloat() / maxOf(r.width(), r.height())).coerceIn(1f, MAX_ENLARGE)
        val crop = Bitmap.createBitmap(bmp, r.left, r.top, r.width(), r.height()).let {
            if (f > 1f) Bitmap.createScaledBitmap(it, (it.width * f).toInt(), (it.height * f).toInt(), true) else it
        }
        return Pipeline.photoTexts(crop).map { n ->
            val b = n.bounds
            n.copy(
                bounds = Rect((b.left / f).toInt() + r.left, (b.top / f).toInt() + r.top, (b.right / f).toInt() + r.left, (b.bottom / f).toInt() + r.top),
                lineHeight = (n.lineHeight / f).toInt(),
            )
        }
    }

    /** Redraws the translated picture, batching translations that arrive close together. */
    private fun render(bmp: Bitmap, found: List<TextNode>) {
        renderJob?.cancel()
        renderJob = scope.launch {
            delay(150)
            val now = english.toList()
            translated = withContext(Dispatchers.Default) {
                PhotoRenderer.render(bmp, found.mapIndexed { i, b -> PhotoRenderer.Box(b.bounds, now.getOrNull(i), b.lineHeight) })
            }
        }
    }
}
