package com.owaisahmed.veil.spikes

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.owaisahmed.veil.BuildConfig

/**
 * Copies a spike's results to the clipboard, headed by app version and device, so they can be
 * pasted into chat. Phone testing has no adb (D-030). Android shows its own "Copied" confirmation.
 */
@Composable
fun CopyButton(label: String, enabled: Boolean = true, text: () -> String) {
    val context = LocalContext.current
    OutlinedButton(enabled = enabled, onClick = {
        val header = "Veil ${BuildConfig.VERSION_NAME} (${BuildConfig.BUILD_TYPE}), ${Build.MANUFACTURER} ${Build.MODEL}, " +
            "Android ${Build.VERSION.RELEASE}\n"
        context.getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText(label, header + text()))
    }) { Text("Copy results") }
}
