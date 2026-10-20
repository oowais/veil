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
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.owaisahmed.veil.capture.TextNode
import com.owaisahmed.veil.photo.PhotoFiles
import com.owaisahmed.veil.photo.PhotoRenderer
import com.owaisahmed.veil.pipeline.Pipeline
import com.owaisahmed.veil.pipeline.PipelineLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Photo mode (D-057): take a photo with the camera app or pick one, then OCR → translate → the
 * English drawn into the picture. Tap the picture (or the switch) for the original. A camera photo
 * can be saved as it is or translated; a picked one only translated (the original is already in
 * the gallery).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoScreen(onOpenModels: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cameraUri = remember { PhotoFiles.cameraTarget(context) }
    var photo by remember { mutableStateOf<Bitmap?>(null) }
    var fromCamera by remember { mutableStateOf(false) }
    var blocks by remember { mutableStateOf<List<TextNode>>(emptyList()) }
    val english = remember { mutableStateListOf<String?>() }
    var translated by remember { mutableStateOf<Bitmap?>(null) }
    var showTranslated by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var needsModel by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }

    fun open(uri: Uri, camera: Boolean) {
        job?.cancel()
        job = scope.launch {
            error = null
            needsModel = false
            translated = null
            blocks = emptyList()
            english.clear()
            showTranslated = true
            try {
                status = "Opening photo…"
                val bmp = withContext(Dispatchers.IO) { PhotoFiles.load(context, uri) }
                photo = bmp
                fromCamera = camera
                status = "Reading text…"
                val found = Pipeline.photoTexts(bmp)
                if (found.isEmpty()) {
                    error = "No German text found in this photo."
                    return@launch
                }
                blocks = found
                english.addAll(List(found.size) { null })
                var done = 0
                status = "Translating 0/${found.size}…"
                Pipeline.translatePhotoTexts(found.map { it.text }) { i, en ->
                    english[i] = en
                    status = "Translating ${++done}/${found.size}…"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalStateException) {
                error = e.message
                needsModel = true
            } catch (e: Exception) {
                PipelineLog.log("photo failed: ${e::class.simpleName}: ${e.message}")
                error = "Couldn't translate this photo: ${e.message}"
            } finally {
                status = null
            }
        }
    }

    // Redraw the translated picture as translations arrive (briefly batched; a full-size copy each time).
    val englishNow = english.toList()
    LaunchedEffect(photo, blocks, englishNow) {
        val p = photo ?: return@LaunchedEffect
        if (blocks.isEmpty() || englishNow.all { it == null }) return@LaunchedEffect
        delay(150)
        translated = withContext(Dispatchers.Default) {
            PhotoRenderer.render(p, blocks.mapIndexed { i, b -> b.bounds to englishNow.getOrNull(i) })
        }
    }

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
                    .clickable(enabled = t != null) { showTranslated = !showTranslated },
            )
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
