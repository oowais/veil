package com.owaisahmed.veil.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.owaisahmed.veil.R
import com.owaisahmed.veil.models.ModelRegistry
import com.owaisahmed.veil.models.ModelStore
import com.owaisahmed.veil.pipeline.Engine
import com.owaisahmed.veil.pipeline.Mode

/** Status at a glance: what the gesture still needs, what it does, and how to use the result. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onOpenModels: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var assistant by remember { mutableStateOf(false) }
    var a11y by remember { mutableStateOf(false) }
    var model by remember { mutableStateOf(false) }
    var ocr by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf(Mode.selected(context)) }
    val engine = remember { mutableStateOf(Engine.selected(context)) }
    LifecycleResumeEffect(Unit) {
        val store = ModelStore(context)
        assistant = SystemSettings.isAssistant(context)
        a11y = SystemSettings.isAccessibilityOn(context)
        engine.value = Engine.selected(context)
        model = store.isInstalled(engine.value.spec)
        ocr = store.isInstalled(ModelRegistry.ocrSmall)
        onPauseOrDispose { }
    }
    // Copy text mode doesn't translate, so it doesn't need a translation model.
    val needsModel = mode == Mode.TRANSLATE
    val missing = listOf(assistant, a11y, model || !needsModel).count { !it }

    Column(
        modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        StatusCard(ready = missing == 0, missing = missing)

        Section("Setup") {
            Step(
                done = assistant,
                title = "Digital assistant",
                detail = if (assistant) "The corner swipe starts Veil" else "Pick Veil as the default digital assistant app",
                action = "Open" to { SystemSettings.openAssistant(context) },
            )
            Step(
                done = a11y,
                title = "Accessibility service",
                detail = if (a11y) "Veil can read the screen and show results"
                else "Lets Veil read on-screen text and draw on top. Nothing leaves your phone.",
                action = "Open" to { SystemSettings.openAccessibility(context) },
            )
            if (!a11y) {
                Row(Modifier.padding(start = 56.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Hint("Blocked as \"Restricted setting\"? App info → ⋮ → Allow restricted settings.", Modifier.weight(1f))
                    TextButton(onClick = { SystemSettings.openAppInfo(context) }) { Text("App info") }
                }
            }
            Step(
                done = model || !needsModel,
                title = "Translation model",
                detail = when {
                    !needsModel -> "Not needed for Copy text"
                    model -> engine.value.title
                    else -> "Download one on the Models screen"
                },
                action = "Models" to onOpenModels,
            )
            Step(
                done = ocr,
                title = "Text in pictures",
                detail = if (ocr) "Text inside images is read too" else "Optional: also read text inside images",
                action = "Models" to onOpenModels,
                optional = true,
            )
        }

        Section("When I swipe") {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Mode.entries.forEachIndexed { i, m ->
                    SegmentedButton(
                        selected = mode == m,
                        onClick = { mode = m; Mode.select(context, m) },
                        shape = SegmentedButtonDefaults.itemShape(i, Mode.entries.size),
                    ) { Text(m.title) }
                }
            }
            Hint(mode.description, Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        }

        Section("Tips") {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    "Swipe up diagonally from a bottom corner in any app.",
                    "Tap a box to see the German, long-press to copy it.",
                    "Drag the toolbar out of the way; it remembers where you put it.",
                    "Back, a tap outside or Close removes the translation.",
                ).forEach { Text("•  $it", style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}

@Composable
private fun StatusCard(ready: Boolean, missing: Int) {
    val colors = MaterialTheme.colorScheme
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (ready) colors.primaryContainer else colors.tertiaryContainer,
            contentColor = if (ready) colors.onPrimaryContainer else colors.onTertiaryContainer,
        ),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(painterResource(R.drawable.ic_translate), contentDescription = null, Modifier.size(32.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (ready) "Ready" else "Almost ready", style = MaterialTheme.typography.titleLarge)
                Text(
                    if (ready) "Swipe up from a bottom corner in any app."
                    else "$missing ${if (missing == 1) "step" else "steps"} left below.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun Step(done: Boolean, title: String, detail: String, action: Pair<String, () -> Unit>, optional: Boolean = false) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            StepIcon(
                if (done) R.drawable.ic_check_circle else R.drawable.ic_circle,
                if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            )
        },
        headlineContent = { Text(if (optional && !done) "$title (optional)" else title) },
        supportingContent = { Text(detail) },
        trailingContent = if (done) null else ({ FilledTonalButton(onClick = action.second) { Text(action.first) } }),
    )
}

@Composable
private fun StepIcon(@DrawableRes icon: Int, tint: Color) {
    Icon(painterResource(icon), contentDescription = null, tint = tint)
}
