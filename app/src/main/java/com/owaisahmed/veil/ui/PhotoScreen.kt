package com.owaisahmed.veil.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
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
 * double-tap to zoom; Rotate turns it a quarter and reads it again (D-058). A camera photo
 * can be saved as it is or translated; a picked one only translated (the original is already in
 * the gallery).
 */
@OptIn(ExperimentalMaterial3Api::class)
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
            Image(
                shown.asImageBitmap(),
                contentDescription = if (shown === t) "Photo with English translation" else "Original photo",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .pointerInput(Unit) {
                        // Two fingers zoom; once zoomed, one finger pans. Unzoomed single-finger drags
                        // are left alone so the page still scrolls.
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            do {
                                val event = awaitPointerEvent()
                                if (event.changes.size > 1 || zoom.scale > 1f) {
                                    zoom = zoom.transform(event.calculateZoom(), event.calculatePan(), size)
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    }
                    .pointerInput(t != null) {
                        detectTapGestures(
                            onTap = { if (t != null) showTranslated = !showTranslated },
                            onDoubleTap = { zoom = if (zoom.scale > 1f) ZoomState() else ZoomState(scale = 2.5f) },
                        )
                    }
                    .graphicsLayer {
                        scaleX = zoom.scale
                        scaleY = zoom.scale
                        translationX = zoom.offset.x
                        translationY = zoom.offset.y
                    },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = ::rotate, enabled = status == null) {
                    Icon(painterResource(R.drawable.ic_rotate), contentDescription = null)
                    Text("Rotate", Modifier.padding(start = 8.dp))
                }
                if (zoom.scale > 1f) TextButton(onClick = { zoom = ZoomState() }) { Text("Fit") }
                Hint("Pinch to zoom, double-tap to zoom in or out.", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (t != null) FilledTonalButton(onClick = { save(t, "-translated") }) { Text("Save translated") }
                if (fromCamera) OutlinedButton(onClick = { save(p, "") }) { Text("Save original") }
            }
            if (englishNow.any { it != null }) {
                TextButton(onClick = {
                    context.getSystemService(ClipboardManager::class.java)
                        .setPrimaryClip(ClipData.newPlainText("Veil photo", englishNow.filterNotNull().joinToString("\n\n")))
                    Toast.makeText(context, "English copied", Toast.LENGTH_SHORT).show()
                }) { Text("Copy English") }
            }
        }
    }
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

    companion object {
        const val MAX_ZOOM = 5f
    }
}
