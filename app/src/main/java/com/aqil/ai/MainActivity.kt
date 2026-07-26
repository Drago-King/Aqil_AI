package com.aqil.ai

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.mutableStateOf
import com.aqil.ai.agent.AqilAccessibilityService
import com.aqil.ai.overlay.FloatingBubbleService
import com.aqil.ai.ui.AqilRoot
import com.aqil.ai.ui.SetupActions
import com.aqil.ai.ui.theme.AqilTheme
import com.aqil.ai.voice.SpeechInput

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()
    private lateinit var speech: SpeechInput

    // live UI state for the mic
    private val listening = mutableStateOf(false)
    private val partial = mutableStateOf("")

    private val micPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) beginListening()
        }

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        speech = SpeechInput(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        val actions = SetupActions(
            onMic = ::onMicTapped,
            listening = listening,
            partial = partial,
            onOpenAccessibility = { openAccessibilitySettings() },
            onRequestOverlay = { requestOverlay() },
            onToggleBubble = ::toggleBubble,
            isAccessibilityOn = { isAccessibilityEnabled() },
            isOverlayOn = { canDrawOverlay() },
            isBubbleOn = { FloatingBubbleService.isRunning },
        )

        setContent {
            AqilTheme {
                AqilRoot(vm = vm, actions = actions)
            }
        }

        if (intent?.getBooleanExtra(EXTRA_START_VOICE, false) == true) {
            onMicTapped()
        }
    }

    // ---- voice ----

    private fun onMicTapped() {
        if (listening.value) {
            speech.stop()
            listening.value = false
            return
        }
        val granted = checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) beginListening() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun beginListening() {
        listening.value = true
        partial.value = ""
        speech.start(
            onPartial = { partial.value = it },
            onResult = { text ->
                partial.value = ""
                if (text.isNotBlank()) vm.send(text)
            },
            onError = { partial.value = "" },
            onEnd = { listening.value = false },
        )
    }

    // ---- setup helpers ----

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun requestOverlay() {
        if (!canDrawOverlay()) {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }
    }

    private fun toggleBubble() {
        if (!canDrawOverlay()) {
            requestOverlay()
            return
        }
        val intent = Intent(this, FloatingBubbleService::class.java)
        if (FloatingBubbleService.isRunning) {
            stopService(intent)
        } else {
            startForegroundService(intent)
        }
    }

    private fun canDrawOverlay(): Boolean = Settings.canDrawOverlays(this)

    private fun isAccessibilityEnabled(): Boolean {
        val expected = "$packageName/${AqilAccessibilityService::class.java.name}"
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        return enabled.split(':').any { it.equals(expected, true) }
    }

    override fun onDestroy() {
        speech.destroy()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_START_VOICE = "start_voice"
    }
}

/** Convenience for other components. */
fun Context.stopBubble() {
    stopService(Intent(this, FloatingBubbleService::class.java))
}
