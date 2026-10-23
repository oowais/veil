package com.owaisahmed.veil.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.owaisahmed.veil.BuildConfig
import com.owaisahmed.veil.R
import com.owaisahmed.veil.update.UpdateCheck

private enum class Screen(val title: String, @DrawableRes val icon: Int) {
    HOME("Veil", R.drawable.ic_home),
    PHOTO("Photo", R.drawable.ic_photo),
    HISTORY("History", R.drawable.ic_history),
    MODELS("Models", R.drawable.ic_models),
    SETTINGS("Settings", R.drawable.ic_settings),
}

/** The app: Home, Photo, Models and Settings behind a bottom navigation bar; History opens from Home. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VeilAppUi(openModels: Boolean = false) {
    val context = LocalContext.current
    var screen by rememberSaveable { mutableStateOf(if (openModels) Screen.MODELS else Screen.HOME) }
    var latest by remember { mutableStateOf(UpdateCheck.saved(context)) }
    // Daily check on app start (D-029); quiet on failure, Settings → Check now can retry.
    LaunchedEffect(Unit) {
        runCatching { UpdateCheck.check(context, force = false) }.onSuccess { latest = it }
    }
    val updateAvailable = !isDevBuild && latest?.let {
        it.apkUrl != null && UpdateCheck.newer(it.version, BuildConfig.VERSION_NAME)
    } == true

    BackHandler(enabled = screen != Screen.HOME) { screen = Screen.HOME }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(screen.title) },
                // History is opened from Home, not the bottom bar, so it gets a way back.
                navigationIcon = {
                    if (screen == Screen.HISTORY) {
                        IconButton(onClick = { screen = Screen.HOME }) { Icon(painterResource(R.drawable.ic_arrow_back), "Back") }
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                Screen.entries.filter { it != Screen.HISTORY }.forEach { s ->
                    NavigationBarItem(
                        selected = screen == s || (screen == Screen.HISTORY && s == Screen.HOME),
                        onClick = { screen = s },
                        label = { Text(if (s == Screen.HOME) "Home" else s.title) },
                        icon = {
                            BadgedBox(badge = { if (s == Screen.SETTINGS && updateAvailable) Badge() }) {
                                Icon(painterResource(s.icon), contentDescription = null)
                            }
                        },
                    )
                }
            }
        },
    ) { padding ->
        val content = Modifier.padding(padding)
        when (screen) {
            Screen.HOME -> HomeScreen(onOpenModels = { screen = Screen.MODELS }, onOpenHistory = { screen = Screen.HISTORY }, modifier = content)
            Screen.PHOTO -> PhotoScreen(onOpenModels = { screen = Screen.MODELS }, modifier = content)
            Screen.HISTORY -> HistoryScreen(content)
            Screen.MODELS -> ModelsScreen(content)
            Screen.SETTINGS -> SettingsScreen(latest, { latest = it }, content)
        }
    }
}
