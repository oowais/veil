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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.owaisahmed.veil.models.ModelDownloads
import com.owaisahmed.veil.models.ModelSpec
import com.owaisahmed.veil.models.ModelStore
import com.owaisahmed.veil.pipeline.Pipeline
import kotlinx.coroutines.launch

/**
 * One model: name, size and state, with download (progress, resumable, cancellable) or delete.
 * Downloads run in [ModelDownloads], so they continue when this row leaves the screen.
 * [selected]/[onSelect] add a radio button, for the translation engines.
 */
@Composable
fun ModelRow(
    spec: ModelSpec,
    title: String,
    detail: String,
    selected: Boolean? = null,
    onSelect: () -> Unit = {},
) {
    val context = LocalContext.current
    val store = remember { ModelStore(context) }
    val scope = rememberCoroutineScope()
    val states by ModelDownloads.states.collectAsState()
    val state = states[spec.id] ?: ModelDownloads.State()
    var installed by remember(spec) { mutableStateOf(store.isInstalled(spec)) }
    var confirmDelete by remember(spec) { mutableStateOf(false) }
    val mb = "%.0f MB".format(spec.totalBytes / 1e6)

    // A download that finished (here or while the screen was closed) shows as installed.
    LaunchedEffect(state.running) {
        if (!state.running) installed = store.isInstalled(spec)
    }

    Column {
        ListItem(
            modifier = if (selected != null) Modifier.clickable(onClick = onSelect) else Modifier,
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = selected?.let { { RadioButton(selected = it, onClick = onSelect) } },
            headlineContent = { Text(title) },
            supportingContent = {
                Text(
                    when {
                        state.running -> "Downloading… ${(state.progress * 100).toInt()} % of $mb"
                        installed -> "$detail · $mb on this phone"
                        else -> "$detail · $mb download"
                    }
                )
            },
            trailingContent = {
                when {
                    installed && !state.running -> TextButton(onClick = { confirmDelete = true }) { Text("Delete") }
                    state.running -> OutlinedButton(onClick = { ModelDownloads.cancel(spec) }) { Text("Cancel") }
                    else -> FilledTonalButton(onClick = { ModelDownloads.start(store, spec) }) {
                        Text(if (state.progress > 0f) "Resume" else "Get")
                    }
                }
            },
        )
        if (state.running) {
            LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        }
        state.error?.let {
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
                        ModelDownloads.reset(spec)
                        installed = false
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep") } },
        )
    }
}
