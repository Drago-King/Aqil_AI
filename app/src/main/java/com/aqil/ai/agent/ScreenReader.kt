package com.aqil.ai.agent

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/** One interactable / labelled element on screen. */
data class ScreenElement(
    val index: Int,
    val text: String,
    val type: String,          // input | button | toggle | link | text
    val id: String,            // short resource id (may be blank)
    val clickable: Boolean,    // itself or via a clickable ancestor
    val editable: Boolean,
    val scrollable: Boolean,
    val focused: Boolean,
    val checked: Boolean,
    val centerX: Int,
    val centerY: Int,
    val node: AccessibilityNodeInfo,
)

object ScreenReader {

    private const val MAX_ELEMENTS = 55

    /** Walk the active window and collect meaningful elements, in reading order. */
    fun collect(root: AccessibilityNodeInfo?): List<ScreenElement> {
        if (root == null) return emptyList()
        val raw = ArrayList<ScreenElement>()
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.addLast(root)

        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            val label = labelOf(node)
            val effClickable = node.isClickable || hasClickableAncestor(node)
            val useful = label.isNotBlank() || effClickable || node.isEditable || node.isScrollable
            if (useful) {
                val r = Rect().also { node.getBoundsInScreen(it) }
                if (r.width() > 0 && r.height() > 0 && r.bottom > 0 && r.right > 0) {
                    raw += ScreenElement(
                        index = 0, // assigned after sorting
                        text = label.ifBlank { "(${shortClass(node)})" }.take(90),
                        type = typeOf(node, effClickable),
                        id = shortId(node),
                        clickable = effClickable,
                        editable = node.isEditable,
                        scrollable = node.isScrollable,
                        focused = node.isFocused,
                        checked = node.isCheckable && node.isChecked,
                        centerX = r.centerX(),
                        centerY = r.centerY(),
                        node = node,
                    )
                }
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let { stack.addLast(it) }
        }

        // Reading order: top-to-bottom, then left-to-right. Cheap and predictable for the model.
        val ordered = raw.sortedWith(compareBy({ it.centerY }, { it.centerX }))
            .take(MAX_ELEMENTS)
        // Re-number after sorting so indices are stable within this dump.
        return ordered.mapIndexed { i, e -> e.copy(index = i) }
    }

    /** Render the elements as a compact numbered list the model can reason over. */
    fun describe(elements: List<ScreenElement>): String {
        if (elements.isEmpty()) return "(screen is empty or not readable)"
        return buildString {
            elements.forEach { e ->
                append("[${e.index}] ${e.type} \"${e.text}\"")
                if (e.id.isNotBlank()) append(" id:${e.id}")
                val state = buildList {
                    if (e.editable) add("editable")
                    if (e.scrollable) add("scrollable")
                    if (e.focused) add("focused")
                    if (e.checked) add("checked")
                }.joinToString(",")
                if (state.isNotBlank()) append(" {$state}")
                append("\n")
            }
        }.trim()
    }

    private fun labelOf(n: AccessibilityNodeInfo): String {
        n.text?.toString()?.trim()?.let { if (it.isNotEmpty()) return it }
        n.contentDescription?.toString()?.trim()?.let { if (it.isNotEmpty()) return it }
        // Hint text for empty inputs helps a lot (e.g. "Search").
        if (n.isEditable) n.hintText?.toString()?.trim()?.let { if (it.isNotEmpty()) return it }
        return ""
    }

    private fun typeOf(n: AccessibilityNodeInfo, clickable: Boolean): String {
        val cls = (n.className?.toString() ?: "").lowercase()
        return when {
            n.isEditable || cls.contains("edittext") -> "input"
            cls.contains("switch") || cls.contains("checkbox") || cls.contains("toggle") -> "toggle"
            cls.contains("button") || cls.contains("imagebutton") -> "button"
            clickable -> "button"
            else -> "text"
        }
    }

    private fun shortId(n: AccessibilityNodeInfo): String =
        n.viewIdResourceName?.substringAfterLast('/')?.take(40).orEmpty()

    private fun hasClickableAncestor(n: AccessibilityNodeInfo): Boolean {
        var p = n.parent
        var hops = 0
        while (p != null && hops < 5) {
            if (p.isClickable) return true
            p = p.parent
            hops++
        }
        return false
    }

    private fun shortClass(n: AccessibilityNodeInfo): String {
        val c = n.className?.toString() ?: return "view"
        return c.substringAfterLast('.').ifBlank { "view" }
    }
}
