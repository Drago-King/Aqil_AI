package com.aqil.ai.ai

import com.aqil.ai.agent.AgentAction
import com.aqil.ai.agent.AgentController
import com.aqil.ai.agent.AgentEvent
import com.aqil.ai.data.ModelProfile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import org.json.JSONObject

/**
 * Drives a task to completion by looping:
 *   read screen -> ask model for next action (JSON) -> perform it -> repeat.
 */
class AgentEngine(private val openAi: OpenAiClient) {

    companion object {
        private const val MAX_STEPS = 24
        private const val KEEP_OBSERVATIONS = 3
        private const val MAX_BAD_REPLIES = 3
        private const val STUCK_LIMIT = 4

        // Button presses that can spend money or cause hard-to-undo effects.
        private val RISKY = listOf(
            "pay", "buy", "purchase", "order", "checkout", "place order", "confirm order",
            "delete", "remove", "transfer", "dial", "call ", "subscribe", "book ", "send money"
        )

        val SYSTEM_PROMPT = """
            You are Aqil AI, an autonomous Android phone operator working for your owner.
            You are given a TASK and, each turn, a fresh SCREEN description.

            SCREEN format — a numbered list of on-screen elements, in reading order:
              [n] <type> "<label>" id:<resourceId> {state}
            type is one of: input, button, toggle, link, text.
            state may include: editable, scrollable, focused, checked.
            Icon-only controls show as button "(ImageView)" — use the id hint or [index].
            Example:  [4] input "Search" id:search_src_text {editable}

            OUTPUT RULES — READ CAREFULLY:
            - Reply with ONE JSON object and ABSOLUTELY NOTHING else. No prose, no markdown,
              no code fences, no safety notes, no explanation before or after.
            - Shape: {"thought":"<=10 words","action":"<name>","params":{...}}

            Actions:
              open_app     {"query":"spotify"}           open an app by name
              tap          {"text":"Search"}             tap by matching label/id (case-insensitive)
              tap          {"index":4}                   tap element by its [number]
              tap          {"x":540,"y":1200}            tap raw coordinates (use with read_screen)
              type         {"text":"lovely","enter":true} type into the focused/nearest input
              press_enter  {}                            submit the current field
              long_press   {"index":6}                   long-press an element
              scroll       {"direction":"down"}          down|up|left|right
              back {} · home {} · recents {} · notifications {}
              read_screen  {}                            OCR the display; returns text with @x,y to tap
              screenshot   {}                            capture a screenshot to gallery
              wait         {"ms":800}                     let the screen settle
              speak        {"text":"..."}                say something aloud
              ask          {"text":"which one?"}         ask a question, then stop
              finish       {"summary":"done: ..."}       the goal is complete

            Strategy:
            - Work step by step and trust the SCREEN list. Never invent elements.
            - Search is almost always a "Search" tab in the BOTTOM navigation bar or a magnifier
              icon in the TOP toolbar. Tap it by "text":"Search" or its [index].
            - Prefer tap by [index] or exact "text". Use raw x/y only with read_screen results.
            - To type: ensure an input is focused (tap it first if needed), then "type".
              For search boxes use "enter":true (or type then press_enter).
            - If a control isn't in the SCREEN list (e.g. an unlabeled icon), use read_screen,
              then tap the returned "label" @x,y with {"x":..,"y":..}.
            - If the target isn't visible, "scroll" to reveal it (tap-by-text auto-scrolls too).
            - After each action the SCREEN updates — verify it worked; if not, adapt. Do NOT repeat
              the same failing action. Do NOT give up early.
            - Finish only when the goal is actually done. If truly blocked, "ask".
        """.trimIndent()
    }

