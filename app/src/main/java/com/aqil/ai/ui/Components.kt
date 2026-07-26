package com.aqil.ai.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Icon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aqil.ai.ui.theme.BorderSubtle
import com.aqil.ai.ui.theme.Gold
import com.aqil.ai.ui.theme.GoldBrush
import com.aqil.ai.ui.theme.Navy750
import com.aqil.ai.ui.theme.Navy800
import com.aqil.ai.ui.theme.Navy900
import com.aqil.ai.ui.theme.Success
import com.aqil.ai.ui.theme.TextMuted
import com.aqil.ai.ui.theme.TextSecondary

/** A raised surface with a hairline border; the border turns gold when [selected]. */
@Composable
fun AqilCard(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    padding: Int = 16,
    content: @Composable () -> Unit,
) {
    val border by animateColorAsState(if (selected) Gold else BorderSubtle, label = "cardBorder")
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) Navy750 else Navy800)
            .border(1.dp, border, RoundedCornerShape(20.dp))
            .padding(padding.dp)
    ) { content() }
}

/** Gold gradient primary action. */
@Composable
fun PrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(GoldBrush)
            .alpha(if (enabled) 1f else 0.4f)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, tint = Navy900, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, color = Navy900, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

/** Outlined, quieter action. */
@Composable
fun GhostButton(
    text: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    val tint = if (accent) Gold else TextSecondary
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, if (accent) Gold.copy(alpha = 0.6f) else BorderSubtle, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, color = tint, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
    }
}

/** Section label with a small gold accent bar. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier.padding(top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(width = 3.dp, height = 14.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(GoldBrush)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text.uppercase(),
            color = TextMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
        )
    }
}

@Composable
fun StatusDot(active: Boolean, size: Int = 9) {
    val c by animateColorAsState(if (active) Success else TextMuted, label = "dot")
    Box(Modifier.size(size.dp).clip(CircleShape).background(c))
}
