package com.owaisahmed.veil.photo

import android.content.Context
import android.graphics.Bitmap
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

    /** Reads and translates a photo; [load] produces it (open a file, or rotate the current one). */
    private fun process(camera: Boolean, load: suspend () -> Bitmap) {
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
            try {
                status = "Opening photo…"
                val bmp = load()
                photo = bmp
                fromCamera = camera
                status = "Reading text…"
                val found = Pipeline.photoTexts(bmp)
                if (found.isEmpty()) {
                    error = "No German text found in this photo. If it's sideways, rotate it."
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

    /** Redraws the translated picture, batching translations that arrive close together. */
    private fun render(bmp: Bitmap, found: List<TextNode>) {
        renderJob?.cancel()
        renderJob = scope.launch {
            delay(150)
            val now = english.toList()
            translated = withContext(Dispatchers.Default) {
                PhotoRenderer.render(bmp, found.mapIndexed { i, b -> b.bounds to now.getOrNull(i) })
            }
        }
    }
}
