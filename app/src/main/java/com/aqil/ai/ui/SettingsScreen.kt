package com.aqil.ai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aqil.ai.MainViewModel
import com.aqil.ai.data.ModelProfile
import com.aqil.ai.data.VoiceConfig
import com.aqil.ai.ui.theme.BorderSubtle
import com.aqil.ai.ui.theme.Danger
import com.aqil.ai.ui.theme.Gold
import com.aqil.ai.ui.theme.Navy750
import com.aqil.ai.ui.theme.Navy800
import com.aqil.ai.ui.theme.Navy900
import com.aqil.ai.ui.theme.Success
import com.aqil.ai.ui.theme.TextMuted
import com.aqil.ai.ui.theme.TextPrimary
import com.aqil.ai.ui.theme.TextSecondary

@Composable
fun SettingsScreen(vm: MainViewModel, actions: SetupActions) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val scroll = rememberScrollState()

    var refresh by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) refresh++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    val accessibilityOn = remember(refresh) { actions.isAccessibilityOn() }
    val overlayOn = remember(refresh) { actions.isOverlayOn() }
    val bubbleOn = remember(refresh) { actions.isBubbleOn() }

    var editing by remember { mutableStateOf<ModelProfile?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxWidth().verticalScroll(scroll).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SectionHeader("Setup")

        StatusCard(
            title = "Screen control",
            subtitle = "Lets Aqil tap, type and scroll for you (Accessibility).",
            active = accessibilityOn,
        ) {
            if (accessibilityOn) GhostButton("Manage", onClick = actions.onOpenAccessibility)
            else PrimaryButton("Enable", onClick = actions.onOpenAccessibility)
        }

        StatusCard(
            title = "Floating bubble",
            subtitle = "A tap-anywhere bubble over other apps (Display over other apps).",
            active = bubbleOn,
        ) {
            when {
                !overlayOn -> PrimaryButton("Allow", onClick = actions.onRequestOverlay)
                bubbleOn -> GhostButton("Hide", onClick = actions.onToggleBubble)
                else -> PrimaryButton("Show", onClick = actions.onToggleBubble)
            }
        }

        SectionHeader("AI models & keys")
        settings.profiles.forEach { profile ->
            ProfileCard(
                profile = profile,
                selected = profile.id == settings.selectedProfileId,
                onSelect = { vm.selectProfile(profile.id) },
                onEdit = { editing = profile; showDialog = true },
                onDelete = { vm.deleteProfile(profile.id) },
            )
        }
        GhostButton(
            text = "Add model / base URL",
            modifier = Modifier.fillMaxWidth(),
            accent = true,
            icon = Icons.Filled.Add,
            onClick = {
                editing = ModelProfile(
                    name = "", baseUrl = "https://openrouter.ai/api/v1", model = "", apiKey = ""
                )
                showDialog = true
            }
        )

        SectionHeader("Voice (ElevenLabs)")
        VoiceCard(voice = settings.voice, onSave = { vm.saveVoice(it) }, onTest = { vm.testVoice() })

        Spacer(Modifier.size(2.dp))
        Text(
            "Aqil AI · v1.0 · crafted for you",
            color = TextMuted, fontSize = 12.sp, modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.size(28.dp))
    }

    if (showDialog && editing != null) {
        ProfileDialog(
            initial = editing!!,
            onDismiss = { showDialog = false; editing = null },
            onSave = { vm.upsertProfile(it); showDialog = false; editing = null }
        )
    }
}

@Composable
private fun StatusCard(
    title: String,
    subtitle: String,
    active: Boolean,
    button: @Composable () -> Unit,
) {
    AqilCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(active = active, size = 10)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text(subtitle, color = TextMuted, fontSize = 12.sp, lineHeight = 16.sp)
            }
            Spacer(Modifier.width(10.dp))
            button()
        }
    }
}

