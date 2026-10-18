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
        return out.values.toList()
    }

    private fun collect(root: AccessibilityNodeInfo, window: Rect, out: MutableMap<Pair<String, Rect>, TextNode>) {
        val stack = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        var visited = 0
        while (stack.isNotEmpty() && visited < MAX_NODES) {
            val node = stack.removeLast()
            visited++
            if (!node.isVisibleToUser) continue
            val text = node.text?.toString()?.trim()
            // Skip fields the user types into: covering them would get in the way.
            if (!text.isNullOrEmpty() && !node.isEditable && !node.isPassword) {
                val bounds = Rect().also(node::getBoundsInScreen)
                if (bounds.intersect(window) && bounds.width() > 0 && bounds.height() > 0) {
                    // WebViews often expose the same text on a node and its wrapper; keep one.
                    out.putIfAbsent(text to bounds, TextNode(text, bounds))
                }
            }
            for (i in node.childCount - 1 downTo 0) node.getChild(i)?.let(stack::add)
        }
    }

    private companion object {
        const val MAX_NODES = 3000
    }
}
