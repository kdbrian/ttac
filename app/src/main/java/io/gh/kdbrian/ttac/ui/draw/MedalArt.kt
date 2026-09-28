package io.gh.kdbrian.ttac.ui.draw

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import io.gh.kdbrian.ttac.data.Medal
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Light and dark tone for each medal. */
fun Medal.tones(): Pair<Color, Color> = when (this) {
    Medal.BRONZE -> Color(0xFFF0A36B) to Color(0xFF9C5327)
    Medal.SILVER -> Color(0xFFF2F5FA) to Color(0xFF8E9AB0)
    Medal.GOLD -> Color(0xFFFFE07A) to Color(0xFFD08A12)
    Medal.PLATINUM -> Color(0xFFD6F8FF) to Color(0xFF4FAECB)
    Medal.DIAMOND -> Color(0xFFE6D6FF) to Color(0xFF7C4DF0)
    Medal.GRIT_I, Medal.GRIT_II, Medal.GRIT_III -> Color(0xFFA9C4FF) to Color(0xFF3550A8)
    Medal.COMEBACK -> Color(0xFF9DFFD2) to Color(0xFF12925D)
}

/**
 * A medal drawn entirely on Canvas: ribbon, rimmed disc, emblem and a sweeping shine.
 * Locked medals render as a dim, dashed silhouette.
 */
@Composable
fun MedalArt(medal: Medal, earned: Boolean, modifier: Modifier = Modifier) {
    val shine by rememberInfiniteTransition(label = "shine").animateFloat(
        -0.6f, 1.6f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "s",
    )
    Canvas(modifier) { drawMedal(medal, earned, shine) }
}

fun DrawScope.drawMedal(medal: Medal, earned: Boolean, shine: Float) {
    val (light, dark) = medal.tones()
    val s = size.minDimension
    val c = Offset(size.width / 2, size.height * 0.58f)
    val r = s * 0.34f
    val alpha = if (earned) 1f else 0.28f

    // Ribbon tails.
    val ribbon = if (earned) listOf(Color(0xFFFF3D7F), Color(0xFF9B6BFF)) else listOf(Color.Gray, Color.DarkGray)
    fun tail(x0: Float, x1: Float, color: Color) {
        val p = Path().apply {
            moveTo(c.x + x0 * s, s * 0.02f)
            lineTo(c.x + (x0 + 0.16f) * s, s * 0.02f)
            lineTo(c.x + x1 * s + 0.08f * s, c.y - r * 0.4f)
            lineTo(c.x + x1 * s - 0.08f * s, c.y - r * 0.4f)
            close()
        }
        drawPath(p, color.copy(alpha = alpha))
    }
    tail(-0.3f, -0.08f, ribbon[0])
    tail(0.14f, 0.08f, ribbon[1])

    // Disc with a bevelled rim.
    if (earned) drawCircle(dark.copy(alpha = 0.35f), r * 1.18f, c + Offset(0f, s * 0.03f))
    drawCircle(Brush.linearGradient(listOf(light, dark), c - Offset(r, r), c + Offset(r, r)), r, c, alpha = alpha)
    drawCircle(Brush.linearGradient(listOf(dark, light), c - Offset(r, r), c + Offset(r, r)), r * 0.8f, c, alpha = alpha)
    if (!earned) {
        drawCircle(Color.Gray.copy(alpha = 0.6f), r * 1.02f, c, style = Stroke(s * 0.02f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(s * 0.05f, s * 0.04f))))
    }

    // Emblem.
    val ink = lerp(dark, Color.Black, 0.35f).copy(alpha = alpha)
    val stroke = Stroke(s * 0.045f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    when (medal) {
        Medal.BRONZE, Medal.SILVER, Medal.GOLD, Medal.PLATINUM, Medal.DIAMOND -> {
            drawPath(star(c, r * 0.5f, r * 0.22f), ink)
            // Pips under the star show the tier.
            val tier = medal.ordinal + 1
            for (i in 0 until tier) {
                val x = c.x + (i - (tier - 1) / 2f) * r * 0.2f
                drawCircle(ink, s * 0.018f, Offset(x, c.y + r * 0.6f))
            }
        }
        Medal.GRIT_I, Medal.GRIT_II, Medal.GRIT_III -> {
            val shield = Path().apply {
                moveTo(c.x, c.y - r * 0.55f)
                lineTo(c.x + r * 0.42f, c.y - r * 0.38f)
                lineTo(c.x + r * 0.36f, c.y + r * 0.2f)
                lineTo(c.x, c.y + r * 0.55f)
                lineTo(c.x - r * 0.36f, c.y + r * 0.2f)
                lineTo(c.x - r * 0.42f, c.y - r * 0.38f)
                close()
            }
            drawPath(shield, ink, style = stroke)
            val chevrons = medal.ordinal - Medal.GRIT_I.ordinal + 1
            for (i in 0 until chevrons) {
                val y = c.y - r * 0.12f + i * r * 0.2f
                drawPath(Path().apply { moveTo(c.x - r * 0.2f, y); lineTo(c.x, y + r * 0.12f); lineTo(c.x + r * 0.2f, y) }, ink, style = stroke)
            }
        }
        Medal.COMEBACK -> {
            drawArc(ink, 150f, 250f, false, c - Offset(r * 0.42f, r * 0.42f), androidx.compose.ui.geometry.Size(r * 0.84f, r * 0.84f), style = stroke)
            val tip = Offset(c.x + cos(40f * PI.toFloat() / 180f) * r * 0.42f, c.y + sin(40f * PI.toFloat() / 180f) * r * 0.42f)
            drawPath(Path().apply { moveTo(tip.x - r * 0.22f, tip.y - r * 0.02f); lineTo(tip.x, tip.y); lineTo(tip.x + r * 0.02f, tip.y - r * 0.24f) }, ink, style = stroke)
        }
    }

    // Shine sweep.
    if (earned) {
        val disc = Path().apply { addOval(androidx.compose.ui.geometry.Rect(c, r)) }
        clipPath(disc) {
            val x = c.x - r + shine * r * 2
            drawLine(Color.White.copy(alpha = 0.55f), Offset(x - r * 0.5f, c.y + r), Offset(x + r * 0.5f, c.y - r), r * 0.28f)
        }
        if (medal == Medal.DIAMOND) {
            // Diamond medals sparkle.
            for (k in 0..2) {
                val a = shine * 3f + k * 2.1f
                val p = c + Offset(cos(a) * r * 1.1f, sin(a) * r * 1.1f)
                val len = s * 0.04f * (0.5f + 0.5f * sin(a * 2f))
                drawLine(Color.White, p - Offset(len, 0f), p + Offset(len, 0f), s * 0.012f, StrokeCap.Round)
                drawLine(Color.White, p - Offset(0f, len), p + Offset(0f, len), s * 0.012f, StrokeCap.Round)
            }
        }
    }
}

private fun star(c: Offset, outer: Float, inner: Float): Path = Path().apply {
    for (i in 0 until 10) {
        val a = -PI.toFloat() / 2 + i * PI.toFloat() / 5
        val rr = if (i % 2 == 0) outer else inner
        val p = Offset(c.x + cos(a) * rr, c.y + sin(a) * rr)
        if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
    }
    close()
}
