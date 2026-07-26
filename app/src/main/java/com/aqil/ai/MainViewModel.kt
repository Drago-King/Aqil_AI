package com.aqil.ai

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aqil.ai.agent.AgentController
import com.aqil.ai.agent.AgentEvent
import com.aqil.ai.ai.AgentEngine
import com.aqil.ai.ai.OpenAiClient
import com.aqil.ai.data.AqilSettings
import com.aqil.ai.data.ChatMessage
import com.aqil.ai.data.ModelProfile
import com.aqil.ai.data.SettingsRepository
import com.aqil.ai.data.VoiceConfig
import com.aqil.ai.ocr.OcrEngine
import com.aqil.ai.voice.VoicePlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo: SettingsRepository = (app as AqilApp).settings
    private val openAi = OpenAiClient()
    private val engine = AgentEngine(openAi)
    private val voice = VoicePlayer(app)
    private val ctx = app.applicationContext

    /** Text OCR'd from the last image the user shared; used as context for follow-up questions. */
    private var documentContext: String = ""

    val settings: StateFlow<AqilSettings> =
        repo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AqilSettings())

    private val _messages = MutableStateFlow<List<ChatMessage>>(
        listOf(
            ChatMessage(
                ChatMessage.Role.ASSISTANT,
                "Hi, I'm Aqil. Tell me what to do — like \"open WhatsApp and message Jihan\" — " +
                    "by text or the mic. Enable screen control in Settings so I can tap and type for you."
            )
        )
    )
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _pendingConfirm = MutableStateFlow<String?>(null)
    val pendingConfirm: StateFlow<String?> = _pendingConfirm.asStateFlow()

    val agentConnected: Boolean get() = AgentController.isConnected

    // ---- chat / agent ----

    fun send(task: String) {
        val text = task.trim()
        if (text.isBlank() || _busy.value) return
        val profile = settings.value.activeProfile
        if (profile == null || profile.apiKey.isBlank()) {
            add(ChatMessage.Role.SYSTEM, "Add an API key in Settings first.")
            return
        }
        add(ChatMessage.Role.USER, text)
        _busy.value = true
        AgentController.reset()
        val s = settings.value

        viewModelScope.launch(Dispatchers.IO) {
            engine.run(
                profile = profile,
                task = text,
                contextText = documentContext.ifBlank { null },
                customInstructions = s.customInstructions.ifBlank { null },
                confirmRisky = s.confirmRisky,
            ) { event -> handleEvent(event) }
            _busy.value = false
        }
    }

    /** Answer a pending risky-action confirmation (from the Approve/No buttons). */
    fun confirm(approved: Boolean) {
        _pendingConfirm.value = null
        AgentController.answerConfirm(approved)
    }

    /** OCR an image the user picked from their gallery (e.g. a printed certificate). */
    fun readImage(uri: Uri) {
        if (_busy.value) return
        add(ChatMessage.Role.SYSTEM, "Reading the image…")
        _busy.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val text = OcrEngine.fromUri(ctx, uri)
                if (text.isBlank()) {
                    add(ChatMessage.Role.ASSISTANT, "I couldn't find readable text in that image.")
                } else {
                    documentContext = text.take(4000)
                    add(ChatMessage.Role.ASSISTANT, "Here's what I read:\n\n" + text.take(1500))
                    add(ChatMessage.Role.SYSTEM, "Ask me about it — e.g. \"what's the name on it?\" — or tell me to use it in a task.")
                }
            } catch (e: Exception) {
                add(ChatMessage.Role.SYSTEM, "⚠ Couldn't read that image: ${e.message}")
            } finally {
                _busy.value = false
            }
        }
    }

    /** Cancel the running task (from the in-app Stop or the floating stop button). */
    fun cancel() {
        _pendingConfirm.value = null
        AgentController.requestCancel()
        stopVoice()
    }

    private fun handleEvent(event: AgentEvent) {
        when (event) {
            is AgentEvent.Thinking ->
                add(ChatMessage.Role.SYSTEM, "…${event.text}")
            is AgentEvent.Step ->
                add(ChatMessage.Role.ACTION, "${event.action.name} → ${event.result}")
            is AgentEvent.Speak -> {
                add(ChatMessage.Role.ASSISTANT, event.text)
                speak(event.text)
            }
            is AgentEvent.Finish -> {
                add(ChatMessage.Role.ASSISTANT, event.summary)
                speak(event.summary)
            }
            is AgentEvent.Error ->
                add(ChatMessage.Role.SYSTEM, "⚠ ${event.message}")
            is AgentEvent.Confirm ->
                _pendingConfirm.value = event.prompt
        }
    }

    private fun speak(text: String) {
        val v = settings.value.voice
        if (!v.enabled) return
        viewModelScope.launch(Dispatchers.IO) { voice.speak(v, text) }
    }

    fun stopVoice() = voice.stop()

    private fun add(role: ChatMessage.Role, text: String) {
        _messages.value = _messages.value + ChatMessage(role, text)
    }

    // ---- settings mutations ----

    fun selectProfile(id: String) = viewModelScope.launch { repo.selectProfile(id) }

    fun upsertProfile(profile: ModelProfile) = viewModelScope.launch {
        val list = settings.value.profiles.toMutableList()
        val idx = list.indexOfFirst { it.id == profile.id }
        if (idx >= 0) list[idx] = profile else list.add(profile)
        repo.saveProfiles(list)
    }

    fun deleteProfile(id: String) = viewModelScope.launch {
        val list = settings.value.profiles.filterNot { it.id == id }
        repo.saveProfiles(list.ifEmpty { ModelProfile.defaults() })
    }

    fun saveVoice(v: VoiceConfig) = viewModelScope.launch { repo.saveVoice(v) }

    fun saveCustomInstructions(text: String) = viewModelScope.launch { repo.saveCustomInstructions(text) }

    fun setConfirmRisky(on: Boolean) = viewModelScope.launch { repo.setConfirmRisky(on) }

    fun testVoice() {
        val v = settings.value.voice
        viewModelScope.launch(Dispatchers.IO) {
            voice.speak(v, "Hello, this is Aqil. Voice is working.")
        }
    }

    override fun onCleared() {
        voice.release()
        super.onCleared()
    }
}
