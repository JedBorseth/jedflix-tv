package com.jedflix.tv.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.jedflix.tv.ui.theme.JedflixRed

private const val WORD = "JEDFLIX"
private const val LETTER_STAGGER = 0.09f
private const val LETTER_DURATION = 0.42f

/**
 * Red JEDFLIX wordmark. [progress] 0..1 drives the intro: each letter fades and scales into place
 * with a staggered start; 1f renders the resting wordmark.
 */
@Composable
fun JedflixWordmark(
    modifier: Modifier = Modifier,
    progress: Float = 1f,
    fontSize: TextUnit = 28.sp,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        WORD.forEachIndexed { index, letter ->
            val start = index * LETTER_STAGGER
            val raw = ((progress - start) / LETTER_DURATION).coerceIn(0f, 1f)
            val t = FastOutSlowInEasing.transform(raw)
            val glow = 0.85f * (1f - t) + 0.25f

            Text(
                text = letter.toString(),
                color = JedflixRed,
                fontSize = fontSize,
                fontWeight = FontWeight.Black,
                letterSpacing = (-1).sp,
                style = TextStyle(
                    shadow = Shadow(
                        color = JedflixRed.copy(alpha = glow),
                        offset = Offset.Zero,
                        blurRadius = 36f * (1f - t) + 10f,
                    ),
                ),
                modifier = Modifier.graphicsLayer {
                    alpha = t
                    val scale = 1.9f - 0.9f * t
                    scaleX = scale
                    scaleY = scale
                },
            )
        }
    }
}
