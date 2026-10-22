package com.owaisahmed.veil

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.owaisahmed.veil.pipeline.Pipeline
import com.owaisahmed.veil.pipeline.PipelineLog
import com.owaisahmed.veil.ui.VeilTheme
import kotlinx.coroutines.CancellationException

/**
 * "Translate with Veil" in the text selection menu of any app (D-063): Android hands over the
 * selected text, Veil shows the English in a sheet over that app, to copy or, where the text can be
 * edited, to put in place of the German.
 */
class SelectionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()?.trim().orEmpty()
        if (text.isEmpty()) return finish()
        val readOnly = intent.getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, false)
        val sourceApp = referrer?.takeIf { it.scheme == "android-app" }?.host
        setContent {
            VeilTheme {
                SelectionSheet(
                    text, sourceApp,
                    onReplace = if (readOnly) null else { english ->
                        setResult(RESULT_OK, Intent().putExtra(Intent.EXTRA_PROCESS_TEXT, english))
                        finish()
                    },
                    onCopy = ::copy,
                    onOpenVeil = { startActivity(Intent(this, MainActivity::class.java)); finish() },
                    onClose = ::finish,
                )
            }
        }
    }

    private fun copy(english: String) {
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("English", english))
        // Android 13+ shows its own confirmation.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun SelectionSheet(
    text: String,
    sourceApp: String?,
    onReplace: ((String) -> Unit)?,
    onCopy: (String) -> Unit,
    onOpenVeil: () -> Unit,
    onClose: () -> Unit,
) {
    var english by remember { mutableStateOf<String?>(null) }
    var done by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var needsModel by remember { mutableStateOf(false) }
    LaunchedEffect(text) {
        try {
            english = Pipeline.translateSelection(text, sourceApp) { english = it }
            done = true
        } catch (e: CancellationException) {
            throw e
        } catch (e: IllegalStateException) {
            error = e.message
            needsModel = true
        } catch (e: Exception) {
            PipelineLog.log("selection failed: ${e::class.simpleName}: ${e.message}")
            error = "Veil failed: ${e.message}"
        }
    }

    // A tap on the dimmed app behind closes the sheet.
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.32f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            shape = MaterialTheme.shapes.extraLarge.copy(bottomStart = MaterialTheme.shapes.extraSmall.bottomStart, bottomEnd = MaterialTheme.shapes.extraSmall.bottomEnd),
            tonalElevation = 3.dp,
        ) {
            Column(Modifier.navigationBarsPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text, maxLines = 3, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!done && error == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                english?.let {
                    SelectionContainer(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                        Text(it, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    TextButton(onClick = onClose) { Text("Close") }
                    if (needsModel) FilledTonalButton(onClick = onOpenVeil) { Text("Open Veil") }
                    val en = english
                    if (done && en != null) {
                        if (onReplace != null) TextButton(onClick = { onReplace(en) }) { Text("Replace") }
                        FilledTonalButton(onClick = { onCopy(en) }) { Text("Copy") }
                    }
                }
            }
        }
    }
}
