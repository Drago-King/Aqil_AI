package com.aqil.ai.ai

import com.aqil.ai.data.VoiceConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

class ElevenLabsClient {

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    private val json = "application/json".toMediaType()

    /**
     * Synthesizes [text] with ElevenLabs and writes an mp3 to [outFile].
     * Returns the file on success, or null if not configured / failed.
     */
    suspend fun synthesize(voice: VoiceConfig, text: String, outFile: File): File? =
        withContext(Dispatchers.IO) {
            if (voice.apiKey.isBlank() || voice.voiceId.isBlank() || text.isBlank()) return@withContext null

            val body = JSONObject().apply {
                put("text", text)
                put("model_id", voice.modelId.ifBlank { "eleven_turbo_v2_5" })
                put("voice_settings", JSONObject().apply {
                    put("stability", 0.5)
                    put("similarity_boost", 0.75)
                })
            }

            val url = "https://api.elevenlabs.io/v1/text-to-speech/${voice.voiceId}"
            val request = Request.Builder()
                .url(url)
                .header("xi-api-key", voice.apiKey)
                .header("accept", "audio/mpeg")
                .post(body.toString().toRequestBody(json))
                .build()

            try {
                http.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext null
                    val bytes = resp.body?.bytes() ?: return@withContext null
                    outFile.writeBytes(bytes)
                    outFile
                }
            } catch (_: Exception) {
                null
            }
        }
}
