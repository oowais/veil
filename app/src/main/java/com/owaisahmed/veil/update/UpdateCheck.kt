package com.owaisahmed.veil.update

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Asks GitHub for the latest release (port of befikr's useUpdate.ts, D-019). Automatic checks run at
 * most once a day and only while the daily check is on (D-020); "Check for updates" always runs.
 * The last result is kept, so an available update survives restarts.
 */
object UpdateCheck {
    private const val LATEST_URL = "https://api.github.com/repos/oowais/veil/releases/latest"
    private const val APK_NAME = "veil.apk"
    private const val DAY_MS = 86_400_000L
    private const val PREFS = "updates"

    /** `apkUrl` is null while the release has no `veil.apk` attached. */
    data class Latest(val version: String, val apkUrl: String?, val checkedAt: Long)

    /** a > b for "0.x.y" versions. */
    fun newer(a: String, b: String): Boolean {
        val pa = a.split('.').map { it.toIntOrNull() ?: 0 }
        val pb = b.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until 3) {
            val x = pa.getOrElse(i) { 0 }
            val y = pb.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun dailyCheck(ctx: Context): Boolean = prefs(ctx).getBoolean("daily", true)

    fun setDailyCheck(ctx: Context, on: Boolean) = prefs(ctx).edit().putBoolean("daily", on).apply()

    fun saved(ctx: Context): Latest? {
        val p = prefs(ctx)
        val version = p.getString("version", null) ?: return null
        return Latest(version, p.getString("apkUrl", null), p.getLong("checkedAt", 0))
    }

    /** Without `force`: skipped when the daily check is off or the last check is under a day old. */
    suspend fun check(ctx: Context, force: Boolean): Latest? = withContext(Dispatchers.IO) {
        val last = saved(ctx)
        if (!force && (!dailyCheck(ctx) || (last != null && System.currentTimeMillis() - last.checkedAt < DAY_MS))) {
            return@withContext last
        }
        val conn = URL(LATEST_URL).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 15_000
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        val body = try {
            when (conn.responseCode) {
                200 -> Unit
                404 -> throw IOException("no release published yet")
                else -> throw IOException("GitHub: HTTP ${conn.responseCode}")
            }
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
        val json = JSONObject(body)
        val assets = json.getJSONArray("assets")
        val apkUrl = (0 until assets.length()).map { assets.getJSONObject(it) }
            .firstOrNull { it.getString("name") == APK_NAME }?.getString("browser_download_url")
        val latest = Latest(json.getString("tag_name").removePrefix("v"), apkUrl, System.currentTimeMillis())
        prefs(ctx).edit()
            .putString("version", latest.version)
            .putString("apkUrl", latest.apkUrl)
            .putLong("checkedAt", latest.checkedAt)
            .apply()
        latest
    }
}
