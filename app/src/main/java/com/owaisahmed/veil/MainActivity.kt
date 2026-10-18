package com.owaisahmed.veil

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.owaisahmed.veil.spikes.OcrSpike
import com.owaisahmed.veil.spikes.TranslateSpike
import com.owaisahmed.veil.spikes.TriggerSpike
import com.owaisahmed.veil.update.UpdateCheck
import com.owaisahmed.veil.update.UpdatesScreen
import com.owaisahmed.veil.update.isDevBuild

/** Milestone 0: one tab per spike, plus Updates (0.5). Replaced by real settings UI in milestone 4. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Surface(Modifier.fillMaxSize()) {
                    var tab by rememberSaveable { mutableIntStateOf(0) }
                    val context = LocalContext.current
                    var latest by remember { mutableStateOf(UpdateCheck.saved(context)) }
                    // Daily check on app start (D-029); quiet on failure, the Updates tab can retry.
                    LaunchedEffect(Unit) {
                        runCatching { UpdateCheck.check(context, force = false) }.onSuccess { latest = it }
                    }
                    val updateDot = !isDevBuild && latest?.let {
                        it.apkUrl != null && UpdateCheck.newer(it.version, BuildConfig.VERSION_NAME)
                    } == true
                    Column(Modifier.safeDrawingPadding()) {
                        PrimaryTabRow(selectedTabIndex = tab) {
                            listOf("Trigger", "OCR", "Translate", if (updateDot) "Updates •" else "Updates")
                                .forEachIndexed { i, title ->
                                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(title) })
                                }
                        }
                        val content = Modifier.padding(16.dp)
                        when (tab) {
                            0 -> TriggerSpike(content)
                            1 -> OcrSpike(content)
                            2 -> TranslateSpike(content)
                            else -> UpdatesScreen(latest, { latest = it }, content)
                        }
                    }
                }
            }
        }
    }
}
