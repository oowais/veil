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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    var model by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        assistant = context.getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_ASSISTANT)
        a11y = accessibilityEnabled(context)
        model = ModelStore(context).isInstalled(ModelRegistry.hyMt2Q4)
        onPauseOrDispose { }
    }
    val log by PipelineLog.lines.collectAsState()

    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Swipe up from a bottom corner in any app to translate German on screen. Do it again to remove the translation.")

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

        Step(3, "Translation model downloaded", model)
        if (!model) Text("Download Hy-MT2 on the Translate tab (~1.1 GB, once).")

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
