package io.gh.kdbrian.ttac.ui.draw

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.gh.kdbrian.ttac.data.MarkStyle
import io.gh.kdbrian.ttac.game.Mark
import io.gh.kdbrian.ttac.ui.theme.LocalPalette
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

enum class Glyph { BACK, GEAR, TROPHY, FLAME, WIFI, PERSON, PEOPLE, PLUS, TRASH, REFRESH, BOT, CHECK, CLOSE, SOUND, VIBRATE, MEDAL, SNOW, QR, LOCK, SOUND_OFF, VIBRATE_OFF, LEFT, RIGHT, DOWN, DROP, PAUSE, PLAY, BULB, COPY }

/** Every icon in the app is a few strokes on a Canvas — no vector or bitmap assets. */
@Composable
fun GlyphIcon(glyph: Glyph, color: Color, modifier: Modifier = Modifier, size: Dp = 22.dp) {
    Canvas(modifier.size(size)) { drawGlyph(glyph, color) }
}

fun DrawScope.drawGlyph(glyph: Glyph, color: Color) {
    val s = size.minDimension
    val w = s * 0.11f
    val st = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun p(x: Float, y: Float) = Offset(x * s, y * s)
    fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(color, p(x1, y1), p(x2, y2), w, StrokeCap.Round)
    fun poly(vararg pts: Float, close: Boolean = false) = drawPath(Path().apply {
        moveTo(pts[0] * s, pts[1] * s)
        var i = 2
        while (i < pts.size) { lineTo(pts[i] * s, pts[i + 1] * s); i += 2 }
        if (close) close()
    }, color, style = st)

    when (glyph) {
        Glyph.BACK -> { poly(0.55f, 0.2f, 0.25f, 0.5f, 0.55f, 0.8f); line(0.27f, 0.5f, 0.8f, 0.5f) }
        Glyph.GEAR -> {
            drawCircle(color, s * 0.2f, center, style = st)
            repeat(8) { k ->
                val a = k * PI.toFloat() / 4f
                drawLine(color, center + Offset(cos(a), sin(a)) * (s * 0.3f), center + Offset(cos(a), sin(a)) * (s * 0.42f), w * 1.2f, StrokeCap.Round)
            }
        }
        Glyph.TROPHY -> {
            poly(0.3f, 0.18f, 0.7f, 0.18f, 0.66f, 0.46f, 0.5f, 0.58f, 0.34f, 0.46f, close = true)
            poly(0.3f, 0.24f, 0.16f, 0.26f, 0.2f, 0.4f, 0.33f, 0.44f)
            poly(0.7f, 0.24f, 0.84f, 0.26f, 0.8f, 0.4f, 0.67f, 0.44f)
            line(0.5f, 0.6f, 0.5f, 0.74f); line(0.32f, 0.82f, 0.68f, 0.82f)
        }
        Glyph.FLAME -> drawPath(Path().apply {
            moveTo(0.5f * s, 0.12f * s)
            cubicTo(0.78f * s, 0.38f * s, 0.84f * s, 0.62f * s, 0.7f * s, 0.78f * s)
            cubicTo(0.6f * s, 0.9f * s, 0.4f * s, 0.9f * s, 0.3f * s, 0.78f * s)
            cubicTo(0.18f * s, 0.62f * s, 0.26f * s, 0.46f * s, 0.38f * s, 0.36f * s)
            cubicTo(0.4f * s, 0.5f * s, 0.46f * s, 0.54f * s, 0.5f * s, 0.54f * s)
            cubicTo(0.44f * s, 0.38f * s, 0.46f * s, 0.24f * s, 0.5f * s, 0.12f * s)
            close()
        }, color, style = st)
        Glyph.WIFI -> {
            listOf(0.14f, 0.26f, 0.38f).forEach { r ->
                drawArc(color, 225f, 90f, false, p(0.5f, 0.78f) - Offset(r * s, r * s), Size(r * s * 2, r * s * 2), style = st)
            }
            drawCircle(color, w * 0.9f, p(0.5f, 0.78f))
        }
        Glyph.PERSON -> {
            drawCircle(color, s * 0.15f, p(0.5f, 0.32f), style = st)
            drawArc(color, 180f, 180f, false, p(0.22f, 0.58f), Size(s * 0.56f, s * 0.5f), style = st)
        }
        Glyph.PEOPLE -> {
            drawCircle(color, s * 0.11f, p(0.35f, 0.35f), style = st)
            drawCircle(color, s * 0.11f, p(0.68f, 0.35f), style = st)
            drawArc(color, 180f, 180f, false, p(0.14f, 0.6f), Size(s * 0.42f, s * 0.36f), style = st)
            drawArc(color, 180f, 180f, false, p(0.47f, 0.6f), Size(s * 0.42f, s * 0.36f), style = st)
        }
        Glyph.PLUS -> { line(0.5f, 0.2f, 0.5f, 0.8f); line(0.2f, 0.5f, 0.8f, 0.5f) }
        Glyph.TRASH -> {
            line(0.2f, 0.28f, 0.8f, 0.28f); poly(0.4f, 0.28f, 0.42f, 0.16f, 0.58f, 0.16f, 0.6f, 0.28f)
            poly(0.28f, 0.34f, 0.32f, 0.84f, 0.68f, 0.84f, 0.72f, 0.34f)
        }
        Glyph.REFRESH -> {
            drawArc(color, -60f, 290f, false, p(0.2f, 0.2f), Size(s * 0.6f, s * 0.6f), style = st)
            poly(0.62f, 0.12f, 0.68f, 0.26f, 0.54f, 0.3f)
        }
        Glyph.BOT -> {
            drawRoundRect(color, p(0.2f, 0.3f), Size(s * 0.6f, s * 0.48f), androidx.compose.ui.geometry.CornerRadius(s * 0.1f), style = st)
            drawCircle(color, w * 0.9f, p(0.38f, 0.52f)); drawCircle(color, w * 0.9f, p(0.62f, 0.52f))
            line(0.5f, 0.3f, 0.5f, 0.16f); drawCircle(color, w * 0.8f, p(0.5f, 0.14f))
        }
        Glyph.CHECK -> poly(0.2f, 0.52f, 0.42f, 0.74f, 0.8f, 0.28f)
        Glyph.CLOSE -> { line(0.25f, 0.25f, 0.75f, 0.75f); line(0.75f, 0.25f, 0.25f, 0.75f) }
        Glyph.SOUND -> {
            poly(0.16f, 0.4f, 0.3f, 0.4f, 0.48f, 0.22f, 0.48f, 0.78f, 0.3f, 0.6f, 0.16f, 0.6f, close = true)
            drawArc(color, -45f, 90f, false, p(0.42f, 0.32f), Size(s * 0.24f, s * 0.36f), style = st)
            drawArc(color, -50f, 100f, false, p(0.4f, 0.2f), Size(s * 0.44f, s * 0.6f), style = st)
        }
        Glyph.QR -> {
            for ((x, y) in listOf(0.14f to 0.14f, 0.58f to 0.14f, 0.14f to 0.58f)) {
                drawRoundRect(color, p(x, y), Size(s * 0.28f, s * 0.28f), androidx.compose.ui.geometry.CornerRadius(s * 0.05f), style = Stroke(w * 0.8f))
                drawRect(color, p(x + 0.09f, y + 0.09f), Size(s * 0.1f, s * 0.1f))
            }
            drawRect(color, p(0.62f, 0.62f), Size(s * 0.1f, s * 0.1f)); drawRect(color, p(0.76f, 0.76f), Size(s * 0.1f, s * 0.1f))
            drawRect(color, p(0.76f, 0.6f), Size(s * 0.08f, s * 0.08f)); drawRect(color, p(0.6f, 0.78f), Size(s * 0.08f, s * 0.08f))
        }
        Glyph.LOCK -> {
            drawRoundRect(color, p(0.24f, 0.46f), Size(s * 0.52f, s * 0.4f), androidx.compose.ui.geometry.CornerRadius(s * 0.08f))
            drawArc(color, 180f, 180f, false, p(0.32f, 0.2f), Size(s * 0.36f, s * 0.5f), style = st)
            line(0.32f, 0.45f, 0.32f, 0.47f); line(0.68f, 0.45f, 0.68f, 0.47f)
        }
        Glyph.SOUND_OFF -> { drawGlyph(Glyph.SOUND, color.copy(alpha = 0.45f)); line(0.14f, 0.14f, 0.86f, 0.86f) }
        Glyph.VIBRATE_OFF -> { drawGlyph(Glyph.VIBRATE, color.copy(alpha = 0.45f)); line(0.14f, 0.14f, 0.86f, 0.86f) }
        Glyph.LEFT -> poly(0.62f, 0.2f, 0.32f, 0.5f, 0.62f, 0.8f)
        Glyph.RIGHT -> poly(0.38f, 0.2f, 0.68f, 0.5f, 0.38f, 0.8f)
        Glyph.DOWN -> poly(0.2f, 0.38f, 0.5f, 0.68f, 0.8f, 0.38f)
        Glyph.DROP -> { poly(0.24f, 0.2f, 0.5f, 0.46f, 0.76f, 0.2f); poly(0.24f, 0.44f, 0.5f, 0.7f, 0.76f, 0.44f); line(0.22f, 0.84f, 0.78f, 0.84f) }
        Glyph.PAUSE -> { line(0.36f, 0.24f, 0.36f, 0.76f); line(0.64f, 0.24f, 0.64f, 0.76f) }
        Glyph.PLAY -> drawPath(Path().apply { moveTo(0.3f * s, 0.2f * s); lineTo(0.8f * s, 0.5f * s); lineTo(0.3f * s, 0.8f * s); close() }, color)
        Glyph.BULB -> {
            drawCircle(color, s * 0.22f, p(0.5f, 0.4f), style = st)
            line(0.4f, 0.72f, 0.6f, 0.72f); line(0.43f, 0.84f, 0.57f, 0.84f)
            line(0.44f, 0.6f, 0.44f, 0.68f); line(0.56f, 0.6f, 0.56f, 0.68f)
        }
        Glyph.SNOW -> {
            line(0.5f, 0.14f, 0.5f, 0.86f); line(0.19f, 0.32f, 0.81f, 0.68f); line(0.19f, 0.68f, 0.81f, 0.32f)
            poly(0.4f, 0.18f, 0.5f, 0.26f, 0.6f, 0.18f); poly(0.4f, 0.82f, 0.5f, 0.74f, 0.6f, 0.82f)
        }
        Glyph.MEDAL -> {
            poly(0.3f, 0.1f, 0.44f, 0.42f); poly(0.7f, 0.1f, 0.56f, 0.42f)
            drawCircle(color, s * 0.2f, p(0.5f, 0.64f), style = st)
            drawCircle(color, w * 0.8f, p(0.5f, 0.64f))
        }
        Glyph.COPY -> {
            drawRoundRect(color, p(0.34f, 0.34f), Size(s * 0.46f, s * 0.5f), androidx.compose.ui.geometry.CornerRadius(s * 0.08f), style = st)
            poly(0.22f, 0.62f, 0.22f, 0.2f, 0.58f, 0.2f)
        }
        Glyph.VIBRATE -> {
            drawRoundRect(color, p(0.34f, 0.18f), Size(s * 0.32f, s * 0.64f), androidx.compose.ui.geometry.CornerRadius(s * 0.06f), style = st)
            line(0.16f, 0.36f, 0.16f, 0.64f); line(0.84f, 0.36f, 0.84f, 0.64f)
        }
    }
}

