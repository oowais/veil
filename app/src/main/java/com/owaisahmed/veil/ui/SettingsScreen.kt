package com.owaisahmed.veil.ui

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.owaisahmed.veil.BuildConfig
import com.owaisahmed.veil.pipeline.Appearance
import com.owaisahmed.veil.pipeline.Glossary
import com.owaisahmed.veil.pipeline.PipelineLog
import com.owaisahmed.veil.pipeline.TextSource
import com.owaisahmed.veil.update.UpdateCheck
import com.owaisahmed.veil.update.Updater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Debug/profile builds have their own app id (D-028), so a release APK can't update them. */
val isDevBuild = BuildConfig.BUILD_TYPE != "release"

private const val REPO = "https://github.com/oowais/veil"

@Composable
fun SettingsScreen(latest: UpdateCheck.Latest?, onLatest: (UpdateCheck.Latest?) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        ReadingSection()
        BoxesSection()
        GlossarySection()
        UpdatesSection(latest, onLatest)
        Section("Privacy") {
            Text(
                "Reading the screen, recognising text and translating all happen on this phone. Saved " +
                    "translations (History) and the activity log stay on this phone too. Veil connects to the internet only to " +
                    "download models you pick and, if turned on, to check for updates.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        LogSection()
        AboutSection()
    }
}

@Composable
private fun ReadingSection() {
    val context = LocalContext.current
    var source by remember { mutableStateOf(TextSource.selected(context)) }
    Section("Reading the screen") {
        TextSource.entries.forEach { s ->
            ListItem(
                modifier = Modifier.clickable { source = s; TextSource.select(context, s) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                leadingContent = { RadioButton(selected = source == s, onClick = { source = s; TextSource.select(context, s) }) },
                headlineContent = { Text(s.title) },
                supportingContent = { Text(s.description) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BoxesSection() {
    val context = LocalContext.current
    var size by remember { mutableStateOf(Appearance.textSize(context)) }
    var opacity by remember { mutableFloatStateOf(Appearance.opacity(context)) }
    Section("Translation boxes") {
        Text("Text size", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Appearance.TextSize.entries.forEachIndexed { i, t ->
                SegmentedButton(
                    selected = size == t,
                    onClick = { size = t; Appearance.setTextSize(context, t) },
                    shape = SegmentedButtonDefaults.itemShape(i, Appearance.TextSize.entries.size),
                ) { Text(t.title) }
            }
        }
        Text(
            "Cover over the original: ${(opacity * 100).roundToInt()} %",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
        )
        Slider(
            value = opacity,
            onValueChange = { opacity = it },
            onValueChangeFinished = { Appearance.setOpacity(context, opacity) },
            valueRange = 0.2f..0.9f,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Hint(
            "Lower shows more of the blurred original behind the English. Android 11 can't blur, so " +
                "the boxes stay nearly solid there.",
            Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun GlossarySection() {
    val context = LocalContext.current
    var text by remember { mutableStateOf(Glossary.text(context)) }
    var saved by remember { mutableStateOf(text) }
    Section("Glossary") {
        Hint(
            "Your own translations, one per line: German = English. Only Hy-MT2 uses them; OPUS-MT " +
                "can't follow instructions.",
            Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text("Sitzung = session\nAnmeldung = registration") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        val count = Glossary.parse(text).size
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Hint("$count ${if (count == 1) "term" else "terms"}", Modifier.padding(horizontal = 8.dp).weight(1f))
            TextButton(enabled = text != saved, onClick = { Glossary.setText(context, text); saved = text }) {
                Text(if (text == saved) "Saved" else "Save")
            }
        }
    }
}

@Composable
private fun UpdatesSection(latest: UpdateCheck.Latest?, onLatest: (UpdateCheck.Latest?) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var daily by remember { mutableStateOf(UpdateCheck.dailyCheck(context)) }
    var checking by remember { mutableStateOf(false) }
    var installing by remember { mutableStateOf(false) }
    /** 0–1, or null while the size is unknown. */
    var progress by remember { mutableStateOf<Float?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    val current = BuildConfig.VERSION_NAME
    val available = latest?.takeIf { UpdateCheck.newer(it.version, current) }

    Section("Updates") {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            headlineContent = { Text("Veil $current${if (isDevBuild) " (dev build)" else ""}") },
            supportingContent = {
                Text(
                    when {
                        isDevBuild -> "Dev builds can't update themselves"
                        available != null && available.apkUrl == null -> "Veil ${available.version} is being built, check again soon"
                        available != null -> "Veil ${available.version} is available"
                        latest != null -> "Up to date · checked ${DateUtils.getRelativeTimeSpanString(latest.checkedAt)}"
                        else -> "Not checked yet"
                    }
                )
            },
        )

        if (available?.apkUrl != null && !isDevBuild) {
            Button(
                enabled = !installing,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                onClick = {
                    message = null
                    if (!Updater.canInstall(context)) {
                        Updater.openInstallSettings(context)
                        message = "Allow Veil to install apps, then tap Update again."
                        return@Button
                    }
                    installing = true
                    progress = null
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                Updater.downloadAndInstall(context, available.apkUrl) { done, total ->
                                    progress = if (total > 0) done.toFloat() / total else null
                                }
                            }
                        } catch (e: Exception) {
                            message = "Update failed: ${e.message}"
                        } finally {
                            installing = false
                        }
                    }
                },
            ) { Text(if (installing) "Downloading…" else "Update to ${available.version}") }
            if (installing) {
                val p = progress
                val m = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                if (p != null) LinearProgressIndicator(progress = { p }, modifier = m) else LinearProgressIndicator(m)
            }
        }

        ListItem(
            modifier = Modifier.clickable { daily = !daily; UpdateCheck.setDailyCheck(context, daily) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            headlineContent = { Text("Check once a day") },
            supportingContent = { Text("Asks GitHub for the latest release. GitHub sees your IP address, nothing else is sent.") },
            trailingContent = {
                Switch(checked = daily, onCheckedChange = { daily = it; UpdateCheck.setDailyCheck(context, it) })
            },
        )

        TextButton(
            enabled = !checking && !installing,
            modifier = Modifier.padding(horizontal = 8.dp),
            onClick = {
                message = null
                checking = true
                scope.launch {
                    try {
                        val result = UpdateCheck.check(context, force = true)
                        onLatest(result)
                        if (result != null && !UpdateCheck.newer(result.version, current)) message = "Veil is up to date."
                    } catch (e: Exception) {
                        message = "Check failed: ${e.message}"
                    } finally {
                        checking = false
                    }
                }
            },
        ) { Text(if (checking) "Checking…" else "Check now") }

        message?.let { Hint(it, Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
    }
}

/** What the last swipes did (D-030), for bug reports: collapsed until asked for. */
@Composable
private fun LogSection() {
    val context = LocalContext.current
    val log by PipelineLog.lines.collectAsState()
    var expanded by remember { mutableStateOf(false) }

    Section("Troubleshooting") {
        ListItem(
            modifier = Modifier.clickable(enabled = log.isNotEmpty()) { expanded = !expanded },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            headlineContent = { Text("Activity log") },
            supportingContent = {
                Text(if (log.isEmpty()) "Empty. It fills when you swipe." else "${log.size} lines · tap to ${if (expanded) "hide" else "show"}")
            },
        )
        if (log.isNotEmpty()) {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { copyWithDeviceInfo(context, "Veil log", log.joinToString("\n")) }) { Text("Copy") }
                OutlinedButton(onClick = { PipelineLog.clear(); expanded = false }) { Text("Clear") }
            }
        }
        if (expanded) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                log.asReversed().forEach {
                    Text(it, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun AboutSection() {
    val context = LocalContext.current
    Section("About") {
        Link("Source code", "Open source, Apache-2.0") { SystemSettings.openUrl(context, REPO) }
        Link("Model licenses", "Where each model comes from") { SystemSettings.openUrl(context, "$REPO/blob/main/MODELS.md") }
        Link("Release notes", "What changed in each version") { SystemSettings.openUrl(context, "$REPO/releases") }
    }
}

@Composable
private fun Link(title: String, detail: String, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = { Text(title) },
        supportingContent = { Text(detail) },
    )
}
