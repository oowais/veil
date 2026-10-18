package com.owaisahmed.veil.spikes

import android.app.role.RoleManager
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
    val request = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        held = roleManager.isRoleHeld(RoleManager.ROLE_ASSISTANT)
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
        Button(
            enabled = !held && roleManager.isRoleAvailable(RoleManager.ROLE_ASSISTANT),
            onClick = { request.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT)) },
        ) { Text("Set as assistant") }
        Text("Then swipe up from a bottom corner (or long-press power, if set to assistant). Events show below and in logcat tag VeilTrigger.")

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