    suspend fun run(
        profile: ModelProfile,
        task: String,
        contextText: String? = null,
        customInstructions: String? = null,
        confirmRisky: Boolean = false,
        onEvent: (AgentEvent) -> Unit,
    ) {
        val device = AgentController.device

        if (device == null) {
            try {
                val msgs = ArrayList<Turn>()
                msgs += Turn("system", "You are Aqil AI, a helpful, concise assistant.")
                if (!customInstructions.isNullOrBlank())
                    msgs += Turn("system", "Owner's standing instructions:\n$customInstructions")
                if (!contextText.isNullOrBlank())
                    msgs += Turn("system", "Reference document the owner shared:\n$contextText")
                msgs += Turn("user", task)
                val reply = openAi.chat(profile, msgs)
                onEvent(AgentEvent.Speak(reply.ifBlank { "I don't have screen control enabled yet." }))
            } catch (e: Exception) {
                onEvent(AgentEvent.Error(e.message ?: "Request failed"))
            }
            return
        }

        var lastScreen = device.dumpScreen()
        val history = ArrayList<Turn>()
        history += Turn("system", SYSTEM_PROMPT)
        history += Turn("user", buildString {
            if (!customInstructions.isNullOrBlank())
                append("OWNER'S STANDING INSTRUCTIONS (obey these):\n$customInstructions\n\n")
            if (!contextText.isNullOrBlank())
                append("REFERENCE DOCUMENT (from an image the owner shared):\n$contextText\n\n")
            append("TASK: $task\n\nSCREEN:\n$lastScreen")
        })

        device.showHud()
        try {
            var step = 0
            var badReplies = 0
            var unchanged = 0
            while (step < MAX_STEPS) {
                step++
                if (AgentController.cancelRequested) { onEvent(AgentEvent.Finish("Stopped.")); return }

                val reply = try {
                    openAi.chat(profile, trimmed(history), temperature = 0.1)
                } catch (e: Exception) {
                    if (AgentController.cancelRequested) { onEvent(AgentEvent.Finish("Stopped.")); return }
                    onEvent(AgentEvent.Error(e.message ?: "Model request failed")); return
                }
                history += Turn("assistant", reply)

                val action = parseAction(reply)
                if (action == null) {
                    badReplies++
                    if (badReplies >= MAX_BAD_REPLIES) {
                        val clean = reply.trim()
                        val looksLikeAnswer = clean.length in 1..300 &&
                            !clean.contains("safety", ignoreCase = true) && !clean.contains("{")
                        onEvent(
                            if (looksLikeAnswer) AgentEvent.Speak(clean)
                            else AgentEvent.Error(
                                "The model kept replying without a usable action. Try a more capable " +
                                    "model in Settings (an OpenAI or Groq model handles this better)."
                            )
                        )
                        return
                    }
                    history += Turn(
                        "user",
                        "That was not a valid action. Reply with ONLY one JSON object, e.g. " +
                            "{\"action\":\"tap\",\"params\":{\"text\":\"Search\"}} — no other text.\n\n" +
                            "SCREEN:\n${device.dumpScreen()}"
                    )
                    continue
                }
                badReplies = 0

                action.thought?.takeIf { it.isNotBlank() }?.let { onEvent(AgentEvent.Thinking(it)) }
                if (AgentController.cancelRequested) { onEvent(AgentEvent.Finish("Stopped.")); return }

                when (action.name) {
                    "finish" -> { onEvent(AgentEvent.Finish(action.params.optString("summary", "Done."))); return }
                    "speak" -> {
                        onEvent(AgentEvent.Speak(action.params.optString("text")))
                        history += Turn("user", "(spoke) SCREEN:\n${device.dumpScreen()}")
                    }
                    "ask" -> {
                        onEvent(AgentEvent.Speak(action.params.optString("text")))
                        onEvent(AgentEvent.Finish("Waiting for your answer.")); return
                    }
                    else -> {
                        // Confirmation gate for risky, hard-to-undo actions.
                        if (confirmRisky && isRisky(action)) {
                            val approved = requestConfirm(riskPrompt(action), onEvent)
                            if (AgentController.cancelRequested) { onEvent(AgentEvent.Finish("Stopped.")); return }
                            if (!approved) {
                                onEvent(AgentEvent.Step(action, "skipped — you declined"))
                                history += Turn(
                                    "user",
                                    "The owner DECLINED that action. Do not repeat it; choose a " +
                                        "different approach or finish.\n\nSCREEN:\n${device.dumpScreen()}"
                                )
                                continue
                            }
                        }

                        val result = try { device.execute(action) } catch (e: Exception) { "error: ${e.message}" }
                        onEvent(AgentEvent.Step(action, result))
                        if (AgentController.cancelRequested) { onEvent(AgentEvent.Finish("Stopped.")); return }
                        delay(settleFor(action.name))

                        val screen = device.dumpScreen()
                        val changesExpected = action.name in setOf("tap", "click", "scroll", "swipe", "type", "long_press")
                        unchanged = if (changesExpected && screen == lastScreen) unchanged + 1 else 0
                        lastScreen = screen
                        if (unchanged >= STUCK_LIMIT) {
                            onEvent(AgentEvent.Finish("I'm stuck — the screen isn't responding to that. Can you nudge me in the right direction?"))
                            return
                        }
                        val note = if (unchanged >= 2)
                            "\n(NOTE: the screen did NOT change after that — try a different element, scroll, or read_screen.)"
                        else ""
                        history += Turn("user", "RESULT: $result$note\n\nSCREEN:\n$screen")
                    }
                }
            }
            onEvent(AgentEvent.Finish("Stopped after $MAX_STEPS steps."))
        } finally {
            device.hideHud()
        }
    }

