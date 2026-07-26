package com.aqil.ai.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text

/** Full-screen background: a soft navy glow at the top fading into near-black. */
val AppBackground = Brush.verticalGradient(
    0.0f to Color(0xFF0B2036),
    0.45f to BgBase,
    1.0f to Color(0xFF030A13),
)

/** Primary gold fill used on the main call-to-action surfaces. */
val GoldBrush = Brush.horizontalGradient(listOf(GoldBright, Gold, GoldDeep))

/** Vertical gold used for the logo mark. */
val GoldBrushVertical = Brush.verticalGradient(listOf(GoldBright, GoldDeep))

/** A faint gold ring used to imply a subtle glow around the brand mark. */
val GoldRing = Brush.radialGradient(listOf(Gold.copy(alpha = 0.35f), Color.Transparent))

/** The Aqil mark: a gold gradient disc with a monogram, wrapped in a soft glow ring. */
@Composable
fun AqilLogo(size: Dp = 40.dp) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size + 12.dp)) {
        Box(
            Modifier
                .size(size + 12.dp)
                .clip(CircleShape)
                .background(GoldRing)
        )
        Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(Navy850)
                .border(1.5.dp, GoldBrushVertical, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "A",
                fontSize = (size.value * 0.5f).sp,
                fontWeight = FontWeight.Black,
                color = Gold,
            )
        }
    }
}
