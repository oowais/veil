package com.owaisahmed.veil.trigger

import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import android.service.voice.VoiceInteractionSession

/**
 * Invisible session: the assistant gesture only signals "translate now".
 * We decline the assist structure and screenshot (we capture ourselves) and never show UI.
 */
class VeilSession(context: Context) : VoiceInteractionSession(context) {

    private var shownAt = 0L

    override fun onCreate() {
        super.onCreate()
        setDisabledShowContext(SHOW_WITH_ASSIST or SHOW_WITH_SCREENSHOT)
        setUiEnabled(false)
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        shownAt = SystemClock.elapsedRealtime()
        TriggerController.record(TriggerEvent.Shown(showFlags))
        hide()
    }

    override fun onHide() {
        super.onHide()
        val hiddenAfterMs = SystemClock.elapsedRealtime() - shownAt
        TriggerController.record(TriggerEvent.Hidden(hiddenAfterMs))
        // Capture must wait until the session window is fully gone, or its scrim ends up in the screenshot.
        TriggerController.fire(source = TriggerSource.ASSIST_GESTURE)
    }
}
