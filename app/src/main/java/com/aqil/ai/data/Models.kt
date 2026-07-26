package com.aqil.ai.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** A single connection profile: a provider + base URL + model + key. */
data class ModelProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val baseUrl: String,
    val model: String,
    val apiKey: String,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("baseUrl", baseUrl)
        put("model", model)
        put("apiKey", apiKey)
    }

    companion object {
        fun fromJson(o: JSONObject) = ModelProfile(
            id = o.optString("id", UUID.randomUUID().toString()),
            name = o.optString("name"),
            baseUrl = o.optString("baseUrl"),
            model = o.optString("model"),
            apiKey = o.optString("apiKey"),
        )

        /** Sensible starter profiles the user can edit. */
        fun defaults(): List<ModelProfile> = listOf(
            ModelProfile(
                name = "OpenRouter — Tencent HY3 (free)",
                baseUrl = "https://openrouter.ai/api/v1",
                model = "tencent/hy3:free",
                apiKey = "",
            ),
            ModelProfile(
                name = "OpenRouter — Llama 3.1 8B (free)",
                baseUrl = "https://openrouter.ai/api/v1",
                model = "meta-llama/llama-3.1-8b-instruct:free",
                apiKey = "",
            ),
            ModelProfile(
                name = "OpenAI — GPT-4o mini",
                baseUrl = "https://api.openai.com/v1",
                model = "gpt-4o-mini",
                apiKey = "",
            ),
            ModelProfile(
                name = "Groq — Llama 3.3 70B",
                baseUrl = "https://api.groq.com/openai/v1",
                model = "llama-3.3-70b-versatile",
                apiKey = "",
            ),
        )

        fun listToJson(list: List<ModelProfile>): String {
            val arr = JSONArray()
            list.forEach { arr.put(it.toJson()) }
            return arr.toString()
        }

        fun listFromJson(s: String?): List<ModelProfile> {
            if (s.isNullOrBlank()) return defaults()
            return try {
                val arr = JSONArray(s)
                (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
                    .ifEmpty { defaults() }
            } catch (_: Exception) {
                defaults()
            }
        }
    }
}

/** ElevenLabs voice configuration. */
data class VoiceConfig(
    val apiKey: String = "",
    val voiceId: String = "",
    val modelId: String = "eleven_turbo_v2_5",
    val enabled: Boolean = true,
)

/** The full snapshot of user settings. */
data class AqilSettings(
    val profiles: List<ModelProfile> = ModelProfile.defaults(),
    val selectedProfileId: String = profiles.firstOrNull()?.id ?: "",
    val voice: VoiceConfig = VoiceConfig(),
    val agentEnabled: Boolean = true,
) {
    val activeProfile: ModelProfile?
        get() = profiles.firstOrNull { it.id == selectedProfileId } ?: profiles.firstOrNull()
}

/** A message in the chat transcript shown in the UI. */
data class ChatMessage(
    val role: Role,
    val text: String,
    val time: Long = System.currentTimeMillis(),
) {
    enum class Role { USER, ASSISTANT, SYSTEM, ACTION }
}
