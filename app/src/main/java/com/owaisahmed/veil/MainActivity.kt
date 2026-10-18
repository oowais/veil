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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.owaisahmed.veil.spikes.OcrSpike
import com.owaisahmed.veil.spikes.TranslateSpike
import com.owaisahmed.veil.spikes.TriggerSpike

/** Milestone 0: one tab per spike. Replaced by real settings UI in milestone 4. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Surface(Modifier.fillMaxSize()) {
                    var tab by rememberSaveable { mutableIntStateOf(0) }
                    Column(Modifier.safeDrawingPadding()) {
                        PrimaryTabRow(selectedTabIndex = tab) {
                            listOf("Trigger", "OCR", "Translate").forEachIndexed { i, title ->
                                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(title) })
                            }
                        }
                        val content = Modifier.padding(16.dp)
                        when (tab) {
                            0 -> TriggerSpike(content)
                            1 -> OcrSpike(content)
                            else -> TranslateSpike(content)
                        }
                    }
                }
            }
        }
    }
}
