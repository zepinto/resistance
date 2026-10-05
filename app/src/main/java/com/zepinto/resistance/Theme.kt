package com.zepinto.resistance

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object Palette {
    val Ink = Color(0xFF0A0E1A)
    val Night = Color(0xFF16203D)
    val Blue = Color(0xFF3B82F6)
    val Red = Color(0xFFEF4444)
    val Gold = Color(0xFFFFC857)
    val Mint = Color(0xFF6EE7B7)
    val Aqua = Color(0xFF5CE1E6)
    val Lilac = Color(0xFFB69CFF)
    val avatars = listOf(Gold, Aqua, Mint, Lilac, Color(0xFFFF8A5C), Color(0xFFFF7AB6))
    fun avatar(index: Int) = avatars[index % avatars.size]
}

val Pill = RoundedCornerShape(50)
val Soft = RoundedCornerShape(22.dp)

/** Deep navy with a red glow in one corner and a blue one in the other: the two sides of the game. */
fun Modifier.noirBackground(): Modifier = this
    .background(Brush.verticalGradient(listOf(Palette.Ink, Palette.Night)))
    .drawBehind {
        drawCircle(Palette.Red.copy(alpha = 0.16f), radius = size.width * 0.55f, center = Offset(size.width * 0.98f, size.height * 0.02f))
        drawCircle(Palette.Blue.copy(alpha = 0.18f), radius = size.width * 0.6f, center = Offset(size.width * 0.02f, size.height * 0.98f))
    }

@Composable
fun Avatar(name: String, index: Int, size: Dp = 44.dp) {
    Box(Modifier.size(size).background(Palette.avatar(index), CircleShape), contentAlignment = Alignment.Center) {
        Text(
            name.firstOrNull()?.uppercase() ?: "?",
            color = Palette.Ink,
            fontWeight = FontWeight.Black,
            fontSize = (size.value * 0.46f).sp,
        )
    }
}

/** The game's mark: a domino mask, bobbing gently. */
@Composable
fun Mask(modifier: Modifier = Modifier, bob: Boolean = true, color: Color = Color.White, hole: Color = Palette.Night) {
    val transition = rememberInfiniteTransition(label = "mask")
    val lift by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (bob) 1f else 0f,
        animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "lift",
    )
    Canvas(modifier) {
        val s = size.minDimension
        translate(top = -lift * s * 0.04f) {
            val body = Path().apply {
                fillType = PathFillType.EvenOdd
                moveTo(s * 0.05f, s * 0.46f)
                cubicTo(s * 0.05f, s * 0.2f, s * 0.3f, s * 0.12f, s * 0.5f, s * 0.26f)
                cubicTo(s * 0.7f, s * 0.12f, s * 0.95f, s * 0.2f, s * 0.95f, s * 0.46f)
                cubicTo(s * 0.95f, s * 0.7f, s * 0.8f, s * 0.86f, s * 0.64f, s * 0.8f)
                cubicTo(s * 0.57f, s * 0.77f, s * 0.55f, s * 0.68f, s * 0.5f, s * 0.68f)
                cubicTo(s * 0.45f, s * 0.68f, s * 0.43f, s * 0.77f, s * 0.36f, s * 0.8f)
                cubicTo(s * 0.2f, s * 0.86f, s * 0.05f, s * 0.7f, s * 0.05f, s * 0.46f)
                close()
            }
            drawPath(body, color)
            drawOval(hole, Offset(s * 0.17f, s * 0.38f), Size(s * 0.26f, s * 0.2f))
            drawOval(hole, Offset(s * 0.57f, s * 0.38f), Size(s * 0.26f, s * 0.2f))
        }
    }
}

@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = Palette.Gold,
    content: Color = Palette.Ink,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = Pill,
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = content,
            disabledContainerColor = Color.White.copy(alpha = 0.12f),
            disabledContentColor = Color.White.copy(alpha = 0.4f),
        ),
        modifier = modifier.heightIn(min = 54.dp),
    ) { Text(text, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold) }
}

@Composable
fun Chip(text: String, color: Color, modifier: Modifier = Modifier) {
    Surface(shape = Pill, color = color.copy(alpha = 0.22f), modifier = modifier) {
        Text(
            text,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}
