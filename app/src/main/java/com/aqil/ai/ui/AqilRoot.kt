package com.aqil.ai.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.aqil.ai.MainViewModel
import com.aqil.ai.ui.theme.AppBackground
import com.aqil.ai.ui.theme.AqilLogo
import com.aqil.ai.ui.theme.BorderSubtle
import com.aqil.ai.ui.theme.GoldBrush
import com.aqil.ai.ui.theme.Navy850
import com.aqil.ai.ui.theme.Navy900
import com.aqil.ai.ui.theme.TextMuted
import com.aqil.ai.ui.theme.TextPrimary
import com.aqil.ai.ui.theme.TextSecondary

/** Bundle of setup callbacks provided by the Activity. */
class SetupActions(
    val onMic: () -> Unit,
    val listening: MutableState<Boolean>,
    val partial: MutableState<String>,
    val onOpenAccessibility: () -> Unit,
    val onRequestOverlay: () -> Unit,
    val onToggleBubble: () -> Unit,
    val isAccessibilityOn: () -> Boolean,
    val isOverlayOn: () -> Boolean,
    val isBubbleOn: () -> Boolean,
)

@Composable
fun AqilRoot(vm: MainViewModel, actions: SetupActions) {
    var tab by remember { mutableIntStateOf(0) }

    // Refresh permission-derived status whenever the app returns to the foreground.
    var refresh by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) refresh++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    val ready = remember(refresh) { actions.isAccessibilityOn() }

    Box(
        Modifier
            .fillMaxSize()
            .background(AppBackground)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
        ) {
            Header(ready = ready)
            Spacer(Modifier.height(6.dp))
            TabBar(selected = tab, onSelect = { tab = it })
            Spacer(Modifier.height(10.dp))
            Crossfade(targetState = tab, label = "tabContent", modifier = Modifier.weight(1f).fillMaxWidth()) { t ->
                when (t) {
                    0 -> ChatScreen(vm, actions)
                    else -> SettingsScreen(vm, actions)
                }
            }
        }
    }
}

@Composable
private fun Header(ready: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AqilLogo(size = 38.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Aqil AI",
                color = TextPrimary,
                fontWeight = FontWeight.Black,
                fontSize = 22.sp,
                letterSpacing = (-0.5).sp,
            )
            Text("personal phone agent", color = TextMuted, fontSize = 11.sp, letterSpacing = 0.5.sp)
        }
        StatusPill(ready)
    }
}

@Composable
private fun StatusPill(ready: Boolean) {
    Row(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Navy850)
            .border(1.dp, BorderSubtle, RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusDot(active = ready, size = 7)
        Spacer(Modifier.width(6.dp))
        Text(
            if (ready) "Active" else "Screen off",
            color = if (ready) TextSecondary else TextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun TabBar(selected: Int, onSelect: (Int) -> Unit) {
    val tabs = listOf("Assistant", "Settings")
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Navy850)
                .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
                .padding(5.dp)
        ) {
            val cellWidth = maxWidth / 2
            val indicatorX by animateDpAsState(
                if (selected == 0) 0.dp else cellWidth, label = "tabIndicator"
            )
            Box(
                Modifier
                    .width(cellWidth)
                    .height(38.dp)
                    .offset(x = indicatorX)
                    .clip(RoundedCornerShape(12.dp))
                    .background(GoldBrush)
            )
            Row(Modifier.fillMaxWidth()) {
                tabs.forEachIndexed { i, label ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(38.dp)
                            .clickable { onSelect(i) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            label,
                            color = if (i == selected) Navy900 else TextSecondary,
                            fontWeight = if (i == selected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 14.sp,
                        )
                    }
                }
            }
        }
    }
}
