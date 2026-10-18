package com.owaisahmed.veil.spikes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.owaisahmed.veil.models.ModelDownloader
import com.owaisahmed.veil.models.ModelSpec
import com.owaisahmed.veil.models.ModelStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Download status and button for one model. Calls [content] once the files are present. */
@Composable
fun ModelCard(spec: ModelSpec, content: @Composable (ModelStore) -> Unit) {
    val context = LocalContext.current
    val store = remember { ModelStore(context) }
    val scope = rememberCoroutineScope()
    var installed by remember { mutableStateOf(store.isInstalled(spec)) }
    var progress by remember { mutableFloatStateOf(0f) }
    var job by remember { mutableStateOf<Job?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    if (installed) {
        content(store)
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("${spec.displayName} — ${"%.0f".format(spec.totalBytes / 1e6)} MB, ${spec.license}")
        Text("Or adb push the files to ${store.dir(spec).path}")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = job == null, onClick = {
                error = null
                job = scope.launch {
                    try {
                        ModelDownloader(store).download(spec) { done, total -> progress = done.toFloat() / total }
                        installed = store.isInstalled(spec)
                    } catch (e: Exception) {
                        error = e.message
                    } finally {
                        job = null
                    }
                }
            }) { Text(if (progress > 0f) "Resume download" else "Download") }
            Button(enabled = job == null, onClick = { installed = store.isInstalled(spec) }) { Text("Re-check") }
        }
        if (job != null) LinearProgressIndicator(progress = { progress })
        error?.let { Text("Error: $it") }
    }
}
