package com.owaisahmed.veil.pipeline

import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.owaisahmed.veil.capture.VeilAccessibilityService
import com.owaisahmed.veil.models.ModelRegistry
import com.owaisahmed.veil.models.ModelStore
import com.owaisahmed.veil.spikes.CopyButton

/** Veil tab (milestone 1): the three things the gesture needs, and what the last triggers did. */
@Composable
fun SetupScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var assistant by remember { mutableStateOf(false) }
    var a11y by remember { mutableStateOf(false) }
    var engine by remember { mutableStateOf(Engine.selected(context)) }
    var model by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf(Mode.selected(context)) }
    var ocrModel by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        assistant = context.getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_ASSISTANT)
        a11y = accessibilityEnabled(context)
        model = ModelStore(context).isInstalled(engine.spec)
        ocrModel = ModelStore(context).isInstalled(ModelRegistry.ocrSmall)
        onPauseOrDispose { }
    }
    val log by PipelineLog.lines.collectAsState()

    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Swipe up from a bottom corner in any app. Tap a box to see the original, long-press to copy it. Back, a tap outside or the Close button removes it.")

        Text("When I swipe", style = MaterialTheme.typography.titleMedium)
        Mode.entries.forEach { m ->
            Row(
                Modifier.selectable(selected = mode == m, onClick = { mode = m; Mode.select(context, m) }),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = mode == m, onClick = null)
                Text(m.label)
            }
        }
        HorizontalDivider()

        Step(1, "Veil is the digital assistant", assistant)
        if (!assistant) {
            Button(onClick = {
                open(context, Settings.ACTION_VOICE_INPUT_SETTINGS, Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
            }) { Text("Open assistant settings") }
        }

        Step(2, "Accessibility service is on", a11y)
        if (!a11y) {
            Text("Settings → Accessibility → Veil → turn on. Veil uses it to read on-screen text and draw the translation; nothing leaves the phone.")
            Text(
                "If Android says \"Restricted setting\": open App info, tap ⋮ (top right) → Allow restricted settings, then try again.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { open(context, Settings.ACTION_ACCESSIBILITY_SETTINGS) }) { Text("Accessibility") }
                OutlinedButton(onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                    )
                }) { Text("App info") }
            }
        }

        Step(3, "Translation model ready", model)
        Text("Pick the engine the gesture uses. Download or delete models any time.", style = MaterialTheme.typography.bodySmall)
        Engine.entries.forEach { e ->
            ModelRow(
                e.spec, e.title, e.detail,
                selected = engine == e,
                onSelect = {
                    engine = e
                    Engine.select(context, e)
                    model = ModelStore(context).isInstalled(e.spec)
                },
                onInstalledChange = { if (engine == e) model = it },
            )
        }

        Step(4, "Text in images (optional)", ocrModel)
        ModelRow(
            ModelRegistry.ocrSmall, "Text recognition (OCR)", "reads text inside pictures, adds about 2 s",
            onInstalledChange = { ocrModel = it },
        )

        HorizontalDivider()
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CopyButton("Veil log", enabled = log.isNotEmpty()) { "Veil log\n" + log.joinToString("\n") }
            OutlinedButton(enabled = log.isNotEmpty(), onClick = PipelineLog::clear) { Text("Clear") }
        }
        if (log.isEmpty()) Text("No triggers yet.", style = MaterialTheme.typography.bodySmall)
        log.asReversed().forEach {
            Text(it, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun Step(n: Int, title: String, done: Boolean) {
    Text("${if (done) "✓" else "$n."}  $title", style = MaterialTheme.typography.titleMedium)
}

private fun accessibilityEnabled(context: Context): Boolean {
    val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
    val cn = ComponentName(context, VeilAccessibilityService::class.java)
    return enabled.split(':').any { it.equals(cn.flattenToString(), true) || it.equals(cn.flattenToShortString(), true) }
}

/** Opens the first settings screen this phone has. */
private fun open(context: Context, vararg actions: String) {
    for (action in listOf(*actions, Settings.ACTION_SETTINGS)) {
        try {
            context.startActivity(Intent(action))
            return
        } catch (_: ActivityNotFoundException) {
        }
    }
}
