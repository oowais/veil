package com.owaisahmed.veil.trigger

import android.service.voice.VoiceInteractionService

/**
 * Exists so Veil can hold the assistant role; all work happens in [VeilSession]. Android calls
 * [onReady] once it has connected the service, which is what makes the corner swipe reach Veil.
 */
class VeilInteractionService : VoiceInteractionService() {

    override fun onReady() {
        super.onReady()
        TriggerController.setAssistantConnected(true)
    }

    override fun onShutdown() {
        TriggerController.setAssistantConnected(false)
        super.onShutdown()
    }
}
