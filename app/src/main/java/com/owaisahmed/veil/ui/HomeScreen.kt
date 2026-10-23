package com.owaisahmed.veil.ui

import androidx.annotation.DrawableRes
import android.net.ConnectivityManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.owaisahmed.veil.R
import com.owaisahmed.veil.capture.VeilAccessibilityService
import com.owaisahmed.veil.models.ModelDownloads
import com.owaisahmed.veil.models.ModelRegistry
import com.owaisahmed.veil.models.ModelStore
import com.owaisahmed.veil.pipeline.Engine
import com.owaisahmed.veil.pipeline.Mode
import com.owaisahmed.veil.trigger.TriggerController
import kotlinx.coroutines.delay

/** Status at a glance: what the gesture still needs, what it does, and how to use the result. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onOpenModels: () -> Unit, onOpenHistory: () -> Unit, modifier: Modifier = Modifier) {
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
        mode = Mode.selected(context) // may have been switched on the overlay's toolbar
        model = store.isInstalled(engine.value.spec)
        ocr = store.isInstalled(ModelRegistry.ocrSmall)
        onPauseOrDispose { }
    }
    // Enabled in settings isn't enough: after an update Android sometimes doesn't start the services
    // again (D-047). Give it a moment after the app opens before calling it stuck.
    val assistantRunning by TriggerController.assistantConnected.collectAsState()
    val a11yRunning by VeilAccessibilityService.connected.collectAsState()
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(2_000)
        settled = true
    }
    val assistantStuck = assistant && !assistantRunning && settled
    val a11yStuck = a11y && !a11yRunning && settled

    // Copy text mode doesn't translate, so it doesn't need a translation model.
    val needsModel = mode == Mode.TRANSLATE
    val missing = listOf(assistant && !assistantStuck, a11y && !a11yStuck, model || !needsModel).count { !it }
    // Once everything is set up, the checklist folds into the status card (D-066).
    var showSetup by rememberSaveable { mutableStateOf(false) }

    // The recommended engine can be downloaded right here (D-066); when it's done and the selected
    // engine isn't there, it becomes the selected one.
    val recommended = Engine.entries.first()
    val downloads by ModelDownloads.states.collectAsState()
    val download = downloads[recommended.spec.id]
    LaunchedEffect(download?.running) {
        if (download?.running != false) return@LaunchedEffect
        val store = ModelStore(context)
        if (store.isInstalled(recommended.spec) && !store.isInstalled(engine.value.spec)) {
            Engine.select(context, recommended)
            engine.value = recommended
        }
        model = store.isInstalled(engine.value.spec)
    }
    fun getRecommended() {
        // On mobile data, Models asks first.
        if (context.getSystemService(ConnectivityManager::class.java).isActiveNetworkMetered) onOpenModels()
        else ModelDownloads.start(ModelStore(context), recommended.spec)
    }

    Column(
        modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        val ready = missing == 0
        StatusCard(ready = ready, missing = missing, setupShown = showSetup, onToggleSetup = { showSetup = !showSetup })

        if (!ready || showSetup) Section("Setup") {
            Step(
                done = assistant && !assistantStuck,
                title = "Digital assistant",
                detail = when {
                    !assistant -> "Pick Veil as the default digital assistant app"
                    assistantStuck -> "Chosen, but Android hasn't started it. Pick another assistant, then Veil again."
                    else -> "The corner swipe starts Veil"
                },
                action = "Open" to { SystemSettings.openAssistant(context) },
                warning = assistantStuck,
            )
            Step(
                done = a11y && !a11yStuck,
                title = "Accessibility service",
                detail = when {
                    !a11y -> "Lets Veil read on-screen text and draw on top. Nothing leaves your phone."
                    a11yStuck -> "Switched on, but not running. Turn Veil off and on again."
                    else -> "Veil can read the screen and show results"
                },
                action = "Open" to { SystemSettings.openAccessibility(context) },
                warning = a11yStuck,
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
                    download?.running == true ->
                        "Downloading ${recommended.title}… ${(download.progress * 100).toInt()} % of %.0f MB".format(recommended.spec.totalBytes / 1e6)
                    download?.error != null -> "Download failed: ${download.error}. Try again."
                    else -> "${recommended.title}: ${recommended.detail}"
                },
                action = if (download?.running == true) null
                else "Get %.0f MB".format(recommended.spec.totalBytes / 1e6) to ::getRecommended,
                secondary = "Others" to onOpenModels,
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
            Hint("You can also switch on the toolbar after swiping.", Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        }

        Section("Translations") {
            ListItem(
                modifier = Modifier.clickable(onClick = onOpenHistory),
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                leadingContent = { Icon(painterResource(R.drawable.ic_history), contentDescription = null) },
                headlineContent = { Text("History") },
                supportingContent = { Text("Everything Veil translated, with app and time") },
            )
        }

        Section("Tips") {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    "Swipe up diagonally from a bottom corner in any app.",
                    "No gesture? Add the Veil tile to Quick Settings, or open veil://translate from Tasker or Key Mapper.",
                    "Tap a box to see the app's own text under it, tap again for the English. Long-press copies.",
                    "Drag the toolbar out of the way; it remembers where you put it.",
                    "Back or a tap outside removes the translation.",
                ).forEach { Text("•  $it", style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}

@Composable
private fun StatusCard(ready: Boolean, missing: Int, setupShown: Boolean, onToggleSetup: () -> Unit) {
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
        if (ready) {
            TextButton(
                onClick = onToggleSetup,
                colors = ButtonDefaults.textButtonColors(contentColor = colors.onPrimaryContainer),
                modifier = Modifier.padding(start = 56.dp, bottom = 8.dp),
            ) {
                Text(if (setupShown) "Hide setup" else "Show setup")
            }
        }
    }
}

@Composable
private fun Step(
    done: Boolean,
    title: String,
    detail: String,
    action: Pair<String, () -> Unit>?,
    secondary: Pair<String, () -> Unit>? = null,
    optional: Boolean = false,
    warning: Boolean = false,
) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            StepIcon(
                if (done) R.drawable.ic_check_circle else R.drawable.ic_circle,
                when {
                    done -> MaterialTheme.colorScheme.primary
                    warning -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.outline
                },
            )
        },
        headlineContent = { Text(if (optional && !done) "$title (optional)" else title) },
        supportingContent = { Text(detail, color = if (warning) MaterialTheme.colorScheme.error else Color.Unspecified) },
        trailingContent = if (done || action == null) null else ({
            Column(horizontalAlignment = Alignment.End) {
                FilledTonalButton(onClick = action.second) { Text(action.first) }
                secondary?.let { (label, onClick) -> TextButton(onClick = onClick) { Text(label) } }
            }
        }),
    )
}

@Composable
private fun StepIcon(@DrawableRes icon: Int, tint: Color) {
    Icon(painterResource(icon), contentDescription = null, tint = tint)
}
