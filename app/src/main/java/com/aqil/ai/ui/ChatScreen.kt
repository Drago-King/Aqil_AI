package com.aqil.ai.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aqil.ai.MainViewModel
import com.aqil.ai.data.ChatMessage
import com.aqil.ai.ui.theme.AccentCyan
import com.aqil.ai.ui.theme.BorderSubtle
import com.aqil.ai.ui.theme.Danger
import com.aqil.ai.ui.theme.Gold
import com.aqil.ai.ui.theme.GoldBrush
import com.aqil.ai.ui.theme.Navy750
import com.aqil.ai.ui.theme.Navy800
import com.aqil.ai.ui.theme.Navy850
import com.aqil.ai.ui.theme.Navy900
import com.aqil.ai.ui.theme.TextMuted
import com.aqil.ai.ui.theme.TextPrimary
import com.aqil.ai.ui.theme.TextSecondary

private val SUGGESTIONS = listOf(
    "Open the calculator",
    "Open WhatsApp",
    "Take a screenshot",
    "Open Settings",
    "What can you do?",
)

@Composable
fun ChatScreen(vm: MainViewModel, actions: SetupActions) {
    val messages by vm.messages.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf("") }

    LaunchedEffect(messages.size, busy) {
        val target = messages.size - 1 + if (busy) 1 else 0
        if (target >= 0) listState.animateScrollToItem(target.coerceAtLeast(0))
    }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 14.dp)
        ) {
            items(messages) { MessageRow(it) }
            if (busy) item { TypingBubble() }
        }

        AnimatedVisibility(visible = messages.size <= 1 && !busy) {
            SuggestionRow(onPick = { vm.send(it) })
        }

        val listening by actions.listening
        val partial by actions.partial
        AnimatedVisibility(visible = listening, enter = fadeIn(), exit = fadeOut()) {
            ListeningBar(partial = partial)
        }

        InputBar(
            value = input,
            onValue = { input = it },
            listening = listening,
            busy = busy,
            onImage = actions.onPickImage,
            onMic = actions.onMic,
            onCancel = { vm.cancel() },
            onSend = { if (input.isNotBlank()) { vm.send(input); input = "" } }
        )
    }
}

@Composable
private fun MessageRow(msg: ChatMessage) {
    when (msg.role) {
        ChatMessage.Role.USER -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Box(
                Modifier
                    .widthIn(max = 300.dp)
                    .clip(RoundedCornerShape(18.dp, 18.dp, 6.dp, 18.dp))
                    .background(GoldBrush)
                    .padding(horizontal = 15.dp, vertical = 10.dp)
            ) { Text(msg.text, color = Navy900, fontSize = 15.sp, fontWeight = FontWeight.Medium) }
        }

        ChatMessage.Role.ASSISTANT -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Avatar()
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .widthIn(max = 300.dp)
                    .clip(RoundedCornerShape(6.dp, 18.dp, 18.dp, 18.dp))
                    .background(Navy800)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(6.dp, 18.dp, 18.dp, 18.dp))
                    .padding(horizontal = 15.dp, vertical = 11.dp)
            ) { Text(msg.text, color = TextPrimary, fontSize = 15.sp, lineHeight = 21.sp) }
        }

        ChatMessage.Role.ACTION -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(40.dp))
            Row(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Navy850)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(10.dp))
                    .padding(horizontal = 11.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(AccentCyan))
                Spacer(Modifier.width(8.dp))
                Text(msg.text, color = TextSecondary, fontSize = 12.sp)
            }
        }

        ChatMessage.Role.SYSTEM -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(msg.text, color = TextMuted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun Avatar() {
    Box(
        Modifier.size(30.dp).clip(CircleShape).background(Navy750)
            .border(1.dp, Gold.copy(alpha = 0.5f), CircleShape),
        contentAlignment = Alignment.Center
    ) { Text("A", color = Gold, fontWeight = FontWeight.Black, fontSize = 14.sp) }
}

@Composable
private fun TypingBubble() {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Avatar()
        Spacer(Modifier.width(8.dp))
        Row(
            Modifier
                .clip(RoundedCornerShape(6.dp, 18.dp, 18.dp, 18.dp))
                .background(Navy800)
                .border(1.dp, BorderSubtle, RoundedCornerShape(6.dp, 18.dp, 18.dp, 18.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val t = rememberInfiniteTransition(label = "typing")
            listOf(0, 160, 320).forEach { delay ->
                val a by t.animateFloat(
                    initialValue = 0.3f, targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        tween(560), RepeatMode.Reverse, initialStartOffset = StartOffset(delay)
                    ), label = "dot"
                )
                Box(Modifier.size(7.dp).alpha(a).clip(CircleShape).background(Gold))
            }
        }
    }
}

@Composable
private fun SuggestionRow(onPick: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SUGGESTIONS.forEach { s ->
            Box(
                Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Navy850)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(20.dp))
                    .clickable { onPick(s) }
                    .padding(horizontal = 14.dp, vertical = 9.dp)
            ) { Text(s, color = TextSecondary, fontSize = 13.sp) }
        }
    }
}

