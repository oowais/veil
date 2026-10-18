package com.owaisahmed.veil.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.owaisahmed.veil.models.ModelDownloader
import com.owaisahmed.veil.models.ModelSpec
import com.owaisahmed.veil.models.ModelStore
import com.owaisahmed.veil.pipeline.Pipeline
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * One model: name, size and state, with download (progress, resumable, cancellable) or delete.
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

    Column {
        ListItem(
            modifier = if (selected != null) Modifier.clickable(onClick = onSelect) else Modifier,
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = selected?.let { { RadioButton(selected = it, onClick = onSelect) } },
            headlineContent = { Text(title) },
            supportingContent = {
                Text(
                    when {
                        job != null -> "Downloading… ${(progress * 100).toInt()} % of $mb"
                        installed -> "$detail · $mb on this phone"
                        else -> "$detail · $mb download"
                    }
                )
            },
            trailingContent = {
                when {
                    installed -> TextButton(onClick = { confirmDelete = true }) { Text("Delete") }
                    job != null -> OutlinedButton(onClick = { job?.cancel() }) { Text("Cancel") }
                    else -> FilledTonalButton(onClick = {
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
                    }) { Text(if (progress > 0f) "Resume" else "Get") }
                }
            },
        )
        if (job != null) {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        }
        error?.let {
            Text(
                "Download failed: $it",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
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
