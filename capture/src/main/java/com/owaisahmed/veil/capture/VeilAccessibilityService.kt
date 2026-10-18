package com.owaisahmed.veil.capture

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * Veil's accessibility service. It does nothing on its own: the pipeline uses [instance] to read
 * the screen, take a screenshot and as the context for the overlay window
 * (TYPE_ACCESSIBILITY_OVERLAY), and listens to [onEvent] to remove the overlay when the user
 * leaves the app.
 */
class VeilAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        instance = this
        onConnected?.invoke()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        onEvent?.invoke(event)
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    companion object {
        /** The running service, or null while it is turned off in system settings. */
        @Volatile var instance: VeilAccessibilityService? = null
            private set

        /** Called on the main thread for window-state events. */
        @Volatile var onEvent: ((AccessibilityEvent) -> Unit)? = null

        /** Called when the service starts (boot, update, switched on): a chance to preload models. */
        @Volatile var onConnected: (() -> Unit)? = null
    }
}
