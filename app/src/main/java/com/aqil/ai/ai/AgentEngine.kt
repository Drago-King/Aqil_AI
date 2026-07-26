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
        private const val MAX_STEPS = 20
        private const val KEEP_OBSERVATIONS = 4

        val SYSTEM_PROMPT = """
            You are Aqil AI, an autonomous Android phone operator. You control a real phone on
            behalf of your owner. You are given a TASK and a live description of the SCREEN
            (a numbered list of on-screen elements with their text and centre coordinates).

            You act ONE step at a time. Each reply MUST be a single JSON object and nothing else
            (no markdown, no commentary outside the JSON). Shape:
              {"thought":"<very short reasoning>","action":"<name>","params":{...}}

            Available actions:
              launch_app   params:{"query":"whatsapp"}      open an app by name
              tap          params:{"text":"Search"}          tap the element whose text matches
              tap          params:{"index":3}                tap element by its list number
              tap          params:{"x":540,"y":1200}         tap raw coordinates
              type         params:{"text":"hello"}           type into the focused field
              scroll       params:{"direction":"down"}       down|up|left|right
              back         params:{}                         press back
              home         params:{}                         go to home screen
              recents      params:{}                         open recent apps
              screenshot   params:{}                         capture a screenshot
              wait         params:{"ms":1200}                wait for the screen to settle
              speak        params:{"text":"..."}             say something to the owner
              ask          params:{"text":"which chat?"}     ask the owner a question, then stop
              finish       params:{"summary":"done: ..."}    the task is complete

            Rules:
            - Prefer tap-by-text or tap-by-index over raw coordinates.
            - After launching an app or tapping, the next SCREEN reflects the result. Re-check it.
            - Keep thoughts under ~12 words. Never invent elements that aren't listed.
            - If you are blocked or unsure, use "ask" and stop.
            - When the goal is achieved, use "finish".
        """.trimIndent()
    }

    /**
     * Runs [task] to completion, emitting progress via [onEvent].
     * If the accessibility service is not connected it degrades to a plain chat reply.
     */
    suspend fun run(
        profile: ModelProfile,
        task: String,
        onEvent: (AgentEvent) -> Unit,
    ) {
        val device = AgentController.device

        if (device == null) {
            // No hands — just answer as a chat assistant.
            try {
                val reply = openAi.chat(
                    profile,
                    listOf(
                        Turn("system", "You are Aqil AI, a helpful, concise assistant."),
                        Turn("user", task),
                    ),
                )
                onEvent(AgentEvent.Speak(reply.ifBlank { "I don't have screen control enabled yet." }))
            } catch (e: Exception) {
                onEvent(AgentEvent.Error(e.message ?: "Request failed"))
            }
            return
        }

        val history = ArrayList<Turn>()
        history += Turn("system", SYSTEM_PROMPT)
        history += Turn("user", "TASK: $task\n\nSCREEN:\n${device.dumpScreen()}")

        var step = 0
        while (step < MAX_STEPS) {
            step++

            val reply = try {
                openAi.chat(profile, trimmed(history), temperature = 0.2)
            } catch (e: Exception) {
                onEvent(AgentEvent.Error(e.message ?: "Model request failed"))
                return
            }
            history += Turn("assistant", reply)

            val action = parseAction(reply)
            if (action == null) {
                // Model spoke plainly instead of JSON — treat as a spoken reply.
                onEvent(AgentEvent.Speak(reply.trim()))
                return
            }

            action.thought?.takeIf { it.isNotBlank() }?.let { onEvent(AgentEvent.Thinking(it)) }

            when (action.name) {
                "finish" -> {
                    onEvent(AgentEvent.Finish(action.params.optString("summary", "Done.")))
                    return
                }
                "speak" -> {
                    onEvent(AgentEvent.Speak(action.params.optString("text")))
                    history += Turn("user", "(spoke to owner) SCREEN:\n${device.dumpScreen()}")
                }
                "ask" -> {
                    onEvent(AgentEvent.Speak(action.params.optString("text")))
                    onEvent(AgentEvent.Finish("Waiting for your answer."))
                    return
                }
                else -> {
                    val result = try {
                        device.execute(action)
                    } catch (e: Exception) {
                        "error: ${e.message}"
                    }
                    onEvent(AgentEvent.Step(action, result))
                    delay(700)
                    history += Turn(
                        "user",
                        "RESULT: $result\n\nSCREEN:\n${device.dumpScreen()}"
                    )
                }
            }
        }
        onEvent(AgentEvent.Finish("Stopped after $MAX_STEPS steps."))
    }

    /** Keep the system + task turns and only the last few observation pairs to bound context. */
    private fun trimmed(history: List<Turn>): List<Turn> {
        if (history.size <= 2 + KEEP_OBSERVATIONS * 2) return history
        val head = history.take(2) // system + original task
        val tail = history.takeLast(KEEP_OBSERVATIONS * 2)
        return head + tail
    }

    private fun parseAction(raw: String): AgentAction? {
        val jsonText = extractJson(raw) ?: return null
        return try {
            val o = JSONObject(jsonText)
            val name = o.optString("action").ifBlank { return null }
            AgentAction(
                name = name,
                params = o.optJSONObject("params") ?: JSONObject(),
                thought = o.optString("thought", null),
            )
        } catch (_: Exception) {
            null
        }
    }

    /** Pull the first balanced {...} block out of a possibly-chatty reply. */
    private fun extractJson(s: String): String? {
        val start = s.indexOf('{')
        if (start < 0) return null
        var depth = 0
        var inStr = false
        var esc = false
        for (i in start until s.length) {
            val c = s[i]
            when {
                esc -> esc = false
                c == '\\' && inStr -> esc = true
                c == '"' -> inStr = !inStr
                !inStr && c == '{' -> depth++
                !inStr && c == '}' -> {
                    depth--
                    if (depth == 0) return s.substring(start, i + 1)
                }
            }
        }
        return null
    }
}
