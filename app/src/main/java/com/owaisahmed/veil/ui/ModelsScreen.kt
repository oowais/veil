package com.owaisahmed.veil.ui

import android.app.ActivityManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.owaisahmed.veil.models.ModelRegistry
import com.owaisahmed.veil.models.ModelStore
import com.owaisahmed.veil.pipeline.Engine
import com.owaisahmed.veil.pipeline.OcrSettings

/** Pick the translation engine; download or delete models. Everything runs offline once downloaded. */
@Composable
fun ModelsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var engine by remember { mutableStateOf(Engine.selected(context)) }
    // Hy-MT2 needs ~2.5 GB while loaded; on phones under 6 GB Android may close Veil for it (D-056).
    val ramGb = remember {
        ActivityManager.MemoryInfo().also { context.getSystemService(ActivityManager::class.java).getMemoryInfo(it) }.totalMem / 1e9
    }
    var labelsApart by remember { mutableStateOf(OcrSettings.keepLabelsApart(context)) }
    val store = remember { ModelStore(context) }
    // Only a downloaded engine can be selected: if the selected one isn't (deleted, or never
    // finished), move to the first one that is.
    fun keepSelectionUsable() {
        if (store.isInstalled(engine.spec)) return
        Engine.entries.firstOrNull { store.isInstalled(it.spec) }?.let { engine = it; Engine.select(context, it) }
    }

    Column(
        modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Section("Translation") {
            Hint("The swipe uses the selected engine.", Modifier.padding(horizontal = 16.dp))
            Engine.entries.forEach { e ->
                ModelRow(
                    e.spec, e.title,
                    if (e == Engine.HY_MT2 && ramGb < 6) "${e.detail} · this phone has %.0f GB, may be too little".format(ramGb) else e.detail,
                    selected = engine == e,
                    onSelect = { engine = e; Engine.select(context, e) },
                    onInstalledChange = { keepSelectionUsable() },
                )
            }
        }

        Section("Text in pictures") {
            ModelRow(ModelRegistry.ocrSmall, "Text recognition", "reads text inside images, adds about 2 s")
            ListItem(
                modifier = Modifier.clickable { labelsApart = !labelsApart; OcrSettings.setKeepLabelsApart(context, labelsApart) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = { Text("Keep short lines apart") },
                supportingContent = {
                    Text(
                        "Buttons, menu entries and headings get their own box. Turn off if text in narrow " +
                            "columns is cut into pieces."
                    )
                },
                trailingContent = {
                    Switch(checked = labelsApart, onCheckedChange = { labelsApart = it; OcrSettings.setKeepLabelsApart(context, it) })
                },
            )
        }

        Hint(
            "Models are stored on this phone and work without internet. Licenses and sources: Settings → About.",
            Modifier.padding(horizontal = 4.dp),
        )
    }
}