/**
 * Full-bleed animated backdrop: a slow glow plus drifting, rotating ghost X/O marks.
 * Drawn behind the system bars (edge-to-edge).
 */
@Composable
fun AnimatedBackdrop(
    xColor: Color,
    oColor: Color,
    modifier: Modifier = Modifier,
    /** When set (during a game), the whole screen washes toward this colour. */
    tint: Color? = null,
    /** -1 = glow from the left (✕ side), 1 = from the right (◯ side), 0 = centred. */
    tintSide: Float = 0f,
) {
    val palette = LocalPalette.current
    val t by rememberInfiniteTransition(label = "bg").animateFloat(
        0f, 1f, infiniteRepeatable(tween(60_000, easing = LinearEasing)), label = "t",
    )
    val tintColor by animateColorAsState(tint ?: palette.bgGlow, tween(650), label = "tint")
    val tintAmount by animateFloatAsState(if (tint != null) 1f else 0f, spring(dampingRatio = 0.8f, stiffness = 120f), label = "amount")
    val side by animateFloatAsState(tintSide, spring(dampingRatio = 0.55f, stiffness = 90f), label = "side")
    val glyphs = rememberFloaters()
    Canvas(modifier) {
        drawRect(Brush.verticalGradient(listOf(palette.bgTop, palette.bg)))
        val dark = palette.isDark
        // Organic blobs creeping in from the corners.
        val tau = 2 * PI.toFloat()
        drawBlob(Offset(size.width * 1.02f, size.height * -0.02f), size.width * 0.55f, t * tau, 0, palette.blobs[0].copy(alpha = if (dark) 0.22f else 0.45f), palette.blobs[3].copy(alpha = 0f))
        drawBlob(Offset(size.width * -0.08f, size.height * 0.46f), size.width * 0.38f, t * tau + 2f, 1, palette.blobs[1].copy(alpha = if (dark) 0.16f else 0.35f), palette.blobs[1].copy(alpha = 0f))
        drawBlob(Offset(size.width * 1.05f, size.height * 1.0f), size.width * 0.6f, t * tau + 4f, 2, palette.blobs[2].copy(alpha = if (dark) 0.22f else 0.4f), palette.blobs[0].copy(alpha = 0f))
        // Player wash: a vertical gradient over the whole screen, strongest at the top.
        if (tintAmount > 0.001f) {
            drawRect(
                Brush.verticalGradient(
                    0f to tintColor.copy(alpha = (if (dark) 0.42f else 0.30f) * tintAmount),
                    0.55f to tintColor.copy(alpha = (if (dark) 0.16f else 0.12f) * tintAmount),
                    1f to tintColor.copy(alpha = (if (dark) 0.28f else 0.20f) * tintAmount),
                )
            )
        }
        // Main glow drifts on its own, and swings toward the active player's side during a game.
        val drift = Offset(size.width * (0.5f + 0.25f * sin(t * 2 * PI.toFloat())), size.height * (0.25f + 0.1f * cos(t * 4 * PI.toFloat())))
        val anchor = Offset(size.width * (0.5f + 0.42f * side), size.height * (0.12f + 0.03f * sin(t * 8 * PI.toFloat())))
        val glowC = drift + (anchor - drift) * tintAmount
        val glowR = size.maxDimension * (0.6f + 0.15f * tintAmount)
        val glowColor = androidx.compose.ui.graphics.lerp(palette.bgGlow, tintColor, tintAmount)
        drawCircle(Brush.radialGradient(listOf(glowColor.copy(alpha = if (dark) 0.7f else 0.8f), Color.Transparent), glowC, glowR), glowR, glowC)
        val glow2 = Offset(size.width * (0.3f + 0.2f * cos(t * 2 * PI.toFloat())), size.height * (0.85f + 0.05f * sin(t * 6 * PI.toFloat())))
        val glow2Color = androidx.compose.ui.graphics.lerp(palette.accent2, tintColor, tintAmount)
        drawCircle(Brush.radialGradient(listOf(glow2Color.copy(alpha = 0.18f + 0.1f * tintAmount), Color.Transparent), glow2, size.maxDimension * 0.5f), size.maxDimension * 0.5f, glow2)

        // A starfield of ✕ and ◯: each star fades in, twinkles and fades out, then reappears
        // somewhere new. Periods divide the 60 s loop so nothing jumps when it wraps.
        val seconds = t * 60f
        val peakAlpha = if (dark) 0.55f else 0.42f
        for (s in glyphs) {
            val cycleF = (seconds + s.offset * s.period) / s.period
            val cycle = cycleF.toInt()
            val local = cycleF - cycle
            val glow = sin(PI.toFloat() * local).let { it * it }
            if (glow < 0.02f) continue
            // New spot every cycle, derived from the star and cycle so it's stable while visible.
            val rnd = Random(s.seed * 7919 + cycle * 104729)
            val pos = Offset(size.width * rnd.nextFloat(), size.height * rnd.nextFloat())
            val cell = s.size.dp.toPx() * (0.7f + 0.3f * glow)
            val color = if (s.mark == Mark.X) xColor else oColor
            rotate(s.spin * 25f * (local - 0.5f), pos) {
                drawMark(s.mark, MarkStyle.NEON, s.seed, pos, cell, color, 0.14f, 1f, alpha = peakAlpha * glow, darkBackground = dark)
            }
            // A glint at the brightest moment.
            if (glow > 0.8f) {
                val g = (glow - 0.8f) / 0.2f
                val len = cell * 0.55f * g
                val glint = Color.White.copy(alpha = 0.5f * g * peakAlpha)
                drawLine(glint, pos - Offset(len, 0f), pos + Offset(len, 0f), 1.2.dp.toPx(), StrokeCap.Round)
                drawLine(glint, pos - Offset(0f, len), pos + Offset(0f, len), 1.2.dp.toPx(), StrokeCap.Round)
            }
        }
    }
}

