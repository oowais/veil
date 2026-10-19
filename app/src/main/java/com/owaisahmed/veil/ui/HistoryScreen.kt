package com.owaisahmed.veil.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.owaisahmed.veil.R
import com.owaisahmed.veil.history.HistoryItem
import com.owaisahmed.veil.history.TranslationStore
import com.owaisahmed.veil.pipeline.Pipeline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

/** Everything translated, newest first, grouped by day (D-049). Delete one or all. */
@Composable
fun HistoryScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val store = remember { TranslationStore.get(context) }
    val scope = rememberCoroutineScope()
    val version by store.version.collectAsState()
    val items by produceState<List<HistoryItem>?>(null, version) {
        value = withContext(Dispatchers.IO) { store.history() }
    }
    var saving by remember { mutableStateOf(store.saving) }
    var confirmAll by remember { mutableStateOf(false) }
    val labels = remember { AppLabels(context) }

    fun delete(item: HistoryItem) = scope.launch {
        withContext(Dispatchers.IO) { store.delete(item) }
        Pipeline.forgetTranslations()
    }

    LazyColumn(
        modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Section("Saving") {
                ListItem(
                    modifier = Modifier.clickable { saving = !saving; store.saving = saving },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text("Save translations") },
                    supportingContent = {
                        Text("Kept only on this phone. A text Veil has seen before then shows up at once, also after a restart.")
                    },
                    trailingContent = { Switch(checked = saving, onCheckedChange = { saving = it; store.saving = it }) },
                )
                if (!items.isNullOrEmpty()) {
                    TextButton(onClick = { confirmAll = true }, modifier = Modifier.padding(horizontal = 8.dp)) {
                        Text("Delete all", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        val list = items
        when {
            list == null -> Unit
            list.isEmpty() -> item {
                Hint(
                    if (saving) "Nothing translated yet. Translations appear here after you swipe."
                    else "Saving is off, so nothing new is kept.",
                    Modifier.padding(16.dp),
                )
            }
            else -> list.groupBy { dayStart(it.at) }.forEach { (day, dayItems) ->
                item(key = "day-$day") {
                    Text(
                        dayLabel(context, day),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 4.dp),
                    )
                }
                items(dayItems, key = { it.id }) { item ->
                    HistoryCard(
                        item,
                        appLabel = item.app?.let(labels::label),
                        onCopy = { copy(context, item.translation) },
                        onDelete = { delete(item) },
                    )
                }
            }
        }
    }

    if (confirmAll) {
        AlertDialog(
            onDismissRequest = { confirmAll = false },
            title = { Text("Delete all history?") },
            text = { Text("Removes every saved translation from this phone. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmAll = false
                    scope.launch {
                        withContext(Dispatchers.IO) { store.deleteAll() }
                        Pipeline.forgetTranslations()
                    }
                }) { Text("Delete all", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmAll = false }) { Text("Keep") } },
        )
    }
}

@Composable
private fun HistoryCard(item: HistoryItem, appLabel: String?, onCopy: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    val lines = if (expanded) Int.MAX_VALUE else 3
    Card(
        Modifier.fillMaxWidth().animateContentSize().clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(start = 16.dp, top = 12.dp, end = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                item.translation,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = lines,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = 12.dp),
            )
            Text(
                item.source,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = lines,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = 12.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    listOfNotNull(appLabel, DateFormat.getTimeFormat(context).format(item.at)).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onCopy) { Icon(painterResource(R.drawable.ic_copy), contentDescription = "Copy English") }
                IconButton(onClick = onDelete) { Icon(painterResource(R.drawable.ic_delete), contentDescription = "Delete") }
            }
        }
    }
}

private fun copy(context: Context, text: String) =
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Veil", text))

private fun dayStart(at: Long): Long = Calendar.getInstance().apply {
    timeInMillis = at
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun dayLabel(context: Context, day: Long): String = when {
    DateUtils.isToday(day) -> "Today"
    DateUtils.isToday(day + DateUtils.DAY_IN_MILLIS) -> "Yesterday"
    else -> DateUtils.formatDateTime(context, day, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_WEEKDAY)
}

/** App names for package names, looked up once each; the package name if the app is gone. */
private class AppLabels(private val context: Context) {
    private val cache = HashMap<String, String>()

    fun label(pkg: String): String = cache.getOrPut(pkg) {
        runCatching {
            val pm = context.packageManager
            @Suppress("DEPRECATION") // the flags overload needs Android 13
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(pkg)
    }
}
