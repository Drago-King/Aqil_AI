package com.aqil.ai.voice

import android.content.Context
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import com.aqil.ai.ai.ElevenLabsClient
import com.aqil.ai.data.VoiceConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * Speaks text. Uses ElevenLabs when a key + voice id are configured,
 * otherwise falls back to the phone's built-in text-to-speech.
 */
class VoicePlayer(private val context: Context) {

    private val eleven = ElevenLabsClient()
    private var player: MediaPlayer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
                ttsReady = true
            }
        }
    }

    suspend fun speak(voice: VoiceConfig, text: String) {
        if (text.isBlank()) return
        if (voice.enabled && voice.apiKey.isNotBlank() && voice.voiceId.isNotBlank()) {
            val file = File(context.cacheDir, "aqil_tts_${System.currentTimeMillis()}.mp3")
            val out = eleven.synthesize(voice, text, file)
            if (out != null) {
                playFile(out)
                return
            }
        }
        fallback(text)
    }

    private suspend fun playFile(file: File) = withContext(Dispatchers.Main) {
        stop()
        player = MediaPlayer().apply {
            setDataSource(file.absolutePath)
            setOnCompletionListener {
                it.release()
                file.delete()
            }
            prepare()
            start()
        }
    }

    private fun fallback(text: String) {
        if (ttsReady) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "aqil")
        }
    }

    fun stop() {
        player?.let { runCatching { it.stop(); it.release() } }
        player = null
        tts?.stop()
    }

    fun release() {
        stop()
        tts?.shutdown()
        tts = null
    }
}
