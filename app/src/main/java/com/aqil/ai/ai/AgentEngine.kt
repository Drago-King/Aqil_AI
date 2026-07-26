package com.aqil.ai.ai

import com.aqil.ai.agent.AgentAction
import com.aqil.ai.agent.AgentController
import com.aqil.ai.agent.AgentEvent
import com.aqil.ai.data.ModelProfile
import kotlinx.coroutines.delay
import org.json.JSONObject

/**
 * Drives a task to completion by looping:
 *   read screen -> ask model for next action (JSON) -> perform it -> repeat.
 *
 * The model is text-only friendly: we serialize the screen's accessibility tree
 * instead of sending images, so cheap/free models can still operate the phone.
 */
class AgentEngine(private val openAi: OpenAiClient) {

    companion object {
        private const val MAX_STEPS = 18
        private const val KEEP_OBSERVATIONS = 3

        val SYSTEM_PROMPT = """
            You are Aqil AI, an autonomous Android phone operator working for your owner.
            You are given a TASK and, each turn, a fresh SCREEN description.

            SCREEN format — a numbered list of on-screen elements, in reading order:
              [n] <type> "<label>" id:<resourceId> {state}
            type is one of: input, button, toggle, link, text.
            state may include: editable, scrollable, focused, checked.
            Example:  [4] input "Search" id:search_src_text {editable}

            Reply with a SINGLE JSON object and nothing else (no markdown, no prose outside it):
              {"thought":"<=10 words","action":"<name>","params":{...}}

            Actions:
              open_app     {"query":"whatsapp"}          open an app by name
              tap          {"text":"Search"}             tap element by matching label/id
              tap          {"index":4}                   tap element by its [number]
              type         {"text":"hello","enter":true} type into the focused/nearest input; enter submits
              press_enter  {}                            submit the current field (search/go)
              long_press   {"index":6}                   long-press an element
              scroll       {"direction":"down"}          down|up|left|right (to reveal more)
              back {} · home {} · recents {} · notifications {}
              screenshot   {}                            capture a screenshot
              read_screen  {}                            OCR the display to read text inside images/photos
              wait         {"ms":800}                     let the screen settle
              speak        {"text":"..."}                say something aloud to the owner
              ask          {"text":"which chat?"}        ask a question, then stop
              finish       {"summary":"done: ..."}       the task is complete

            Strategy:
            - Work in small, verifiable steps. Trust the SCREEN list; never invent elements.
            - Prefer tap by [index] or exact "text". Use raw x/y only as a last resort.
            - To type: make sure an input is focused (tap it first if needed), then "type".
            - For search: type with "enter":true, or type then press_enter.
            - If the target isn't listed, "scroll" to reveal it (tap-by-text auto-scrolls too).
            - If the SCREEN list is sparse, or the text you need lives inside an image/photo, use read_screen.
            - If the TASK is a question you can answer from the REFERENCE DOCUMENT (when provided) or
              from general knowledge without touching the phone, answer it immediately with finish.
            - After each action the SCREEN updates — check that it did what you expected; if not, adapt.
            - Finish as soon as the goal is met. If truly blocked, "ask" and stop.
        """.trimIndent()
    }

    suspend fun run(
        profile: ModelProfile,
        task: String,
        contextText: String? = null,
        onEvent: (AgentEvent) -> Unit,
    ) {
        val device = AgentController.device

        if (device == null) {
            try {
                val msgs = ArrayList<Turn>()
                msgs += Turn("system", "You are Aqil AI, a helpful, concise assistant.")
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

        val history = ArrayList<Turn>()
        history += Turn("system", SYSTEM_PROMPT)
        history += Turn("user", buildString {
            if (!contextText.isNullOrBlank())
                append("REFERENCE DOCUMENT (from an image the owner shared):\n$contextText\n\n")
            append("TASK: $task\n\nSCREEN:\n${device.dumpScreen()}")
        })

        device.showHud()
        try {
            var step = 0
            while (step < MAX_STEPS) {
                step++
                if (AgentController.cancelRequested) { onEvent(AgentEvent.Finish("Stopped.")); return }

                val reply = try {
                    openAi.chat(profile, trimmed(history), temperature = 0.1)
                } catch (e: Exception) {
                    onEvent(AgentEvent.Error(e.message ?: "Model request failed")); return
                }
                history += Turn("assistant", reply)

                val action = parseAction(reply)
                if (action == null) { onEvent(AgentEvent.Speak(reply.trim())); return }

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
                        val result = try { device.execute(action) } catch (e: Exception) { "error: ${e.message}" }
                        onEvent(AgentEvent.Step(action, result))
                        if (AgentController.cancelRequested) { onEvent(AgentEvent.Finish("Stopped.")); return }
                        delay(250)
                        history += Turn("user", "RESULT: $result\n\nSCREEN:\n${device.dumpScreen()}")
                    }
                }
            }
            onEvent(AgentEvent.Finish("Stopped after $MAX_STEPS steps."))
        } finally {
            device.hideHud()
        }
    }

    /** Keep the system + task turns and only the last few observation pairs to bound context. */
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
