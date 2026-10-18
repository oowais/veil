package com.owaisahmed.veil.trigger

import android.service.voice.VoiceInteractionService

/** Exists only so Veil can hold the assistant role; all work happens in [VeilSession]. */
class VeilInteractionService : VoiceInteractionService()
