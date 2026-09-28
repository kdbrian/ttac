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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import io.gh.kdbrian.ttac.data.ArcadeGame
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Colours for each tetromino (by ordinal) — shared by the game and its artwork. */
val BlockColors = listOf(
    Color(0xFF33E1FF), Color(0xFFFFE45C), Color(0xFF9B6BFF), Color(0xFF3DFFA8),
    Color(0xFFFF5470), Color(0xFF4D7CFF), Color(0xFFFFA53D),
)

val HoneyLight = Color(0xFFFFD65C)
val HoneyDark = Color(0xFFE08A00)

fun ArcadeGame.tones(): Pair<Color, Color> = when (this) {
    ArcadeGame.BLOCKS -> Color(0xFF7FE7FF) to Color(0xFF7C4DF0)
    ArcadeGame.HIVE -> HoneyLight to HoneyDark
}

/** A glossy block: glow, gradient face and a top sheen. */
fun DrawScope.drawBlock(topLeft: Offset, cell: Float, color: Color, alpha: Float = 1f, glow: Boolean = true) {
    val inset = cell * 0.06f
    val size = Size(cell - inset * 2, cell - inset * 2)
    val tl = topLeft + Offset(inset, inset)
    val r = CornerRadius(cell * 0.22f)
    if (glow) drawRoundRect(color.copy(alpha = 0.28f * alpha), tl - Offset(inset, inset), Size(cell, cell), CornerRadius(cell * 0.3f))
    drawRoundRect(Brush.verticalGradient(listOf(lerp(color, Color.White, 0.35f), color, lerp(color, Color.Black, 0.2f)), tl.y, tl.y + size.height), tl, size, r, alpha = alpha)
    drawRoundRect(Color.White.copy(alpha = 0.45f * alpha), tl + Offset(size.width * 0.18f, size.height * 0.1f), Size(size.width * 0.64f, size.height * 0.14f), CornerRadius(cell))
}

/** Pointy-top hexagon path. */
fun hexPath(center: Offset, radius: Float): Path = Path().apply {
    for (i in 0 until 6) {
        val a = PI.toFloat() / 180f * (60f * i - 30f)
        val p = Offset(center.x + radius * cos(a), center.y + radius * sin(a))
        if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
    }
    close()
}

/** Little animated emblem for an arcade game (home tiles and unlock celebration). */
@Composable
fun ArcadeArt(game: ArcadeGame, locked: Boolean, modifier: Modifier = Modifier) {
    val t by rememberInfiniteTransition(label = "arcade").animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "t")
    Canvas(modifier) {
        val alpha = if (locked) 0.35f else 1f
        when (game) {
            ArcadeGame.BLOCKS -> {
                val cell = size.minDimension / 4.4f
                val ox = (size.width - cell * 4) / 2
                val oy = (size.height - cell * 4) / 2
                // A settled stack plus a T piece bobbing down into its slot.
                val stack = listOf(0 to 3, 1 to 3, 3 to 3, 0 to 2, 3 to 2)
                stack.forEachIndexed { i, (x, y) -> drawBlock(Offset(ox + x * cell, oy + y * cell), cell, BlockColors[(i + 3) % 7], alpha) }
                val fall = if (locked) 0f else (sin(t * 2 * PI.toFloat()) * 0.5f + 0.5f) * cell * 0.5f
                for ((x, y) in listOf(1 to 0, 0 to 1, 1 to 1, 2 to 1)) drawBlock(Offset(ox + (x + 0.5f) * cell, oy + y * cell + fall), cell, BlockColors[2], alpha)
            }
            ArcadeGame.HIVE -> {
                val r = size.minDimension / 6.4f
                val c = center
                val w = sqrt(3f) * r
                val cells = listOf(Offset(0f, 0f)) + (0 until 6).map { i ->
                    val a = PI.toFloat() / 3f * i
                    Offset(cos(a) * w, sin(a) * w)
                }
                cells.forEachIndexed { i, o ->
                    val pulse = if (locked) 0f else (sin((t + i / 7f) * 2 * PI.toFloat()) * 0.5f + 0.5f)
                    val p = hexPath(c + o, r * 0.94f)
                    drawPath(p, Brush.verticalGradient(listOf(lerp(HoneyLight, Color.White, 0.25f * pulse), HoneyDark), c.y + o.y - r, c.y + o.y + r), alpha = alpha)
                    drawPath(p, Color.White.copy(alpha = 0.35f * alpha), style = Stroke(r * 0.06f))
                }
            }
        }
        if (locked) {
            val s = size.minDimension * 0.3f
            // Outline first so the padlock reads on light and dark art without dimming anything.
            drawRoundRect(Color(0xFF1A0B3D), center + Offset(-s * 0.5f, -s * 0.1f), Size(s * 1.0f, s * 0.8f), CornerRadius(s * 0.18f))
            drawArc(Color(0xFF1A0B3D), 180f, 180f, false, center + Offset(-s * 0.35f, -s * 0.55f), Size(s * 0.7f, s * 0.9f), style = Stroke(s * 0.24f))
            drawRoundRect(Color.White, center + Offset(-s * 0.45f, -s * 0.05f), Size(s * 0.9f, s * 0.7f), CornerRadius(s * 0.14f))
            drawArc(Color.White, 180f, 180f, false, center + Offset(-s * 0.3f, -s * 0.5f), Size(s * 0.6f, s * 0.8f), style = Stroke(s * 0.13f))
        }
    }
}
