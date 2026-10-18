package com.owaisahmed.veil

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.owaisahmed.veil.ui.VeilAppUi
import com.owaisahmed.veil.ui.VeilTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VeilTheme { VeilAppUi() }
        }
    }
}
