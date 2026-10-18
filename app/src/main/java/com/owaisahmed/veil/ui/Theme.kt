package com.owaisahmed.veil.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Slate = Color(0xFF2B3A55) // launcher icon background

/** Material You colours from the wallpaper on Android 12+, a slate-blue scheme before that. */
@Composable
fun VeilTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme(primary = Color(0xFFB4C5E8), secondary = Color(0xFFBDC7DC))
        else -> lightColorScheme(primary = Slate, secondary = Color(0xFF555F71))
    }
    MaterialTheme(colorScheme = colors, content = content)
}
