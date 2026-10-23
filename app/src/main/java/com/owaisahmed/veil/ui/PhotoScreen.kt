package com.owaisahmed.veil.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Card
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import kotlin.math.abs
import android.content.ClipboardManager
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import androidx.compose.ui.unit.IntOffset
import com.owaisahmed.veil.R
import com.owaisahmed.veil.photo.PhotoFiles
import com.owaisahmed.veil.photo.PhotoSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Photo mode (D-057): take a photo with the camera app or pick one, then OCR → translate → the
 * English drawn into the picture. Tap the picture (or the switch) for the original; pinch or
 * double-tap to zoom; Rotate turns it a quarter and reads it again (D-058). Tap a box for its
 * German; Select part reads only a dragged area; Share sends the picture to another app (D-060). A camera photo
 * can be saved as it is or translated; a picked one only translated (the original is already in
 * the gallery).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PhotoScreen(onOpenModels: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cameraUri = remember { PhotoFiles.cameraTarget(context) }
    // The photo and its translation live in PhotoSession, so they survive rotation and tab switches (D-059).
    val session = PhotoSession
    val photo = session.photo
    val fromCamera = session.fromCamera
    val translated = session.translated
    var showTranslated by session::showTranslated
    val status = session.status
    var error by session::error
    val needsModel = session.needsModel
    val englishNow = session.english.toList()

    var zoom by remember { mutableStateOf(ZoomState()) }
    // Choosing a part of the photo (D-060): a drag on the unzoomed image, in its layout coordinates.
    var selecting by remember { mutableStateOf(false) }
    var dragFrom by remember { mutableStateOf<Offset?>(null) }
    var dragTo by remember { mutableStateOf<Offset?>(null) }
    var imageWidth by remember { mutableIntStateOf(1) }
    LaunchedEffect(session.generation) { zoom = ZoomState() }

    fun open(uri: Uri, camera: Boolean) = session.open(context, uri, camera)
    fun rotate() = session.rotate()

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) open(cameraUri, camera = true)
    }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) open(uri, camera = false)
    }

    fun save(bitmap: Bitmap, suffix: String) {
        scope.launch {
            val name = "veil-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + suffix
            val ok = withContext(Dispatchers.IO) { runCatching { PhotoFiles.saveToGallery(context, bitmap, name) } }
            Toast.makeText(
                context,
                ok.fold({ "Saved to Pictures/Veil" }, { "Couldn't save: ${it.message}" }),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    Column(
        modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                try {
                    takePicture.launch(cameraUri)
                } catch (_: ActivityNotFoundException) {
                    error = "No camera app found."
                }
            }) { Text("Take photo") }
            FilledTonalButton(onClick = {
                pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }) { Text("Choose photo") }
        }

        val p = photo
        if (p == null && status == null) {
            Hint(
                "Take a photo of a sign, a letter or a menu, or pick one from your gallery. Veil reads " +
                    "the German in it and draws the English on top. Needs text recognition and a " +
                    "translation model (Models). Everything stays on this phone.",
                Modifier.padding(horizontal = 4.dp),
            )
        }

        status?.let {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(it, style = MaterialTheme.typography.bodyMedium)
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
        error?.let {
            Column {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                if (needsModel) TextButton(onClick = onOpenModels) { Text("Open Models") }
            }
        }

        if (p != null) {
            val t = translated
            if (t != null) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(true to "Translated", false to "Original").forEachIndexed { i, (value, label) ->
                        SegmentedButton(
                            selected = showTranslated == value,
                            onClick = { showTranslated = value },
                            shape = SegmentedButtonDefaults.itemShape(i, 2),
                        ) { Text(label) }
                    }
                }
            }
            val shown = if (showTranslated && t != null) t else p
            val blocks = session.blocks
            val selected = session.selected
            val highlight = MaterialTheme.colorScheme.primary
            // Boxes tapped to show the photo's own text again, like the swipe's boxes (D-065, D-066).
            var revealed by remember(blocks) { mutableStateOf(emptySet<Int>()) }
            val original = remember(p) { p.asImageBitmap() }
            Image(
                shown.asImageBitmap(),
                contentDescription = if (shown === t) "Photo with English translation" else "Original photo",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .onSizeChanged { imageWidth = it.width.coerceAtLeast(1) }
                    .pointerInput(Unit) {
                        // Two fingers zoom; once zoomed, one finger pans. Unzoomed single-finger drags
                        // are left alone so the page still scrolls. Off while choosing a part.
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            do {
                                val event = awaitPointerEvent()
                                if (!selecting && (event.changes.size > 1 || zoom.scale > 1f)) {
                                    zoom = zoom.transform(event.calculateZoom(), event.calculatePan(), size)
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    }
                    .pointerInput(selecting, t != null) {
                        if (selecting) {
                            detectDragGestures(
                                onDragStart = { dragFrom = it; dragTo = it },
                                onDrag = { change, _ -> dragTo = change.position; change.consume() },
                            )
                        } else {
                            detectTapGestures(
                                // Tap a box: the photo's own text there, tap again for the English.
                                // Long-press a box: its German and English below. Elsewhere: flip the whole photo.
                                onTap = { pos ->
                                    val (x, y) = zoom.toPhoto(pos, size, p.width)
                                    val hit = session.blockAt(x, y)
                                    when {
                                        hit != null && showTranslated && t != null -> revealed = if (hit in revealed) revealed - hit else revealed + hit
                                        t != null -> showTranslated = !showTranslated
                                    }
                                },
                                onLongPress = { pos ->
                                    val (x, y) = zoom.toPhoto(pos, size, p.width)
                                    session.blockAt(x, y)?.let { hit -> session.selected = if (hit == session.selected) null else hit }
                                },
                                onDoubleTap = { zoom = if (zoom.scale > 1f) ZoomState() else ZoomState(scale = 2.5f) },
                            )
                        }
                    }
                    .graphicsLayer {
                        scaleX = zoom.scale
                        scaleY = zoom.scale
                        translationX = zoom.offset.x
                        translationY = zoom.offset.y
                    }
                    .drawWithContent {
                        drawContent()
                        val k = size.width / p.width
                        if (shown === t) revealed.mapNotNull { blocks.getOrNull(it)?.bounds }.forEach { b ->
                            drawImage(
                                original,
                                srcOffset = IntOffset(b.left, b.top),
                                srcSize = IntSize(b.width(), b.height()),
                                dstOffset = IntOffset((b.left * k).roundToInt(), (b.top * k).roundToInt()),
                                dstSize = IntSize((b.width() * k).roundToInt(), (b.height() * k).roundToInt()),
                            )
                            drawRect(
                                highlight.copy(alpha = 0.7f),
                                topLeft = Offset(b.left * k, b.top * k),
                                size = Size(b.width() * k, b.height() * k),
                                style = Stroke(width = 1.dp.toPx() / zoom.scale),
                            )
                        }
                        selected?.let { blocks.getOrNull(it) }?.bounds?.let { b ->
                            drawRect(
                                highlight,
                                topLeft = Offset(b.left * k, b.top * k),
                                size = Size(b.width() * k, b.height() * k),
                                style = Stroke(width = 3.dp.toPx() / zoom.scale),
                            )
                        }
                        session.area?.takeIf { !selecting }?.let { a ->
                            drawRect(
                                highlight.copy(alpha = 0.6f),
                                topLeft = Offset(a.left * k, a.top * k),
                                size = Size(a.width() * k, a.height() * k),
                                style = Stroke(width = 2.dp.toPx() / zoom.scale, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))),
                            )
                        }
                        val from = dragFrom
                        val to = dragTo
                        if (selecting && from != null && to != null) {
                            val r = Rect(minOf(from.x, to.x), minOf(from.y, to.y), maxOf(from.x, to.x), maxOf(from.y, to.y))
                            drawRect(highlight.copy(alpha = 0.15f), r.topLeft, r.size)
                            drawRect(highlight, r.topLeft, r.size, style = Stroke(width = 3.dp.toPx()))
                        }
                    },
            )

            if (selecting) {
                Hint("Drag over the part you want translated.", Modifier.padding(horizontal = 4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val from = dragFrom
                    val to = dragTo
                    Button(
                        enabled = from != null && to != null && abs(from.x - to.x) > 24 && abs(from.y - to.y) > 24,
                        onClick = {
                            if (from != null && to != null) {
                                val k = p.width / imageWidth.toFloat()
                                session.translateArea(
                                    android.graphics.Rect(
                                        (minOf(from.x, to.x) * k).toInt(), (minOf(from.y, to.y) * k).toInt(),
                                        (maxOf(from.x, to.x) * k).toInt(), (maxOf(from.y, to.y) * k).toInt(),
                                    )
                                )
                            }
                            selecting = false
                        },
                    ) { Text("Translate this part") }
                    TextButton(onClick = { selecting = false }) { Text("Cancel") }
                }
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = ::rotate, enabled = status == null) {
                        Icon(painterResource(R.drawable.ic_rotate), contentDescription = null)
                        Text("Rotate", Modifier.padding(start = 8.dp))
                    }
                    OutlinedButton(
                        onClick = { zoom = ZoomState(); dragFrom = null; dragTo = null; session.selected = null; selecting = true },
                        enabled = status == null,
                    ) { Text("Select part") }
                    if (session.area != null) TextButton(onClick = { session.translateArea(null) }, enabled = status == null) { Text("Whole photo") }
                    if (zoom.scale > 1f) TextButton(onClick = { zoom = ZoomState() }) { Text("Fit") }
                }
                Hint("Tap a box to see the photo's own text, long-press for German and English. Pinch or double-tap to zoom.", Modifier.padding(horizontal = 4.dp))
            }

            selected?.let { i -> blocks.getOrNull(i) }?.let { block ->
                val en = englishNow.getOrNull(selected)
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("German", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        SelectionContainer { Text(block.text, style = MaterialTheme.typography.bodyLarge) }
                        if (en != null) {
                            Text("English", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            SelectionContainer { Text(en, style = MaterialTheme.typography.bodyLarge) }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { copy(context, block.text, "German copied") }) { Text("Copy German") }
                            if (en != null) TextButton(onClick = { copy(context, en, "English copied") }) { Text("Copy English") }
                            TextButton(onClick = { session.selected = null }) { Text("Close") }
                        }
                    }
                }
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (t != null) FilledTonalButton(onClick = { save(t, "-translated") }) { Text("Save translated") }
                if (fromCamera) OutlinedButton(onClick = { save(p, "") }) { Text("Save original") }
                OutlinedButton(onClick = {
                    scope.launch {
                        runCatching { withContext(Dispatchers.IO) { PhotoFiles.share(context, if (showTranslated && t != null) t else p) } }
                            .onFailure { Toast.makeText(context, "Couldn't share: ${it.message}", Toast.LENGTH_SHORT).show() }
                    }
                }) {
                    Icon(painterResource(R.drawable.ic_share), contentDescription = null)
                    Text("Share", Modifier.padding(start = 8.dp))
                }
                if (englishNow.any { it != null }) {
                    TextButton(onClick = { copy(context, englishNow.filterNotNull().joinToString("\n\n"), "English copied") }) {
                        Text("Copy all English")
                    }
                }
            }
        }
    }
}

private fun copy(context: Context, text: String, done: String) {
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Veil photo", text))
    Toast.makeText(context, done, Toast.LENGTH_SHORT).show()
}

/** Zoom of the photo: [scale] around the centre, then [offset] in px, kept so the photo fills its box. */
private data class ZoomState(val scale: Float = 1f, val offset: Offset = Offset.Zero) {
    fun transform(zoomBy: Float, pan: Offset, size: IntSize): ZoomState {
        val s = (scale * zoomBy).coerceIn(1f, MAX_ZOOM)
        val maxX = size.width * (s - 1) / 2
        val maxY = size.height * (s - 1) / 2
        val o = offset + pan
        return ZoomState(s, Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY)))
    }

    /** Photo pixel under [pos] (layout coordinates of the unzoomed image, [size]) for a photo [photoWidth] wide. */
    fun toPhoto(pos: Offset, size: IntSize, photoWidth: Int): Pair<Int, Int> {
        val c = Offset(size.width / 2f, size.height / 2f)
        val q = c + (pos - c - offset) / scale
        val k = photoWidth / size.width.toFloat()
        return (q.x * k).toInt() to (q.y * k).toInt()
    }

    companion object {
        const val MAX_ZOOM = 5f
    }
}
