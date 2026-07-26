package com.aqil.ai.ai

import com.aqil.ai.data.ModelProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** One chat turn sent to the model. */
data class Turn(val role: String, val content: String)

class OpenAiClient {

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private val json = "application/json; charset=utf-8".toMediaType()

    /**
     * Calls {baseUrl}/chat/completions and returns the assistant text.
     * Works with OpenRouter, OpenAI, Groq, Together, and any OpenAI-compatible endpoint.
     */
    suspend fun chat(
        profile: ModelProfile,
        messages: List<Turn>,
        temperature: Double = 0.4,
    ): String = withContext(Dispatchers.IO) {
        require(profile.apiKey.isNotBlank()) { "No API key set. Add one in Settings." }

        val body = JSONObject().apply {
            put("model", profile.model)
            put("temperature", temperature)
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
            // OpenRouter attribution headers (harmless elsewhere):
            .header("HTTP-Referer", "https://github.com/aqil-ai")
            .header("X-Title", "Aqil AI")
            .post(body.toString().toRequestBody(json))
            .build()

        http.newCall(request).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw RuntimeException("Model error ${resp.code}: ${raw.take(400)}")
            }
            val obj = JSONObject(raw)
            val choices = obj.optJSONArray("choices")
                ?: throw RuntimeException("Unexpected response: ${raw.take(300)}")
            val msg = choices.getJSONObject(0).getJSONObject("message")
            msg.optString("content").ifBlank {
                // Some providers stream reasoning separately; fall back gracefully.
                msg.optString("reasoning", "")
            }
        }
    }
}
