package com.aqil.ai.agent

import org.json.JSONObject

/** A structured action the model asked us to perform. */
data class AgentAction(
    val name: String,
    val params: JSONObject = JSONObject(),
    val thought: String? = null,
)

/** Events streamed back to the UI / voice as the agent works. */
sealed interface AgentEvent {
    data class Thinking(val text: String) : AgentEvent
    data class Step(val action: AgentAction, val result: String) : AgentEvent
    data class Speak(val text: String) : AgentEvent
    data class Finish(val summary: String) : AgentEvent
    data class Error(val message: String) : AgentEvent
}

/** Implemented by the accessibility service; the engine talks to the phone through this. */
interface DeviceController {
    /** A compact, model-friendly description of what's on screen right now. */
    fun dumpScreen(): String

    /** Perform one action and return a short human-readable result. */
    suspend fun execute(action: AgentAction): String

    /** Show/hide the floating "stop" control while a task runs. Default no-op. */
    fun showHud() {}
    fun hideHud() {}
}

/**
 * Global bridge so the engine (running in a ViewModel or overlay service) can reach the
 * accessibility service without a hard dependency. The service registers itself on connect.
 */
object AgentController {
    @Volatile
    var device: DeviceController? = null

    /** Set by the floating stop button (or the in-app Stop). The loop checks it each step. */
    @Volatile
    var cancelRequested: Boolean = false

    val isConnected: Boolean get() = device != null

    fun requestCancel() { cancelRequested = true }
    fun reset() { cancelRequested = false }
}
