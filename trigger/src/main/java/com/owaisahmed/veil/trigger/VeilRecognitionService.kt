package com.owaisahmed.veil.trigger

import android.content.Intent
import android.speech.RecognitionService
import android.speech.SpeechRecognizer

/** Stub required for the assistant role to be selectable; Veil does no speech recognition. */
class VeilRecognitionService : RecognitionService() {
    override fun onStartListening(recognizerIntent: Intent?, listener: Callback?) {
        listener?.error(SpeechRecognizer.ERROR_CLIENT)
    }

    override fun onCancel(listener: Callback?) = Unit

    override fun onStopListening(listener: Callback?) = Unit
}
