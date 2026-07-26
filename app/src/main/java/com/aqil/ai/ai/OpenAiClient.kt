package com.aqil.ai.ai

import com.aqil.ai.agent.AgentController
import com.aqil.ai.data.ModelProfile
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/** One chat turn sent to the model. */
data class Turn(val role: String, val content: String)

class OpenAiClient {

    // Fast-fail timeouts so a slow/hanging model never makes the app feel frozen.
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .callTimeout(55, TimeUnit.SECONDS)
        .build()

    private val json = "application/json; charset=utf-8".toMediaType()

    /**
     * Calls {baseUrl}/chat/completions and returns the assistant text.
     * The call is fully cancellable: stopping a task aborts the in-flight request immediately.
     */
    suspend fun chat(
        profile: ModelProfile,
        messages: List<Turn>,
        temperature: Double = 0.4,
    ): String {
        require(profile.apiKey.isNotBlank()) { "No API key set. Add one in Settings." }

        val body = JSONObject().apply {
            put("model", profile.model)
            put("temperature", temperature)
            put("max_tokens", 320)               // actions are tiny — cap output so replies are fast
            put("messages", JSONArray().apply {
                messages.forEach {
                    put(JSONObject().put("role", it.role).put("content", it.content))
                }
            })
        }

        val url = profile.baseUrl.trimEnd('/') + "/chat/completions"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${profile.apiKey}")
            .header("Content-Type", "application/json")
            .header("HTTP-Referer", "https://github.com/aqil-ai")
            .header("X-Title", "Aqil AI")
            .post(body.toString().toRequestBody(json))
            .build()

        val call = http.newCall(request)

        return suspendCancellableCoroutine { cont ->
            // Let the floating Stop button (or in-app Stop) abort this exact request.
            AgentController.onCancel = { runCatching { call.cancel() } }
            cont.invokeOnCancellation { runCatching { call.cancel() } }

            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    AgentController.onCancel = null
                    if (cont.isActive) cont.resumeWithException(RuntimeException(e.message ?: "Network error"))
                }

                override fun onResponse(call: Call, response: Response) {
                    AgentController.onCancel = null
                    try {
                        response.use { resp ->
                            val raw = resp.body?.string().orEmpty()
                            if (!resp.isSuccessful) {
                                cont.resumeWithException(RuntimeException("Model error ${resp.code}: ${raw.take(300)}"))
                                return
                            }
                            val obj = JSONObject(raw)
                            val choices = obj.optJSONArray("choices")
                            if (choices == null || choices.length() == 0) {
                                cont.resumeWithException(RuntimeException("Unexpected response: ${raw.take(200)}"))
                                return
                            }
                            val msg = choices.getJSONObject(0).getJSONObject("message")
                            val out = msg.optString("content").ifBlank { msg.optString("reasoning", "") }
                            if (cont.isActive) cont.resume(out)
                        }
                    } catch (e: Exception) {
                        if (cont.isActive) cont.resumeWithException(e)
                    }
                }
            })
        }
    }
}
