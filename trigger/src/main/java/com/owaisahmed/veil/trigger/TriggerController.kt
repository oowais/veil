package com.owaisahmed.veil.trigger

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

enum class TriggerSource { ASSIST_GESTURE }

sealed interface TriggerEvent {
    val atMs: Long

    data class Shown(val showFlags: Int, override val atMs: Long = SystemClock.elapsedRealtime()) : TriggerEvent
    data class Hidden(val afterMs: Long, override val atMs: Long = SystemClock.elapsedRealtime()) : TriggerEvent
    data class Fired(val source: TriggerSource, override val atMs: Long = SystemClock.elapsedRealtime()) : TriggerEvent
}

/** Single entry point for every trigger (assist gesture now; deeplink and QS tile later). */
object TriggerController {

    private const val TAG = "VeilTrigger"

    /** Delay after the session window hides before the screen may be captured. Tune on device. */
    @Volatile var captureDelayMs: Long = 200

    private val main = Handler(Looper.getMainLooper())
    private val _events = MutableSharedFlow<TriggerEvent>(replay = 50, extraBufferCapacity = 50)
    val events: SharedFlow<TriggerEvent> = _events.asSharedFlow()

    private val _assistantConnected = MutableStateFlow(false)

    /**
     * True once Android has connected [VeilInteractionService] in this process. Holding the assistant
     * role isn't enough: after an in-app update Android sometimes doesn't reconnect it, and swipes
     * then never arrive.
     */
    val assistantConnected: StateFlow<Boolean> = _assistantConnected.asStateFlow()

    fun setAssistantConnected(connected: Boolean) {
        Log.i(TAG, "assistant service connected=$connected")
        _assistantConnected.value = connected
    }

    /** Invoked on the main thread once the screen is ready to capture. */
    @Volatile var onTrigger: ((TriggerSource) -> Unit)? = null

    fun record(event: TriggerEvent) {
        Log.i(TAG, event.toString())
        _events.tryEmit(event)
    }

    fun fire(source: TriggerSource) {
        main.postDelayed({
            record(TriggerEvent.Fired(source))
            onTrigger?.invoke(source)
        }, captureDelayMs)
    }
}
