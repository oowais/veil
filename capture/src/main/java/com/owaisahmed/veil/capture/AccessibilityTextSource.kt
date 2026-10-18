package com.owaisahmed.veil.capture

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

/**
 * Text from the accessibility tree of the app windows on screen. Each node's text is already a
 * whole paragraph, so no line grouping or OCR is needed. Misses text drawn as pixels (images,
 * games, some Flutter apps); screenshot + OCR covers those in milestone 2.
 */
class AccessibilityTextSource(private val service: AccessibilityService) : TextSource {

    override suspend fun capture(): List<TextNode> {
        val out = LinkedHashMap<Pair<String, Rect>, TextNode>()
        val roots = service.windows
            .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            .mapNotNull { w -> w.root?.let { it to Rect().also(w::getBoundsInScreen) } }
            .ifEmpty { listOfNotNull(service.rootInActiveWindow?.let { it to Rect(0, 0, Int.MAX_VALUE, Int.MAX_VALUE) }) }
        for ((root, windowBounds) in roots) collect(root, windowBounds, out)
        return dropContainers(out.values.toList())
    }

    private fun collect(root: AccessibilityNodeInfo, window: Rect, out: MutableMap<Pair<String, Rect>, TextNode>) {
        val iconMaxPx = (ICON_MAX_DP * service.resources.displayMetrics.density).toInt()
        val stack = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        var visited = 0
        while (stack.isNotEmpty() && visited < MAX_NODES) {
            val node = stack.removeLast()
            visited++
            if (!node.isVisibleToUser) continue
            // Input fields count too (search boxes, forms): their text, or the placeholder when empty.
            // The overlay is modal (D-038), so covering them doesn't get in the way of typing.
            val text = (node.text?.takeIf { it.isNotBlank() } ?: node.hintText)?.toString()?.trim()
            if (!text.isNullOrEmpty() && !node.isPassword && !isImage(node)) {
                val bounds = Rect().also(node::getBoundsInScreen)
                val icon = node.isClickable && bounds.width() <= iconMaxPx && bounds.height() <= iconMaxPx
                if (!icon && bounds.intersect(window) && bounds.width() > 0 && bounds.height() > 0) {
                    // WebViews often expose the same text on a node and its wrapper; keep one.
                    out.putIfAbsent(text to bounds, TextNode(text, bounds))
                }
            }
            for (i in node.childCount - 1 downTo 0) node.getChild(i)?.let(stack::add)
        }
    }

    private companion object {
        const val MAX_NODES = 3000
        /** Clickable nodes this small are icon buttons; their text is a label for the icon, not on screen. */
        const val ICON_MAX_DP = 64

        /** Image alt text (Chrome exposes it as the node's text) isn't visible text. */
        fun isImage(node: AccessibilityNodeInfo) = node.className?.contains("Image") == true

        /**
         * Drops nodes whose box contains another text node: those are containers whose text is a
         * title or summary (a WebView's text is the page title, over the whole page), not visible
         * text of their own.
         */
        fun dropContainers(nodes: List<TextNode>): List<TextNode> = nodes.filter { outer ->
            nodes.none { inner -> inner !== outer && inner.bounds != outer.bounds && outer.bounds.contains(inner.bounds) }
        }
    }
}
