package com.owaisahmed.veil.pipeline

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** What the last triggers did, shown on the Veil tab (no adb, D-030). Newest last, capped. */
object PipelineLog {
    private const val TAG = "VeilPipeline"
    private const val MAX_LINES = 200
    private val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT)

    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines.asStateFlow()

    fun log(message: String) {
        Log.i(TAG, message)
        val line = "${time.format(Date())}  $message"
        _lines.update { (it + line).takeLast(MAX_LINES) }
    }

    fun clear() = _lines.update { emptyList() }
}
