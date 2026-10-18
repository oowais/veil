package com.owaisahmed.veil.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.owaisahmed.veil.BuildConfig

/** A titled group of settings: small coloured heading above a card. */
@Composable
fun Section(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            Column(Modifier.padding(vertical = 8.dp), content = content)
        }
    }
}

/** Secondary explanation under a row or inside a section. */
@Composable
fun Hint(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** Copies [text] headed by app version and device, so it can be pasted into a bug report. */
fun copyWithDeviceInfo(context: Context, label: String, text: String) {
    val header = "Veil ${BuildConfig.VERSION_NAME} (${BuildConfig.BUILD_TYPE}), ${Build.MANUFACTURER} ${Build.MODEL}, " +
        "Android ${Build.VERSION.RELEASE}\n"
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(label, header + text))
}
