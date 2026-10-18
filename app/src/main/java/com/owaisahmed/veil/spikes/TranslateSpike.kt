package com.owaisahmed.veil.spikes

import android.os.Debug
import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.owaisahmed.veil.models.ModelRegistry
import com.owaisahmed.veil.translate.LlamaTranslator
import kotlinx.coroutines.launch

/** Spike c: Hy-MT2 1.8B via llama.cpp — load time, RAM, tokens/s on ten German paragraphs. */
@Composable
fun TranslateSpike(modifier: Modifier = Modifier) {
    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ModelCard(ModelRegistry.hyMt2Q4) { store ->
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            var threads by remember { mutableIntStateOf(4) }
            var translator by remember { mutableStateOf<LlamaTranslator?>(null) }
            var status by remember { mutableStateOf("Model not loaded.") }
            var busy by remember { mutableStateOf(false) }
            var input by remember { mutableStateOf("") }
            val results = remember { mutableStateListOf<String>() }
            DisposableEffect(Unit) { onDispose { translator?.close() } }

            fun pssMb() = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }.totalPss / 1024

            Text("Threads")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(2, 4, 6, 8).forEach { n ->
                    FilterChip(selected = threads == n, enabled = translator == null, onClick = { threads = n }, label = { Text("$n") })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        status = try {
                            translator?.close()
                            translator = null
                            val before = pssMb()
                            val t0 = SystemClock.elapsedRealtime()
                            translator = LlamaTranslator.load(context, store.file(ModelRegistry.hyMt2Q4, "model.gguf"), threads = threads)
                            "Loaded in ${SystemClock.elapsedRealtime() - t0} ms, PSS ${before} → ${pssMb()} MB\n\n" +
                                LlamaTranslator.systemInfo(context)
                        } catch (e: Throwable) {
                            "Load failed: ${e.message}"
                        }
                        busy = false
                    }
                }) { Text(if (translator == null) "Load model" else "Reload") }

                Button(enabled = !busy && translator != null, onClick = {
                    val tr = translator ?: return@Button
                    busy = true
                    results.clear()
                    scope.launch {
                        var genTok = 0; var genMs = 0.0; var promptTok = 0; var promptMs = 0.0
                        val wall = SystemClock.elapsedRealtime()
                        try {
                            for ((i, de) in germanSamples.withIndex()) {
                                val r = tr.translate(de)
                                val s = r.stats
                                genTok += s.generatedTokens; genMs += s.generateMs
                                promptTok += s.promptTokens; promptMs += s.promptMs
                                results += "#${i + 1}  %.0f+%.0f ms, %.1f tok/s\nDE: %s\nEN: %s".format(
                                    s.promptMs, s.generateMs, s.generateTokensPerSec, de, r.text,
                                )
                            }
                            results.add(
                                0,
                                "Total %d ms wall · prompt %.0f tok/s · generate %.1f tok/s · PSS %d MB".format(
                                    SystemClock.elapsedRealtime() - wall,
                                    promptTok * 1000.0 / promptMs, genTok * 1000.0 / genMs, pssMb(),
                                ),
                            )
                        } catch (e: Throwable) {
                            results += "Failed: ${e.message}"
                        }
                        busy = false
                    }
                }) { Text("Run 10 samples") }
            }

            OutlinedTextField(value = input, onValueChange = { input = it }, label = { Text("German text") }, modifier = Modifier.fillMaxWidth())
            Button(enabled = !busy && translator != null && input.isNotBlank(), onClick = {
                val tr = translator ?: return@Button
                busy = true
                scope.launch {
                    results.add(0, try {
                        val r = tr.translate(input)
                        "%.1f tok/s\nEN: %s".format(r.stats.generateTokensPerSec, r.text)
                    } catch (e: Throwable) {
                        "Failed: ${e.message}"
                    })
                    busy = false
                }
            }) { Text("Translate") }

            HorizontalDivider()
            CopyButton("Veil translate", enabled = translator != null || results.isNotEmpty()) {
                "Translate, $threads threads\n$status\n\n" + results.joinToString("\n\n")
            }
            Text(status, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            results.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
