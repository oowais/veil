package com.owaisahmed.veil.ui

import android.net.ConnectivityManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
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
import com.owaisahmed.veil.models.ModelImport
import com.owaisahmed.veil.models.ModelSpec
import com.owaisahmed.veil.models.ModelStore
import com.owaisahmed.veil.pipeline.Pipeline
import kotlinx.coroutines.launch

/**
 * One model: name, size and state, with download (progress, resumable, cancellable), import from
 * files, or delete. Downloading on a metered network (mobile data) asks first (D-055).
 * Downloads run in [ModelDownloads], so they continue when this row leaves the screen.
 * [selected]/[onSelect] add a radio button, for the translation engines; it can only be picked
 * once the model is on the phone. [onInstalledChange] reports downloads, imports and deletes.
 */
@Composable
fun ModelRow(
    spec: ModelSpec,
    title: String,
    detail: String,
    badge: String? = null,
    selected: Boolean? = null,
    onSelect: () -> Unit = {},
    onInstalledChange: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val store = remember { ModelStore(context) }
    val scope = rememberCoroutineScope()
    val states by ModelDownloads.states.collectAsState()
    val state = states[spec.id] ?: ModelDownloads.State()
    var installed by remember(spec) { mutableStateOf(store.isInstalled(spec)) }
    var confirmDelete by remember(spec) { mutableStateOf(false) }
    var confirmMetered by remember(spec) { mutableStateOf(false) }
    var importing by remember(spec) { mutableStateOf(false) }
    var importMessage by remember(spec) { mutableStateOf<String?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        importing = true
        importMessage = null
        scope.launch {
            val r = ModelImport.import(context, store, spec, uris)
            installed = store.isInstalled(spec)
            importing = false
            importMessage = buildString {
                append(if (installed) "Imported, ready to use." else "Imported ${r.imported} of ${spec.files.size} files.")
                if (r.skipped.isNotEmpty()) append(" Skipped: ").append(r.skipped.joinToString(", "))
            }
        }
    }
    fun download() {
        val metered = context.getSystemService(ConnectivityManager::class.java).isActiveNetworkMetered
        if (metered) confirmMetered = true else ModelDownloads.start(store, spec)
    }
    val mb = "%.0f MB".format(spec.totalBytes / 1e6)

    // A download that finished (here or while the screen was closed) shows as installed.
    LaunchedEffect(state.running) {
        if (!state.running) installed = store.isInstalled(spec)
    }
    LaunchedEffect(installed) { onInstalledChange(installed) }

    Column {
        ListItem(
            modifier = if (selected != null) Modifier.clickable(enabled = installed, onClick = onSelect) else Modifier,
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = selected?.let { { RadioButton(selected = it, onClick = onSelect, enabled = installed) } },
            headlineContent = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // The title gives way, so the badge never wraps letter by letter.
                    Text(title, Modifier.weight(1f, fill = false))
                    badge?.let { Badge(it) }
                }
            },
            supportingContent = {
                Text(
                    when {
                        importing -> "Importing…"
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
                    importing -> {}
                    else -> FilledTonalButton(onClick = ::download) {
                        Text(if (state.progress > 0f) "Resume" else "Get")
                    }
                }
            },
        )
        if (state.running) {
            LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        }
        if (!installed && !state.running && !importing) {
            TextButton(onClick = { pick.launch(arrayOf("*/*")) }, modifier = Modifier.padding(start = 8.dp)) {
                Text("Import from files")
            }
        }
        importMessage?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
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

    if (confirmMetered) {
        AlertDialog(
            onDismissRequest = { confirmMetered = false },
            title = { Text("Download on mobile data?") },
            text = { Text("You're not on Wi-Fi. $title is $mb.") },
            confirmButton = {
                TextButton(onClick = { confirmMetered = false; ModelDownloads.start(store, spec) }) { Text("Download") }
            },
            dismissButton = { TextButton(onClick = { confirmMetered = false }) { Text("Wait for Wi-Fi") } },
        )
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
