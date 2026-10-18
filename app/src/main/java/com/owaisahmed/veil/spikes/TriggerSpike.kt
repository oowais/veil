package com.owaisahmed.veil.spikes

import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.owaisahmed.veil.trigger.TriggerController
import com.owaisahmed.veil.trigger.TriggerEvent

/** Spike a: is Veil selectable as the assistant, and does the corner swipe reach us? */
@Composable
fun TriggerSpike(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val roleManager = remember { context.getSystemService(RoleManager::class.java) }
    var held by remember { mutableStateOf(roleManager.isRoleHeld(RoleManager.ROLE_ASSISTANT)) }
    // ROLE_ASSISTANT is not requestable (the request is cancelled without a dialog), so send the
    // user to the system's default-assistant screen instead.
    val openSettings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        held = roleManager.isRoleHeld(RoleManager.ROLE_ASSISTANT)
    }
    fun openAssistantSettings() {
        val candidates = listOf(
            Settings.ACTION_VOICE_INPUT_SETTINGS, // "Digital assistant app" on Pixel
            Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS,
            Settings.ACTION_SETTINGS,
        )
        for (action in candidates) {
            try {
                openSettings.launch(Intent(action))
                return
            } catch (_: ActivityNotFoundException) {
            }
        }
    }
    LifecycleResumeEffect(Unit) {
        held = roleManager.isRoleHeld(RoleManager.ROLE_ASSISTANT)
        onPauseOrDispose { }
    }

    val events = remember { mutableStateListOf<TriggerEvent>() }
    LaunchedEffect(Unit) {
        TriggerController.events.collect { events.add(0, it) }
    }
    var delay by remember { mutableFloatStateOf(TriggerController.captureDelayMs.toFloat()) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            if (held) "Veil is the default assistant." else "Veil is not the default assistant.",
            style = MaterialTheme.typography.titleMedium,
        )
        Button(onClick = ::openAssistantSettings) {
            Text(if (held) "Open assistant settings" else "Set as assistant")
        }
        Text("In settings: Default digital assistant app → Veil. Then swipe up from a bottom corner (or long-press power, if set to assistant). Events show below and in logcat tag VeilTrigger.")

        Text("Capture delay after hide: ${delay.toInt()} ms")
        Slider(
            value = delay,
            onValueChange = { delay = it; TriggerController.captureDelayMs = it.toLong() },
            valueRange = 0f..600f,
            steps = 11,
        )

        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(events) { e -> Text(e.toString(), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
