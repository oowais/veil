package com.owaisahmed.veil.overlay

/**
 * User-adjustable look of the boxes (D-055): largest text size, and how strongly the page-coloured
 * layer covers the blurred original.
 */
data class OverlayStyle(val textMaxSp: Float = 16f, val veilAlpha: Float = DEFAULT_VEIL_ALPHA) {
    companion object {
        const val DEFAULT_VEIL_ALPHA = 0.45f
    }
}
