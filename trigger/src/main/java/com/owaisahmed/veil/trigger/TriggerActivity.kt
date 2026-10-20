package com.owaisahmed.veil.trigger

import android.app.Activity
import android.os.Bundle

/**
 * Invisible activity behind `veil://translate` and the Quick Settings tile (D-055). It only starts a
 * translation of whatever is under it and closes at once (Theme.NoDisplay must finish before
 * onResume); the capture waits until it's gone.
 */
class TriggerActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val source = if (intent.getBooleanExtra(EXTRA_FROM_TILE, false)) TriggerSource.QS_TILE else TriggerSource.LINK
        TriggerController.fire(source)
        finish()
    }

    companion object {
        const val EXTRA_FROM_TILE = "from_tile"
    }
}