/** A soft, wobbling blob: a circle whose radius is perturbed by a few slow sine waves. */
private fun DrawScope.drawBlob(center: Offset, radius: Float, phase: Float, seed: Int, inner: Color, outer: Color) {
    val path = Path()
    val steps = 64
    for (i in 0..steps) {
        val a = i / steps.toFloat() * 2f * PI.toFloat()
        val r = radius * (1f + 0.09f * sin(a * 3f + phase + seed) + 0.06f * sin(a * 5f - phase * 1.3f + seed * 2) + 0.04f * cos(a * 2f + phase * 0.7f))
        val p = Offset(center.x + cos(a) * r, center.y + sin(a) * r)
        if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
    }
    path.close()
    drawPath(path, Brush.radialGradient(listOf(inner, inner.copy(alpha = inner.alpha * 0.6f), outer), center, radius * 1.15f))
}

private class Floater(val mark: Mark, val offset: Float, val period: Float, val size: Float, val spin: Float, val seed: Int)

@Composable
private fun rememberFloaters(): List<Floater> = androidx.compose.runtime.remember {
    val r = Random(42)
    // Periods (seconds) all divide 60, so the starfield loops seamlessly.
    val periods = floatArrayOf(3f, 4f, 5f, 6f, 10f)
    List(26) {
        Floater(
            mark = if (it % 2 == 0) Mark.X else Mark.O,
            offset = r.nextFloat(),
            period = periods[r.nextInt(periods.size)],
            // Mostly tiny stars, the odd bigger one.
            size = if (r.nextFloat() < 0.15f) 26f + r.nextFloat() * 14f else 9f + r.nextFloat() * 11f,
            spin = if (r.nextBoolean()) 1f else -1f,
            seed = it,
        )
    }
}
