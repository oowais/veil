package com.owaisahmed.veil.pipeline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.owaisahmed.veil.models.ModelDownloader
import com.owaisahmed.veil.models.ModelSpec
import com.owaisahmed.veil.models.ModelStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * One model on the Veil tab: name, size, download (with progress, resumable) or delete.
 * [selected]/[onSelect] add a radio button, for the translation engines.
 */
@Composable
fun ModelRow(
    spec: ModelSpec,
    title: String,
    detail: String,
    selected: Boolean? = null,
    onSelect: () -> Unit = {},
    onInstalledChange: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val store = remember { ModelStore(context) }
    val scope = rememberCoroutineScope()
    var installed by remember(spec) { mutableStateOf(store.isInstalled(spec)) }
    var progress by remember(spec) { mutableFloatStateOf(0f) }
    var job by remember(spec) { mutableStateOf<Job?>(null) }
    var error by remember(spec) { mutableStateOf<String?>(null) }
    var confirmDelete by remember(spec) { mutableStateOf(false) }
    val mb = "%.0f MB".format(spec.totalBytes / 1e6)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (selected != null) RadioButton(selected = selected, onClick = onSelect)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text("$detail · $mb · ${if (installed) "downloaded" else "not downloaded"}", style = MaterialTheme.typography.bodySmall)
            }
            when {
                installed -> TextButton(onClick = { confirmDelete = true }) { Text("Delete") }
                job != null -> OutlinedButton(onClick = { job?.cancel() }) { Text("Cancel") }
                else -> Button(onClick = {
                    error = null
                    job = scope.launch {
                        try {
                            ModelDownloader(store).download(spec) { done, total -> progress = done.toFloat() / total }
                            installed = store.isInstalled(spec)
                            onInstalledChange(installed)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            error = e.message
                        } finally {
                            job = null
                        }
                    }
                }) { Text(if (progress > 0f) "Resume" else "Download") }
            }
        }
        if (job != null) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        error?.let { Text("Download failed: $it", style = MaterialTheme.typography.bodySmall) }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete $title?") },
            text = { Text("Frees $mb. You can download it again any time.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        Pipeline.forget(spec)
                        store.delete(spec)
                        installed = false
                        progress = 0f
                        onInstalledChange(false)
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep") } },
        )
    }
}
