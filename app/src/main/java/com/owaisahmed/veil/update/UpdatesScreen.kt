package com.owaisahmed.veil.update

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.owaisahmed.veil.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Debug/profile builds have their own app id (D-028), so a release APK can't update them. */
val isDevBuild = BuildConfig.BUILD_TYPE != "release"

/** Updates tab (milestone 0.5): version, daily-check toggle, manual check, install. */
@Composable
fun UpdatesScreen(latest: UpdateCheck.Latest?, onLatest: (UpdateCheck.Latest?) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var daily by remember { mutableStateOf(UpdateCheck.dailyCheck(context)) }
    var checking by remember { mutableStateOf(false) }
    var installing by remember { mutableStateOf(false) }
    /** 0–1, or null while the size is unknown. */
    var progress by remember { mutableStateOf<Float?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    val current = BuildConfig.VERSION_NAME
    val available = latest?.takeIf { UpdateCheck.newer(it.version, current) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Installed: Veil $current${if (isDevBuild) " (dev build)" else ""}")
        if (isDevBuild) {
            Text("Dev builds can't update themselves. Install the release from github.com/oowais/veil/releases.")
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Switch(checked = daily, onCheckedChange = {
                daily = it
                UpdateCheck.setDailyCheck(context, it)
            })
            Text("Check for updates once a day")
        }
        Text(
            "The check asks GitHub (api.github.com) for the latest Veil release. GitHub sees your IP " +
                "address, nothing else is sent. Turn it off and use the button below instead."
        )

        Button(enabled = !checking && !installing, onClick = {
            message = null
            checking = true
            scope.launch {
                try {
                    val result = UpdateCheck.check(context, force = true)
                    onLatest(result)
                    if (result != null && !UpdateCheck.newer(result.version, current)) message = "Veil is up to date."
                } catch (e: Exception) {
                    message = "Check failed: ${e.message}"
                } finally {
                    checking = false
                }
            }
        }) { Text(if (checking) "Checking…" else "Check for updates") }

        latest?.let {
            Text("Latest release: ${it.version}, checked ${DateUtils.getRelativeTimeSpanString(it.checkedAt)}")
        }

        if (available != null) {
            if (available.apkUrl == null) {
                Text("Veil ${available.version} is being built. Check again in a few minutes.")
            } else if (!isDevBuild) {
                Button(enabled = !installing, onClick = {
                    message = null
                    if (!Updater.canInstall(context)) {
                        Updater.openInstallSettings(context)
                        message = "Allow Veil to install apps, then tap Install again."
                        return@Button
                    }
                    installing = true
                    progress = null
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                Updater.downloadAndInstall(context, available.apkUrl) { done, total ->
                                    progress = if (total > 0) done.toFloat() / total else null
                                }
                            }
                        } catch (e: Exception) {
                            message = "Update failed: ${e.message}"
                        } finally {
                            installing = false
                        }
                    }
                }) { Text(if (installing) "Downloading…" else "Install Veil ${available.version}") }
                if (installing) {
                    val p = progress
                    if (p != null) LinearProgressIndicator(progress = { p }) else LinearProgressIndicator()
                }
            }
        }

        message?.let { Text(it) }
    }
}
