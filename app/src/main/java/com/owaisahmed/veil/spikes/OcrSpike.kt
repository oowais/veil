package com.owaisahmed.veil.spikes

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.owaisahmed.veil.BuildConfig
import com.owaisahmed.veil.layout.BlockBuilder
import com.owaisahmed.veil.layout.Line
import com.owaisahmed.veil.models.ModelRegistry
import com.owaisahmed.veil.ocr.CharDict
import com.owaisahmed.veil.ocr.OcrConfig
import com.owaisahmed.veil.ocr.OcrEngine
import com.owaisahmed.veil.ocr.OcrResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Spike b: German screenshot → PP-OCRv6 small → lines/blocks, with timings. */
@Composable
fun OcrSpike(modifier: Modifier = Modifier) {
    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ModelCard(ModelRegistry.ocrSmall) { store ->
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            var maxSide by remember { mutableIntStateOf(1280) }
            var xnnpack by remember { mutableStateOf(false) }
            var uri by remember { mutableStateOf<Uri?>(null) }
            var busy by remember { mutableStateOf(false) }
            var report by remember { mutableStateOf<String?>(null) }
            var engine by remember { mutableStateOf<Pair<OcrConfig, OcrEngine>?>(null) }
            DisposableEffect(Unit) { onDispose { engine?.second?.close() } }

            fun run(target: Uri) {
                busy = true
                scope.launch {
                    report = try {
                        withContext(Dispatchers.Default) {
                            val config = OcrConfig(detMaxSide = maxSide, detXnnpack = xnnpack)
                            val ocr = engine?.takeIf { it.first == config }?.second ?: run {
                                engine?.second?.close()
                                val spec = ModelRegistry.ocrSmall
                                OcrEngine(
                                    store.file(spec, "det.onnx"),
                                    store.file(spec, "rec.onnx"),
                                    CharDict.fromYaml(store.file(spec, "rec.yml")),
                                    config,
                                ).also { engine = config to it }
                            }
                            val bmp = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, target)) { d, _, _ ->
                                d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                            }.copy(Bitmap.Config.ARGB_8888, false)
                            // First run includes ORT warm-up; report it separately from the best of three.
                            val runs = List(3) { ocr.recognize(bmp) }
                            format(bmp, runs)
                        }
                    } catch (e: Throwable) {
                        "Failed: ${e::class.simpleName}: ${e.message}"
                    }
                    busy = false
                }
            }

            val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { picked ->
                if (picked != null) { uri = picked; run(picked) }
            }

            Text("Detector input (longest side)")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(960, 1280, 1600, 1920).forEach { side ->
                    FilterChip(selected = maxSide == side, onClick = { maxSide = side }, label = { Text("$side") })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Switch(checked = xnnpack, onCheckedChange = { xnnpack = it })
                Text("XNNPACK for detector (recognizer always CPU)")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = !busy, onClick = {
                    pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) { Text(if (busy) "Running…" else "Pick screenshot") }
                Button(enabled = !busy && uri != null, onClick = { uri?.let(::run) }) { Text("Re-run") }
            }
            HorizontalDivider()
            report?.let { Text(it, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

private fun format(bmp: Bitmap, runs: List<OcrResult>): String {
    val first = runs.first()
    val best = runs.drop(1).minBy { it.timings.total }
    val blocks = BlockBuilder().build(best.lines.map { Line(it.text, it.quad.left, it.quad.top, it.quad.right, it.quad.bottom) })
    val allText = best.lines.joinToString(" ") { it.text }
    val umlauts = allText.count { it in "äöüÄÖÜß" }
    return buildString {
        appendLine("Image ${bmp.width}×${bmp.height}, det input ${best.detInput.first}×${best.detInput.second}")
        val c = first.timings
        val w = best.timings
        appendLine("Cold: det ${c.det} ms + rec ${c.rec} ms = ${c.total} ms")
        appendLine("Warm: det ${w.det} ms + rec ${w.rec} ms = ${w.total} ms")
        appendLine("  det: pre ${w.detPre} · infer ${w.detInfer} · post ${w.detPost}")
        appendLine("  rec: pre ${w.recPre} · infer ${w.recInfer} · post ${w.recPost}")
        appendLine("Build: ${if (BuildConfig.DEBUG) "debug (Kotlin parts run slower)" else "optimized"}")
        appendLine("${best.boxes} boxes → ${best.lines.size} lines (${best.dropped} dropped) → ${blocks.size} blocks; umlaut/ß chars: $umlauts")
        appendLine()
        appendLine("── Blocks ──")
        blocks.forEach { appendLine("• ${it.text}") }
        appendLine()
        appendLine("── Lines (score) ──")
        best.lines.forEach { appendLine("%.2f  %s".format(it.score, it.text)) }
    }
}
