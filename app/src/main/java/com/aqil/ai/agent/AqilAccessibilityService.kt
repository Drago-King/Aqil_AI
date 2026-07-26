package com.aqil.ai.agent

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.ImageView
import com.aqil.ai.R
import kotlin.math.abs

/**
 * Aqil's connection to the physical device. Enabled by the user in
 * Settings > Accessibility. Reads the screen and performs gestures/typing.
 */
class AqilAccessibilityService : AccessibilityService(), DeviceController {

    private val main = Handler(Looper.getMainLooper())
    private var hud: View? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        AgentController.device = this
    }

    override fun onDestroy() {
        removeHud()
        if (AgentController.device === this) AgentController.device = null
        super.onDestroy()
    }

    override fun onInterrupt() {}
    override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* passive */ }

    // ---- DeviceController ----

    override fun dumpScreen(): String {
        val root = rootInActiveWindow
        val elements = ScreenReader.collect(root)
        val app = root?.packageName?.toString() ?: "unknown"
        val title = root?.let { firstBigText(elements) } ?: ""
        val header = "app: $app" + if (title.isNotBlank()) " · screen: $title" else ""
        return header + "\n" + ScreenReader.describe(elements)
    }

    override suspend fun execute(action: AgentAction): String {
        return when (action.name) {
            "launch_app", "open_app" -> launchApp(action.params.optString("query").ifBlank { action.params.optString("name") })
            "tap", "click" -> tap(action)
            "type" -> typeText(action.params.optString("text"), action.params.optBoolean("enter", false))
            "press_enter", "enter", "submit" -> pressEnter()
            "long_press" -> longPress(action)
            "scroll", "swipe" -> scroll(action.params.optString("direction", "down"))
            "back" -> global(GLOBAL_ACTION_BACK, "pressed back")
            "home" -> global(GLOBAL_ACTION_HOME, "went home")
            "recents" -> global(GLOBAL_ACTION_RECENTS, "opened recents")
            "notifications" -> global(GLOBAL_ACTION_NOTIFICATIONS, "opened notifications")
            "screenshot" -> screenshot()
            "wait" -> { Thread.sleep(action.params.optLong("ms", 800).coerceIn(200, 4000)); "waited" }
            else -> "unknown action: ${action.name}"
        }
    }

    // ---- HUD (floating stop button) ----

    override fun showHud() {
        main.post {
            if (hud != null) return@post
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val sizePx = (52 * resources.displayMetrics.density).toInt()
            val icon = ImageView(this).apply {
                setImageResource(R.drawable.ic_stop)
                val pad = (12 * resources.displayMetrics.density).toInt()
                setPadding(pad, pad, pad, pad)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#E5484D"))
                    setStroke((2 * resources.displayMetrics.density).toInt(), Color.parseColor("#33FFFFFF"))
                }
            }
            val types = intArrayOf(
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            )
            for (type in types) {
                val lp = WindowManager.LayoutParams(
                    sizePx, sizePx, type,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.END
                    x = (14 * resources.displayMetrics.density).toInt()
                    y = (90 * resources.displayMetrics.density).toInt()
                }
                attachDrag(icon, lp, wm)
                val added = runCatching { wm.addView(icon, lp) }.isSuccess
                if (added) { hud = icon; break }
            }
        }
    }

    override fun hideHud() = removeHud()

    private fun removeHud() {
        main.post {
            hud?.let { v ->
                val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
                runCatching { wm.removeView(v) }
            }
            hud = null
        }
    }

    private fun attachDrag(view: View, lp: WindowManager.LayoutParams, wm: WindowManager) {
        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; var moved = false
        view.setOnTouchListener { _, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.rawX; downY = ev.rawY; startX = lp.x; startY = lp.y; moved = false; true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (ev.rawX - downX); val dy = (ev.rawY - downY)
                    if (abs(dx) > 12 || abs(dy) > 12) moved = true
                    // gravity is TOP|END, so a rightward drag should reduce x
                    lp.x = (startX - dx).toInt().coerceAtLeast(0)
                    lp.y = (startY + dy).toInt().coerceAtLeast(0)
                    runCatching { wm.updateViewLayout(view, lp) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) AgentController.requestCancel()
                    true
                }
                else -> false
            }
        }
    }

    // ---- actions ----

    private fun tap(action: AgentAction): String {
        val p = action.params
        if (p.has("x") && p.has("y")) {
            val x = p.getInt("x"); val y = p.getInt("y")
            return if (runGesture(x.toFloat(), y.toFloat())) "tapped $x,$y" else "tap failed"
        }
        var elements = ScreenReader.collect(rootInActiveWindow)
        if (p.has("index")) {
            val e = elements.getOrNull(p.getInt("index")) ?: return "no element at index ${p.getInt("index")}"
            return tapElement(e)
        }
        val query = p.optString("text").trim()
        if (query.isBlank()) return "tap needs text, index, or x/y"

        matchElement(elements, query)?.let { return tapElement(it) }

        // Not visible — try scrolling to reveal it (up to a few screens).
        repeat(4) {
            if (AgentController.cancelRequested) return "cancelled"
            scroll("down"); Thread.sleep(450)
            elements = ScreenReader.collect(rootInActiveWindow)
            matchElement(elements, query)?.let { return tapElement(it) }
        }
        return "couldn't find \"$query\" on screen"
    }

    /** Rank matches: exact text > exact id > startsWith > contains, preferring tappable items. */
    private fun matchElement(elements: List<ScreenElement>, q: String): ScreenElement? {
        val query = q.lowercase()
        fun score(e: ScreenElement): Int {
            val t = e.text.lowercase(); val id = e.id.lowercase()
            var s = when {
                t == query -> 0
                id == query -> 1
                t.startsWith(query) -> 2
                id.contains(query) -> 3
                t.contains(query) -> 4
                else -> 99
            }
            if (s < 99 && !e.clickable && !e.editable) s += 5 // prefer actionable
            return s
        }
        return elements.map { it to score(it) }.filter { it.second < 99 }.minByOrNull { it.second }?.first
    }

    private fun tapElement(e: ScreenElement): String {
        val clickTarget = clickableSelfOrAncestor(e.node)
        if (clickTarget != null && clickTarget.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return "tapped \"${e.text}\""
        }
        return if (runGesture(e.centerX.toFloat(), e.centerY.toFloat()))
            "tapped \"${e.text}\"" else "tap failed on \"${e.text}\""
    }

    private fun clickableSelfOrAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isClickable) return node
        var p = node.parent; var hops = 0
        while (p != null && hops < 5) {
            if (p.isClickable) return p
            p = p.parent; hops++
        }
        return null
    }

    private fun typeText(text: String, thenEnter: Boolean): String {
        if (text.isBlank()) return "nothing to type"
        val target = findEditable(rootInActiveWindow) ?: return "no text field found — tap one first"
        target.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val ok = target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        if (!ok) return "type failed"
        if (thenEnter) { Thread.sleep(150); imeEnter(target) }
        return if (thenEnter) "typed \"$text\" and submitted" else "typed \"$text\""
    }

    private fun pressEnter(): String {
        val target = findEditable(rootInActiveWindow) ?: return "no field to submit"
        return if (imeEnter(target)) "submitted" else "couldn't submit"
    }

    private fun imeEnter(node: AccessibilityNodeInfo): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
        else false
    }

    private fun longPress(action: AgentAction): String {
        val elements = ScreenReader.collect(rootInActiveWindow)
        val e = when {
            action.params.has("index") -> elements.getOrNull(action.params.getInt("index"))
            action.params.optString("text").isNotBlank() -> matchElement(elements, action.params.getString("text"))
            else -> null
        } ?: return "long_press needs text or index"
        val target = clickableSelfOrAncestor(e.node) ?: e.node
        if (target.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)) return "long-pressed \"${e.text}\""
        // gesture fallback
        val path = Path().apply { moveTo(e.centerX.toFloat(), e.centerY.toFloat()); lineTo(e.centerX.toFloat(), e.centerY.toFloat()) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 650)
        main.post { dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null) }
        Thread.sleep(700)
        return "long-pressed \"${e.text}\""
    }

    private fun findEditable(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (root == null) return null
        var firstEditable: AccessibilityNodeInfo? = null
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.addLast(root)
        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            if (n.isEditable) {
                if (n.isFocused) return n
                if (firstEditable == null) firstEditable = n
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.addLast(it) }
        }
        return firstEditable
    }

    private fun scroll(direction: String): String {
        val dm = resources.displayMetrics
        val w = dm.widthPixels; val h = dm.heightPixels
        val cx = w / 2f; val cy = h / 2f
        val (x1, y1, x2, y2) = when (direction.lowercase()) {
            "up" -> arrayOf(cx, h * 0.35f, cx, h * 0.75f)
            "down" -> arrayOf(cx, h * 0.75f, cx, h * 0.35f)
            "left" -> arrayOf(w * 0.2f, cy, w * 0.8f, cy)
            "right" -> arrayOf(w * 0.8f, cy, w * 0.2f, cy)
            else -> arrayOf(cx, h * 0.75f, cx, h * 0.35f)
        }
        val path = Path().apply { moveTo(x1, y1); lineTo(x2, y2) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 260)
        main.post { dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null) }
        Thread.sleep(120)
        return "scrolled $direction"
    }

    private fun global(action: Int, label: String): String {
        performGlobalAction(action); return label
    }

    private fun screenshot(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT); "screenshot saved to gallery"
        } else "screenshots need Android 11+"
    }

    private fun launchApp(query: String): String {
        if (query.isBlank()) return "no app named"
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(intent, 0)
        val q = query.lowercase()
        val match = apps.minByOrNull { info ->
            val label = info.loadLabel(pm).toString().lowercase()
            when {
                label == q -> 0
                label.startsWith(q) -> 1
                label.contains(q) -> 2
                else -> 99
            }
        }?.takeIf { it.loadLabel(pm).toString().lowercase().contains(q) }
            ?: return "couldn't find an app called \"$query\""
        val pkg = match.activityInfo.packageName
        val launch = pm.getLaunchIntentForPackage(pkg)?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            ?: return "can't launch $pkg"
        startActivity(launch)
        return "opened ${match.loadLabel(pm)}"
    }

    private fun firstBigText(elements: List<ScreenElement>): String =
        elements.firstOrNull { it.type == "text" && it.text.length in 3..40 }?.text.orEmpty()

    /** Dispatch a short tap gesture at the given point. Posted to the main thread. */
    private fun runGesture(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y); lineTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 55)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        main.post { dispatchGesture(gesture, null, null) }
        Thread.sleep(110)
        return true
    }
}
