package io.gh.kdbrian.ttac.ui.draw

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import io.gh.kdbrian.ttac.data.MarkStyle
import io.gh.kdbrian.ttac.game.Mark
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * Marks are described as polylines in unit space (centred on 0, roughly ±0.3) so that every style
 * can be "drawn in" the same way: by walking a fraction of the total stroke length.
 */
object MarkGeometry {
    private const val R = 0.3f

    fun strokes(mark: Mark, style: MarkStyle, seed: Int): List<List<Offset>> {
        val rnd = Random(seed * 7919 + mark.ordinal)
        val sketch = style == MarkStyle.SKETCH
        return when (mark) {
            Mark.X -> listOf(
                line(Offset(-R, -R), Offset(R, R), sketch, rnd),
                line(Offset(R, -R), Offset(-R, R), sketch, rnd),
            )
            Mark.O -> listOf(circle(sketch, rnd))
        }
    }

    private fun line(a: Offset, b: Offset, sketch: Boolean, rnd: Random): List<Offset> {
        val steps = 16
        if (!sketch) return List(steps + 1) { i -> lerp(a, b, i / steps.toFloat()) }
        val dir = b - a
        val len = hypot(dir.x, dir.y)
        val normal = Offset(-dir.y / len, dir.x / len)
        val bend = (rnd.nextFloat() - 0.5f) * 0.06f
        val wobblePhase = rnd.nextFloat() * 6f
        val overshootA = rnd.nextFloat() * 0.05f
        val overshootB = rnd.nextFloat() * 0.07f
        return List(steps + 1) { i ->
            val t = i / steps.toFloat()
            val along = -overshootA + t * (1f + overshootA + overshootB)
            val arc = bend * sin(PI.toFloat() * t)
            val wobble = 0.008f * sin(t * 13f + wobblePhase)
            lerp(a, b, along) + normal * (arc + wobble)
        }
    }

    private fun circle(sketch: Boolean, rnd: Random): List<Offset> {
        val steps = 72
        val start = if (sketch) rnd.nextFloat() * 2f * PI.toFloat() else -PI.toFloat() / 2f
        val sweep = if (sketch) (2f * PI.toFloat()) * (1.08f + rnd.nextFloat() * 0.06f) else 2f * PI.toFloat()
        val phase = rnd.nextFloat() * 6f
        val squash = if (sketch) 0.93f + rnd.nextFloat() * 0.1f else 1f
        return List(steps + 1) { i ->
            val t = i / steps.toFloat()
            val a = start + sweep * t
            // Sketch circles spiral slightly inward, like a hand that didn't quite close the loop.
            val r = if (sketch) R * (1.04f - 0.07f * t + 0.015f * sin(a * 3f + phase)) else R
            Offset(cos(a) * r, sin(a) * r * squash)
        }
    }

    fun length(points: List<Offset>): Float {
        var total = 0f
        for (i in 1 until points.size) total += (points[i] - points[i - 1]).getDistance()
        return total
    }

    /** The first [fraction] (by length) of a set of strokes, drawn in order. */
    fun partial(strokes: List<List<Offset>>, fraction: Float): List<List<Offset>> {
        if (fraction >= 1f) return strokes
        if (fraction <= 0f) return emptyList()
        var remaining = strokes.sumOf { length(it).toDouble() }.toFloat() * fraction
        val out = ArrayList<List<Offset>>()
        for (stroke in strokes) {
            if (remaining <= 0f) break
            val partialStroke = ArrayList<Offset>()
            partialStroke += stroke.first()
            for (i in 1 until stroke.size) {
                val seg = (stroke[i] - stroke[i - 1]).getDistance()
                if (seg <= remaining) {
                    partialStroke += stroke[i]
                    remaining -= seg
                } else {
                    partialStroke += lerp(stroke[i - 1], stroke[i], remaining / seg)
                    remaining = 0f
                    break
                }
            }
            out += partialStroke
        }
        return out
    }

    private fun lerp(a: Offset, b: Offset, t: Float) = Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
}

/**
 * Draws a mark centred at [center], [cell] pixels wide, [progress] of the way through its
 * stroke animation, with [pop] as the bouncy scale factor.
 */
fun DrawScope.drawMark(
    mark: Mark,
    style: MarkStyle,
    seed: Int,
    center: Offset,
    cell: Float,
    color: Color,
    strokeFraction: Float,
    progress: Float,
    pop: Float = 1f,
    alpha: Float = 1f,
    darkBackground: Boolean = true,
) {
    if (progress <= 0f) return
    val strokes = MarkGeometry.partial(MarkGeometry.strokes(mark, style, seed), progress)
    val scale = cell * pop
    val w = cell * strokeFraction

    fun path(offset: Offset = Offset.Zero): Path = Path().apply {
        for (s in strokes) {
            if (s.isEmpty()) continue
            moveTo(center.x + s[0].x * scale + offset.x, center.y + s[0].y * scale + offset.y)
            for (i in 1 until s.size) lineTo(center.x + s[i].x * scale + offset.x, center.y + s[i].y * scale + offset.y)
        }
    }

    fun stroke(width: Float) = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)

    when (style) {
        MarkStyle.NEON -> {
            val p = path()
            val glowAlpha = if (darkBackground) 1f else 0.6f
            drawPath(p, color.copy(alpha = 0.10f * alpha * glowAlpha), style = stroke(w * 3.4f))
            drawPath(p, color.copy(alpha = 0.20f * alpha * glowAlpha), style = stroke(w * 2.1f))
            drawPath(p, color.copy(alpha = alpha), style = stroke(w))
            drawPath(p, Color.White.copy(alpha = (if (darkBackground) 0.75f else 0.45f) * alpha), style = stroke(w * 0.32f))
        }
        MarkStyle.SOLID -> {
            val shadow = cell * 0.035f
            drawPath(path(Offset(shadow, shadow * 1.4f)), Color.Black.copy(alpha = 0.28f * alpha), style = stroke(w))
            drawPath(path(), color.copy(alpha = alpha), style = stroke(w))
        }
        MarkStyle.SKETCH -> {
            drawPath(path(), color.copy(alpha = 0.9f * alpha), style = stroke(w * 0.75f))
            drawPath(path(Offset(cell * 0.012f, cell * 0.016f)), color.copy(alpha = 0.45f * alpha), style = stroke(w * 0.45f))
        }
    }
}