@Composable
private fun ListeningBar(partial: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val t = rememberInfiniteTransition(label = "listen")
        val s by t.animateFloat(
            1f, 1.35f, infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "pulse"
        )
        Box(Modifier.size(10.dp).scale(s).clip(CircleShape).background(Gold))
        Spacer(Modifier.width(10.dp))
        Text(
            partial.ifBlank { "Listening…" },
            color = if (partial.isBlank()) TextMuted else TextPrimary,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun InputBar(
    value: String,
    onValue: (String) -> Unit,
    listening: Boolean,
    busy: Boolean,
    onImage: () -> Unit,
    onMic: () -> Unit,
    onCancel: () -> Unit,
    onSend: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Navy850)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ImageButton(onClick = onImage)
        OutlinedTextField(
            value = value,
            onValueChange = onValue,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Ask Aqil…", color = TextMuted) },
            shape = RoundedCornerShape(16.dp),
            maxLines = 4,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Gold,
                unfocusedBorderColor = BorderSubtle,
                focusedContainerColor = Navy800,
                unfocusedContainerColor = Navy800,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                cursorColor = Gold,
            )
        )
        MicButton(listening = listening, onClick = onMic)
        if (busy) StopButton(onClick = onCancel) else SendButton(onClick = onSend)
    }
}

@Composable
private fun ImageButton(onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Navy800)
            .border(1.dp, BorderSubtle, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(Icons.Filled.Image, contentDescription = "Read a photo", tint = Gold) }
}

@Composable
private fun StopButton(onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Danger)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(Icons.Filled.Stop, contentDescription = "Stop", tint = Color.White) }
}

@Composable
private fun MicButton(listening: Boolean, onClick: () -> Unit) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(50.dp)) {
        if (listening) {
            val t = rememberInfiniteTransition(label = "ring")
            val s by t.animateFloat(1f, 1.7f, infiniteRepeatable(tween(900), RepeatMode.Restart), label = "s")
            val a by t.animateFloat(0.5f, 0f, infiniteRepeatable(tween(900), RepeatMode.Restart), label = "a")
            Box(Modifier.size(48.dp).scale(s).alpha(a).clip(CircleShape).background(Danger))
        }
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(if (listening) Danger else Navy800)
                .border(1.dp, if (listening) Color.Transparent else BorderSubtle, CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (listening) Icons.Filled.Stop else Icons.Filled.Mic,
                contentDescription = "Voice",
                tint = if (listening) Color.White else Gold
            )
        }
    }
}

@Composable
private fun SendButton(onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(GoldBrush)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(Icons.Filled.ArrowUpward, contentDescription = "Send", tint = Navy900) }
}
