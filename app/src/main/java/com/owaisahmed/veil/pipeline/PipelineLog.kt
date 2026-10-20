package com.owaisahmed.veil.pipeline

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * What the last triggers did, shown under Settings → Troubleshooting (no adb, D-030). Newest last,
 * capped. Kept in an app-private file, so it survives Android ending the app and the stack trace of
 * a crash is there on the next start (D-059).
 */
object PipelineLog {
    private const val TAG = "VeilPipeline"
    private const val MAX_LINES = 300
    private val time = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.ROOT)
    private val writer = Executors.newSingleThreadExecutor { Thread(it, "veil-log") }
    @Volatile private var file: File? = null
    private var written = 0

    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines.asStateFlow()

    /** Loads the saved log and records crashes from now on. Call once, from Application.onCreate. */
    fun init(context: Context) {
        val f = File(context.filesDir, "activity.log")
        file = f
        val saved = runCatching { if (f.exists()) f.readLines().takeLast(MAX_LINES) else emptyList() }.getOrDefault(emptyList())
        _lines.update { (saved + it).takeLast(MAX_LINES) }
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            // Written synchronously: the process is about to end.
            runCatching {
                val trace = StringWriter().also { e.printStackTrace(PrintWriter(it)) }.toString()
                f.appendText("${time.format(Date())}  CRASH in ${thread.name}: $trace\n")
            }
            previous?.uncaughtException(thread, e)
        }
    }

    fun log(message: String) {
        Log.i(TAG, message)
        val line = "${time.format(Date())}  $message"
        _lines.update { (it + line).takeLast(MAX_LINES) }
        val f = file ?: return
        writer.execute {
            runCatching {
                // Append; now and then rewrite the file with only the lines still shown.
                if (++written % MAX_LINES == 0) f.writeText(_lines.value.joinToString("\n", postfix = "\n"))
                else f.appendText(line + "\n")
            }
        }
    }

    fun clear() {
        _lines.update { emptyList() }
        file?.let { f -> writer.execute { runCatching { f.writeText("") } } }
    }
}