@Composable
private fun ProfileCard(
    profile: ModelProfile,
    selected: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    AqilCard(selected = selected) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (selected) Gold else Color.Transparent)
                    .border(1.5.dp, if (selected) Gold else BorderSubtle, CircleShape)
                    .clickable(onClick = onSelect),
                contentAlignment = Alignment.Center
            ) {
                if (selected) Icon(Icons.Filled.Check, null, tint = Navy900, modifier = Modifier.size(15.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f).clickable(onClick = onSelect)) {
                Text(
                    profile.name.ifBlank { "Unnamed model" },
                    color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp
                )
                Text(profile.model.ifBlank { "no model set" }, color = Gold, fontSize = 12.sp)
                Text(profile.baseUrl, color = TextMuted, fontSize = 11.sp)
                Text(
                    if (profile.apiKey.isBlank()) "no API key yet" else "key set",
                    color = if (profile.apiKey.isBlank()) Danger else Success, fontSize = 11.sp
                )
            }
            IconBtn(Icons.Filled.Edit, onEdit)
            IconBtn(Icons.Filled.Delete, onDelete, tint = Danger)
        }
    }
}

@Composable
private fun IconBtn(icon: ImageVector, onClick: () -> Unit, tint: Color = TextSecondary) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = tint, modifier = Modifier.size(19.dp)) }
}

@Composable
private fun VoiceCard(voice: VoiceConfig, onSave: (VoiceConfig) -> Unit, onTest: () -> Unit) {
    var key by remember(voice) { mutableStateOf(voice.apiKey) }
    var voiceId by remember(voice) { mutableStateOf(voice.voiceId) }
    var modelId by remember(voice) { mutableStateOf(voice.modelId) }
    var enabled by remember(voice) { mutableStateOf(voice.enabled) }

    AqilCard {
        Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Speak replies aloud", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    Text("Falls back to the phone voice if no key.", color = TextMuted, fontSize = 12.sp)
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = { enabled = it; onSave(voice.copy(enabled = it)) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Navy900,
                        checkedTrackColor = Gold,
                        uncheckedTrackColor = Navy750,
                        uncheckedBorderColor = BorderSubtle,
                    )
                )
            }
            Field("ElevenLabs API key", key) { key = it }
            Field("Voice ID", voiceId) { voiceId = it }
            Field("Model ID", modelId) { modelId = it }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton(
                    "Save voice",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onSave(VoiceConfig(key.trim(), voiceId.trim(), modelId.trim().ifBlank { "eleven_turbo_v2_5" }, enabled))
                    }
                )
                GhostButton("Test", modifier = Modifier.weight(1f), accent = true, onClick = onTest)
            }
        }
    }
}

@Composable
private fun ProfileDialog(initial: ModelProfile, onDismiss: () -> Unit, onSave: (ModelProfile) -> Unit) {
    var name by remember { mutableStateOf(initial.name) }
    var baseUrl by remember { mutableStateOf(initial.baseUrl) }
    var model by remember { mutableStateOf(initial.model) }
    var key by remember { mutableStateOf(initial.apiKey) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Navy800,
        titleContentColor = TextPrimary,
        title = { Text("Model connection", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Field("Name", name) { name = it }
                Field("Base URL", baseUrl) { baseUrl = it }
                Field("Model", model) { model = it }
                Field("API key", key) { key = it }
            }
        },
        confirmButton = {
            PrimaryButton("Save", onClick = {
                onSave(initial.copy(
                    name = name.trim().ifBlank { "Custom model" },
                    baseUrl = baseUrl.trim(), model = model.trim(), apiKey = key.trim()
                ))
            })
        },
        dismissButton = { GhostButton("Cancel", onClick = onDismiss) }
    )
}

@Composable
private fun Field(label: String, value: String, onValue: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label, color = TextMuted, fontSize = 12.sp) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Gold,
            unfocusedBorderColor = BorderSubtle,
            focusedContainerColor = Navy750,
            unfocusedContainerColor = Navy750,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            cursorColor = Gold,
        )
    )
}
