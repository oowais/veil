package com.owaisahmed.veil.capture

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.view.Display
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * The current screen as a software bitmap (for OCR and the blurred box backgrounds), or null when
 * Android refuses: secure windows (FLAG_SECURE, e.g. banking apps) or calls less than ~1/3 s apart.
 * Must run before Veil's own overlay is shown.
 */
suspend fun AccessibilityService.screenshot(): Bitmap? = suspendCancellableCoroutine { cont ->
    takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : AccessibilityService.TakeScreenshotCallback {
        override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
            val buffer = result.hardwareBuffer
            val bmp = try {
                Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)?.copy(Bitmap.Config.ARGB_8888, false)
            } finally {
                buffer.close()
            }
            cont.resume(bmp)
        }

        override fun onFailure(errorCode: Int) = cont.resume(null)
    })
}
