package com.owaisahmed.veil.ui

import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.owaisahmed.veil.capture.VeilAccessibilityService

/** What the gesture needs from Android, and the system screens that grant it. */
object SystemSettings {

    /** The assistant role can't be requested by apps (D-022); the user picks Veil in settings. */
    fun isAssistant(context: Context): Boolean =
        context.getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_ASSISTANT)

    fun isAccessibilityOn(context: Context): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        val cn = ComponentName(context, VeilAccessibilityService::class.java)
        return enabled.split(':').any { it.equals(cn.flattenToString(), true) || it.equals(cn.flattenToShortString(), true) }
    }

    fun openAssistant(context: Context) =
        open(context, Settings.ACTION_VOICE_INPUT_SETTINGS, Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)

    fun openAccessibility(context: Context) = open(context, Settings.ACTION_ACCESSIBILITY_SETTINGS)

    fun openAppInfo(context: Context) = context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
    )

    fun openUrl(context: Context, url: String) = context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    /** Opens the first of [actions] this phone has, falling back to the main settings screen. */
    private fun open(context: Context, vararg actions: String) {
        for (action in listOf(*actions, Settings.ACTION_SETTINGS)) {
            try {
                context.startActivity(Intent(action))
                return
            } catch (_: ActivityNotFoundException) {
            }
        }
    }
}
