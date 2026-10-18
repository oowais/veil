package com.owaisahmed.veil.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.owaisahmed.veil.models.ModelRegistry
import com.owaisahmed.veil.pipeline.Engine

/** Pick the translation engine; download or delete models. Everything runs offline once downloaded. */
@Composable
fun ModelsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var engine by remember { mutableStateOf(Engine.selected(context)) }

    Column(
        modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Section("Translation") {
            Hint("The swipe uses the selected engine.", Modifier.padding(horizontal = 16.dp))
            Engine.entries.forEach { e ->
                ModelRow(
                    e.spec, e.title, e.detail,
                    selected = engine == e,
                    onSelect = { engine = e; Engine.select(context, e) },
                )
            }
        }

        Section("Text in pictures") {
            ModelRow(ModelRegistry.ocrSmall, "Text recognition", "reads text inside images, adds about 2 s")
        }

        Hint(
            "Models are stored on this phone and work without internet. Licenses and sources: Settings → About.",
            Modifier.padding(horizontal = 4.dp),
        )
    }
}