    private suspend fun requestConfirm(prompt: String, onEvent: (AgentEvent) -> Unit): Boolean {
        val d = CompletableDeferred<Boolean>()
        AgentController.confirm = d
        onEvent(AgentEvent.Confirm(prompt))
        return try { d.await() } finally { AgentController.confirm = null }
    }

    private fun isRisky(a: AgentAction): Boolean {
        if (a.name !in setOf("tap", "click", "long_press")) return false
        val hay = (a.params.optString("text") + " " + (a.thought ?: "")).lowercase()
        return RISKY.any { hay.contains(it) }
    }

    private fun riskPrompt(a: AgentAction): String {
        val label = a.params.optString("text").ifBlank { a.thought ?: a.name }
        return "Aqil wants to tap \"$label\". Approve?"
    }

    /** How long to wait for the screen to settle after each kind of action. */
    private fun settleFor(action: String): Long = when (action) {
        "open_app", "launch_app" -> 1100
        "tap", "click", "back", "recents", "notifications" -> 380
        "scroll", "swipe" -> 280
        "type", "press_enter", "enter", "submit" -> 280
        "read_screen", "ocr", "screen_text" -> 200
        else -> 250
    }

    private fun trimmed(history: List<Turn>): List<Turn> {
        if (history.size <= 2 + KEEP_OBSERVATIONS * 2) return history
        return history.take(2) + history.takeLast(KEEP_OBSERVATIONS * 2)
    }

    private fun parseAction(raw: String): AgentAction? {
        val jsonText = extractJson(raw) ?: return null
        return try {
            val o = JSONObject(jsonText)
            val name = o.optString("action").ifBlank { return null }
            AgentAction(name, o.optJSONObject("params") ?: JSONObject(), o.optString("thought", null))
        } catch (_: Exception) { null }
    }

    /** Pull the first balanced {...} block out of a possibly-chatty reply. */
    private fun extractJson(s: String): String? {
        val start = s.indexOf('{')
        if (start < 0) return null
        var depth = 0; var inStr = false; var esc = false
        for (i in start until s.length) {
            val c = s[i]
            when {
                esc -> esc = false
                c == '\\' && inStr -> esc = true
                c == '"' -> inStr = !inStr
                !inStr && c == '{' -> depth++
                !inStr && c == '}' -> { depth--; if (depth == 0) return s.substring(start, i + 1) }
            }
        }
        return null
    }
}
