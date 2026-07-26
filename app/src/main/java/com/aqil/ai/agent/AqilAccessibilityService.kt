package com.aqil.ai.agent

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Aqil's connection to the physical device. Enabled by the user in
 * Settings > Accessibility. Reads the screen and performs gestures/typing.
 */
class AqilAccessibilityService : AccessibilityService(), DeviceController {

    private val main = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        AgentController.device = this
    }

    override fun onDestroy() {
        if (AgentController.device === this) AgentController.device = null
        super.onDestroy()
    }

    override fun onInterrupt() {}
    override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* passive */ }

    // ---- DeviceController ----

    override fun dumpScreen(): String {
        val elements = ScreenReader.collect(rootInActiveWindow)
        val app = rootInActiveWindow?.packageName?.toString() ?: "unknown"
        return "app: $app\n" + ScreenReader.describe(elements)
    }

    override suspend fun execute(action: AgentAction): String {
        return when (action.name) {
            "launch_app" -> launchApp(action.params.optString("query"))
            "tap" -> tap(action)
            "type" -> typeText(action.params.optString("text"))
            "scroll" -> scroll(action.params.optString("direction", "down"))
            "back" -> global(GLOBAL_ACTION_BACK, "pressed back")
            "home" -> global(GLOBAL_ACTION_HOME, "went home")
            "recents" -> global(GLOBAL_ACTION_RECENTS, "opened recents")
            "screenshot" -> screenshot()
            "wait" -> { Thread.sleep(action.params.optLong("ms", 1000).coerceAtMost(4000)); "waited" }
            else -> "unknown action: ${action.name}"
        }
    }

    // ---- actions ----

    private fun tap(action: AgentAction): String {
        val p = action.params
        // 1) explicit coordinates
        if (p.has("x") && p.has("y")) {
            return runGesture(p.getInt("x").toFloat(), p.getInt("y").toFloat())
                .let { if (it) "tapped ${p.getInt("x")},${p.getInt("y")}" else "tap failed" }
        }
        val elements = ScreenReader.collect(rootInActiveWindow)
        // 2) by index
        if (p.has("index")) {
            val e = elements.getOrNull(p.getInt("index")) ?: return "no element at that index"
            return tapElement(e)
        }
        // 3) by text (best fuzzy match)
        val query = p.optString("text").trim()
        if (query.isNotBlank()) {
            val match = elements.firstOrNull { it.text.equals(query, true) }
                ?: elements.firstOrNull { it.text.contains(query, true) }
                ?: return "no element matching \"$query\""
            return tapElement(match)
        }
        return "tap needs text, index, or x/y"
    }

    private fun tapElement(e: ScreenElement): String {
        // Prefer a semantic click if the node supports it; fall back to a gesture.
        if (e.node.isClickable && e.node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return "clicked \"${e.text}\""
        }
        return if (runGesture(e.centerX.toFloat(), e.centerY.toFloat()))
            "tapped \"${e.text}\"" else "tap failed on \"${e.text}\""
    }

    private fun typeText(text: String): String {
        if (text.isBlank()) return "nothing to type"
        val target = findEditable(rootInActiveWindow) ?: return "no text field focused"
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        target.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val ok = target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        return if (ok) "typed \"$text\"" else "type failed"
    }

    private fun findEditable(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (root == null) return null
        var firstEditable: AccessibilityNodeInfo? = null
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.addLast(root)
        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            if (n.isEditable) {
                if (n.isFocused) return n            // best: the field the user is in
                if (firstEditable == null) firstEditable = n
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.addLast(it) }
        }
        return firstEditable                          // fall back to the first text field
    }

    private fun scroll(direction: String): String {
        val dm = resources.displayMetrics
        val w = dm.widthPixels
        val h = dm.heightPixels
        val cx = w / 2f
        val cy = h / 2f
        val (x1, y1, x2, y2) = when (direction.lowercase()) {
            "up" -> arrayOf(cx, h * 0.3f, cx, h * 0.7f)
            "down" -> arrayOf(cx, h * 0.7f, cx, h * 0.3f)
            "left" -> arrayOf(w * 0.2f, cy, w * 0.8f, cy)
            "right" -> arrayOf(w * 0.8f, cy, w * 0.2f, cy)
            else -> arrayOf(cx, h * 0.7f, cx, h * 0.3f)
        }
        val path = Path().apply { moveTo(x1, y1); lineTo(x2, y2) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 300)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
        return "scrolled $direction"
    }

    private fun global(action: Int, label: String): String {
        performGlobalAction(action)
        return label
    }

    private fun screenshot(): String {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
            "screenshot saved to gallery"
        } else {
            "screenshots need Android 11+"
        }
    }

    private fun launchApp(query: String): String {
        if (query.isBlank()) return "no app named"
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(intent, 0)
        val match = apps.minByOrNull { info ->
            val label = info.loadLabel(pm).toString().lowercase()
            when {
                label == query.lowercase() -> 0
                label.startsWith(query.lowercase()) -> 1
                label.contains(query.lowercase()) -> 2
                else -> 99
            }
        }?.takeIf { it.loadLabel(pm).toString().lowercase().contains(query.lowercase()) }
            ?: return "couldn't find an app called \"$query\""

        val pkg = match.activityInfo.packageName
        val launch = pm.getLaunchIntentForPackage(pkg)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } ?: return "can't launch $pkg"
        startActivity(launch)
        return "opened ${match.loadLabel(pm)}"
    }

    /** Dispatch a short tap gesture at the given point. Posted to the main thread. */
    private fun runGesture(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y); lineTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 60)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        main.post { dispatchGesture(gesture, null, null) }
        Thread.sleep(120) // let the tap land before we read the next screen
        return true
    }

    // A suspend-friendly tap kept for future use (awaits completion).
    suspend fun tapAwait(x: Float, y: Float): Boolean =
        suspendCancellableCoroutine { cont ->
            val path = Path().apply { moveTo(x, y); lineTo(x, y) }
            val stroke = GestureDescription.StrokeDescription(path, 0, 60)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            main.post {
                dispatchGesture(gesture, object : GestureResultCallback() {
                    override fun onCompleted(d: GestureDescription?) { if (cont.isActive) cont.resume(true) }
                    override fun onCancelled(d: GestureDescription?) { if (cont.isActive) cont.resume(false) }
                }, null)
            }
        }
}
