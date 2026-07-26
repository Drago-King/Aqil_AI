package com.aqil.ai.agent

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/** One interactable / labelled element on screen. */
data class ScreenElement(
    val index: Int,
    val text: String,
    val kind: String,
    val clickable: Boolean,
    val editable: Boolean,
    val centerX: Int,
    val centerY: Int,
    val node: AccessibilityNodeInfo,
)

object ScreenReader {

    private const val MAX_ELEMENTS = 60

    /** Walk the active window and collect meaningful elements. */
    fun collect(root: AccessibilityNodeInfo?): List<ScreenElement> {
        if (root == null) return emptyList()
        val out = ArrayList<ScreenElement>()
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.addLast(root)
        var index = 0

        while (stack.isNotEmpty() && out.size < MAX_ELEMENTS) {
            val node = stack.removeLast()
            val label = labelOf(node)
            val useful = label.isNotBlank() || node.isClickable || node.isEditable
            if (useful) {
                val r = Rect().also { node.getBoundsInScreen(it) }
                if (r.width() > 0 && r.height() > 0) {
                    out += ScreenElement(
                        index = index++,
                        text = label.ifBlank { "(${shortClass(node)})" }.take(80),
                        kind = shortClass(node),
                        clickable = node.isClickable,
                        editable = node.isEditable,
                        centerX = r.centerX(),
                        centerY = r.centerY(),
                        node = node,
                    )
                }
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { stack.addLast(it) }
            }
        }
        return out
    }

    /** Render the elements as a compact numbered list the model can reason over. */
    fun describe(elements: List<ScreenElement>): String {
        if (elements.isEmpty()) return "(screen is empty or not readable)"
        return buildString {
            elements.forEach { e ->
                val tags = buildList {
                    if (e.clickable) add("tap")
                    if (e.editable) add("input")
                }.joinToString(",")
                append("[${e.index}] \"${e.text}\"")
                if (tags.isNotBlank()) append(" <$tags>")
                append(" @${e.centerX},${e.centerY}\n")
            }
        }.trim()
    }

    private fun labelOf(n: AccessibilityNodeInfo): String {
        val t = n.text?.toString()?.trim().orEmpty()
        if (t.isNotEmpty()) return t
        val d = n.contentDescription?.toString()?.trim().orEmpty()
        return d
    }

    private fun shortClass(n: AccessibilityNodeInfo): String {
        val c = n.className?.toString() ?: return "view"
        return c.substringAfterLast('.').ifBlank { "view" }
    }
}
