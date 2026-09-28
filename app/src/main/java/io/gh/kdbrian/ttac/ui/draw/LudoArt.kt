package io.gh.kdbrian.ttac.ui.draw

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import io.gh.kdbrian.ttac.game.LudoBoard
import io.gh.kdbrian.ttac.game.LudoColor
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

val LudoColor.tone: Color get() = Color(argb)
val LudoColor.light: Color get() = lerp(tone, Color.White, 0.35f)
val LudoColor.dark: Color get() = lerp(tone, Color.Black, 0.35f)

private val Wood = Color(0xFF4A2511)
private val Copper = Color(0xFF8B4A1C)
private val Gold = Color(0xFFE8B84A)
private val Parchment = Color(0xFFF7E9C3)
private val ParchmentEdge = Color(0xFFE7CD91)
private val HoleInk = Color(0xFF5A381C)

/**
 * Where everything sits on the board, in pixels, for a square of side [side] centred in the canvas.
 * Shared by drawing and tap handling so they always agree.
 */
class LudoGeometry(val center: Offset, side: Float) {
    val radius = side / 2f * 0.985f
    /** Grid cell size: the 15-cell cross fits inside the frame's lobes. */
    val cell = radius * 0.86f * 0.98f / 7.5f
    private val origin = center - Offset(7.5f * cell, 7.5f * cell)

    fun cellCenter(c: Int, r: Int) = origin + Offset((c + 0.5f) * cell, (r + 0.5f) * cell)

    /** Centre of each colour's rosette base, in the quadrant between its arms (blue TL, red TR, green BR, yellow BL). */
    fun rosette(color: LudoColor): Offset = when (color) {
        LudoColor.BLUE -> origin + Offset(3.1f * cell, 3.1f * cell)
        LudoColor.RED -> origin + Offset(11.9f * cell, 3.1f * cell)
        LudoColor.GREEN -> origin + Offset(11.9f * cell, 11.9f * cell)
        LudoColor.YELLOW -> origin + Offset(3.1f * cell, 11.9f * cell)
    }

    /** The four base holes: above, right, below and left of the rosette centre. */
    fun baseSlot(color: LudoColor, i: Int): Offset {
        val d = 0.78f * cell
        return rosette(color) + when (i) { 0 -> Offset(0f, -d); 1 -> Offset(d, 0f); 2 -> Offset(0f, d); else -> Offset(-d, 0f) }
    }

    /** Finished pawns gather just inside the centre, on their own colour's side. */
    fun homeSlot(color: LudoColor, i: Int): Offset {
        val c = cellCenter(7, 7)
        val side = when (color) {
            LudoColor.BLUE -> Offset(-1f, 0f); LudoColor.RED -> Offset(0f, -1f)
            LudoColor.GREEN -> Offset(1f, 0f); LudoColor.YELLOW -> Offset(0f, 1f)
        }
        val across = Offset(-side.y, side.x)
        return c + side * (cell * 0.95f) + across * ((i - 1.5f) * cell * 0.42f)
    }

    fun spot(color: LudoColor, progress: Int, pawn: Int): Offset = when {
        progress < 0 -> baseSlot(color, pawn)
        progress >= LudoBoard.HOME -> homeSlot(color, pawn)
        else -> LudoBoard.cellOf(color, progress)!!.let { (c, r) -> cellCenter(c, r) }
    }

    /**
     * Position for a pawn at a *fractional* progress while it animates, with a hop between squares.
     * Returns the point and how high it is (0..1) for the shadow.
     */
    fun animated(color: LudoColor, progress: Float, pawn: Int): Pair<Offset, Float> {
        val lo = floor(progress).toInt()
        val t = progress - lo
        val a = spot(color, lo, pawn)
        val b = spot(color, (lo + 1).coerceAtMost(LudoBoard.HOME), pawn)
        val hop = sin(PI.toFloat() * t)
        val p = Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t - hop * cell * 0.45f)
        return p to hop
    }
}

// ---- The board --------------------------------------------------------------------------------

/** The scalloped frame: four big lobes on the axes and pointed ogee tips on the diagonals. */
private fun framePath(c: Offset, r: Float, scale: Float): Path = Path().apply {
    val steps = 240
    for (i in 0..steps) {
        val th = i / steps.toFloat() * 2f * PI.toFloat()
        val lobe = 0.9f + 0.07f * cos(4f * th)
        val tip = 0.13f * max(0f, -cos(4f * th)).pow(7)
        val rr = r * scale * (lobe + tip)
        val p = Offset(c.x + cos(th) * rr, c.y + sin(th) * rr)
        if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
    }
    close()
}

private fun flowerPath(c: Offset, r: Float, petals: Int, depth: Float, rotate: Float = 0f): Path = Path().apply {
    val steps = 160
    for (i in 0..steps) {
        val th = i / steps.toFloat() * 2f * PI.toFloat()
        val rr = r * (1f - depth + depth * (0.5f + 0.5f * cos(petals * (th + rotate))))
        val p = Offset(c.x + cos(th) * rr, c.y + sin(th) * rr)
        if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
    }
    close()
}

private fun star(c: Offset, outer: Float, inner: Float, points: Int = 5): Path = Path().apply {
    for (i in 0 until points * 2) {
        val a = -PI.toFloat() / 2 + i * PI.toFloat() / points
        val rr = if (i % 2 == 0) outer else inner
        val p = Offset(c.x + cos(a) * rr, c.y + sin(a) * rr)
        if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
    }
    close()
}

/** A carved hole: a dark well with a lighter lip on its lower edge. */
private fun DrawScope.hole(at: Offset, r: Float, fill: Color, rim: Color = Color.White.copy(alpha = 0.55f)) {
    drawCircle(Color.Black.copy(alpha = 0.18f), r * 1.12f, at + Offset(0f, r * 0.12f))
    drawCircle(Brush.radialGradient(listOf(lerp(fill, Color.Black, 0.35f), fill), at - Offset(0f, r * 0.3f), r * 1.3f), r, at)
    drawArc(rim, 20f, 140f, false, at - Offset(r, r), Size(r * 2, r * 2), style = Stroke(r * 0.18f, cap = StrokeCap.Round))
}

/** Draws the static board (frame, parchment, track, lanes, rosettes). Cached — it only redraws on resize. */
fun Modifier.ludoBoardBackground(): Modifier = drawWithCache {
    val side = size.minDimension
    val c = Offset(size.width / 2f, size.height / 2f)
    val g = LudoGeometry(c, side)
    val r = g.radius
    val frameOuter = framePath(c, r, 1f)
    val frameBand = framePath(c, r, 0.955f)
    val stitch = framePath(c, r, 0.925f)
    val parchment = framePath(c, r, 0.9f)
    onDrawBehind {
        // A wide wooden table band behind the board, like the photo's backdrop rail.
        val bandH = side * 0.3f
        drawRect(Brush.verticalGradient(listOf(Color(0xFF5B2E12), Color(0xFF7A3E17), Color(0xFF4A2410)), c.y - bandH / 2, c.y + bandH / 2), Offset(0f, c.y - bandH / 2), Size(size.width, bandH))
        drawRect(Gold.copy(alpha = 0.5f), Offset(0f, c.y - bandH / 2), Size(size.width, 2f))
        drawRect(Gold.copy(alpha = 0.5f), Offset(0f, c.y + bandH / 2 - 2f), Size(size.width, 2f))

        // Frame: shadow, dark wood, copper band, gold stitching, parchment.
        drawPath(framePath(c + Offset(0f, side * 0.012f), r, 1f), Color.Black.copy(alpha = 0.45f))
        drawPath(frameOuter, Brush.radialGradient(listOf(Color(0xFF6B3517), Wood), c, r))
        drawPath(frameBand, Brush.linearGradient(listOf(Color(0xFFB0622A), Copper, Color(0xFF6E3413)), c - Offset(r, r), c + Offset(r, r)))
        drawPath(stitch, Color(0xFFFFF1D0), style = Stroke(side * 0.006f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(side * 0.018f, side * 0.012f))))
        drawPath(parchment, Brush.radialGradient(listOf(Parchment, Parchment, ParchmentEdge), c, r * 0.95f))

        // A faint eight-point star watermark in the middle of the field.
        drawPath(star(c, g.cell * 5.2f, g.cell * 3.4f, 8), ParchmentEdge.copy(alpha = 0.55f))

        val hr = g.cell * 0.3f
        // The loop: carved holes, safe squares marked with a gold star, starts ringed in their colour.
        LudoBoard.loop.forEachIndexed { i, (col, row) ->
            val p = g.cellCenter(col, row)
            val startOf = LudoColor.entries.firstOrNull { it.start == i }
            when {
                startOf != null -> { hole(p, hr, startOf.tone); drawCircle(startOf.light, hr * 1.45f, p, style = Stroke(g.cell * 0.07f)) }
                i in LudoBoard.safe -> { hole(p, hr, HoleInk); drawPath(star(p, hr * 1.6f, hr * 0.7f), Gold.copy(alpha = 0.9f), style = Stroke(g.cell * 0.05f)) }
                else -> hole(p, hr, HoleInk)
            }
        }
        // Home lanes in each colour.
        for (color in LudoColor.entries) for ((col, row) in LudoBoard.lane(color)) hole(g.cellCenter(col, row), hr, color.tone)

        // Centre medallion where the die lands.
        val mid = g.cellCenter(7, 7)
        drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF4D8), ParchmentEdge), mid, g.cell * 1.35f), g.cell * 1.35f, mid)
        drawCircle(Gold, g.cell * 1.35f, mid, style = Stroke(g.cell * 0.09f))
        drawCircle(Wood.copy(alpha = 0.6f), g.cell * 1.2f, mid, style = Stroke(g.cell * 0.03f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(g.cell * 0.12f, g.cell * 0.1f))))

        // Rosette bases.
        for (color in LudoColor.entries) {
            val rc = g.rosette(color)
            val rr = g.cell * 1.75f
            drawPath(flowerPath(rc + Offset(0f, rr * 0.06f), rr, 8, 0.22f), Color.Black.copy(alpha = 0.25f))
            drawPath(flowerPath(rc, rr, 8, 0.22f), Brush.radialGradient(listOf(color.light, color.tone, color.dark), rc, rr))
            drawPath(flowerPath(rc, rr * 0.8f, 8, 0.25f, PI.toFloat() / 8), Color.White.copy(alpha = 0.9f), style = Stroke(g.cell * 0.05f))
            drawCircle(Parchment, rr * 0.62f, rc)
            drawCircle(color.dark, rr * 0.62f, rc, style = Stroke(g.cell * 0.05f))
            for (i in 0..3) hole(g.baseSlot(color, i), hr * 0.95f, color.dark)
            hole(rc, hr * 0.7f, color.tone)
        }
    }
}

/** A glossy pawn with a soft shadow; [lift] (0..1) raises it off the board mid-hop. */
fun DrawScope.drawPawn(at: Offset, cell: Float, color: LudoColor, lift: Float = 0f, glow: Float = 0f) {
    val r = cell * 0.34f
    val shadowAt = at + Offset(0f, r * 0.9f + lift * cell * 0.45f)
    drawOval(Color.Black.copy(alpha = 0.35f * (1f - lift * 0.5f)), Offset(shadowAt.x - r * 0.9f, shadowAt.y - r * 0.3f), Size(r * 1.8f, r * 0.6f))
    if (glow > 0f) drawCircle(Gold.copy(alpha = 0.55f * glow), r * (1.55f + 0.2f * glow), at, style = Stroke(cell * 0.08f))
    drawCircle(Brush.radialGradient(listOf(color.light, color.tone, color.dark), at - Offset(r * 0.35f, r * 0.4f), r * 1.5f), r, at)
    drawCircle(Color.White.copy(alpha = 0.85f), r * 0.28f, at - Offset(r * 0.35f, r * 0.38f))
    drawCircle(Color(0xFF2A160A).copy(alpha = 0.6f), r, at, style = Stroke(cell * 0.04f))
}

// ---- The desert palace ------------------------------------------------------------------------

/**
 * A golden palace at dusk, painted procedurally: sky, dunes, walls with arches, domes, minarets and palms.
 * The scene is cached; only the drifting dust sparkles animate.
 */
@Composable
fun PalaceBackdrop(modifier: Modifier = Modifier) {
    val t by rememberInfiniteTransition(label = "dust").animateFloat(0f, 1f, infiniteRepeatable(tween(24_000, easing = LinearEasing)), label = "t")
    val motes = remember { Random(7).let { r -> List(40) { floatArrayOf(r.nextFloat(), r.nextFloat(), r.nextFloat()) } } }
    Box(modifier) {
        Box(Modifier.fillMaxSize().drawWithCache {
            val w = size.width
            val h = size.height
            onDrawBehind { drawPalace(w, h) }
        })
        Canvas(Modifier.fillMaxSize()) {
            for (m in motes) {
                val y = ((m[1] - t * (0.3f + m[2] * 0.4f)) % 1f + 1f) % 1f
                val x = m[0] + 0.02f * sin((t * 6f + m[2] * 10f) * PI.toFloat())
                val a = 0.35f * sin(PI.toFloat() * y)
                drawCircle(Color(0xFFFFE3A0).copy(alpha = a), 1.5f + m[2] * 2.5f, Offset(x * size.width, y * size.height))
            }
        }
    }
}

private fun DrawScope.drawPalace(w: Float, h: Float) {
    // Dusk sky → warm horizon → shadowed ground.
    drawRect(Brush.verticalGradient(0f to Color(0xFF2B1508), 0.35f to Color(0xFF6B3413), 0.55f to Color(0xFFC77D2E), 0.62f to Color(0xFF8A4A18), 1f to Color(0xFF2A1407)))
    // Sun glow behind the central dome.
    val sun = Offset(w * 0.5f, h * 0.2f)
    drawCircle(Brush.radialGradient(listOf(Color(0xFFFFD27A).copy(alpha = 0.55f), Color.Transparent), sun, w * 0.7f), w * 0.7f, sun)

    val base = h * 0.3f          // wall top line
    val wallBottom = h * 0.62f
    val stone = Brush.verticalGradient(listOf(Color(0xFFE2A548), Color(0xFFB9772B), Color(0xFF7A4617)), base, wallBottom)

    // Minarets at the edges and inner towers.
    fun tower(cx: Float, width: Float, top: Float) {
        drawRect(stone, Offset(cx - width / 2, top), Size(width, wallBottom - top))
        // Gallery ring and onion cap.
        drawRect(Color(0xFF8C5220), Offset(cx - width * 0.65f, top + width * 0.9f), Size(width * 1.3f, width * 0.18f))
        val cap = Path().apply {
            moveTo(cx - width * 0.55f, top)
            cubicTo(cx - width * 0.7f, top - width * 0.6f, cx - width * 0.1f, top - width * 0.8f, cx, top - width * 1.3f)
            cubicTo(cx + width * 0.1f, top - width * 0.8f, cx + width * 0.7f, top - width * 0.6f, cx + width * 0.55f, top)
            close()
        }
        drawPath(cap, Brush.verticalGradient(listOf(Color(0xFFFFD27A), Color(0xFFC98A2E)), top - width * 1.3f, top))
        drawLine(Color(0xFFFFE6A8), Offset(cx, top - width * 1.3f), Offset(cx, top - width * 1.7f), width * 0.08f, StrokeCap.Round)
        // Slit windows, lit.
        for (k in 0..2) drawRoundRect(Color(0xFFFFC96B).copy(alpha = 0.8f), Offset(cx - width * 0.12f, top + width * (1.5f + k * 1.1f)), Size(width * 0.24f, width * 0.6f), CornerRadius(width * 0.12f))
    }
    tower(w * 0.08f, w * 0.07f, h * 0.12f)
    tower(w * 0.92f, w * 0.07f, h * 0.12f)
    tower(w * 0.27f, w * 0.09f, h * 0.2f)
    tower(w * 0.73f, w * 0.09f, h * 0.2f)

    // Main wall with crenellations and a row of lit arches.
    drawRect(stone, Offset(0f, base), Size(w, wallBottom - base))
    val merlon = w / 28f
    for (i in 0 until 28 step 2) drawRect(Color(0xFFD89A42), Offset(i * merlon, base - merlon * 0.6f), Size(merlon, merlon * 0.6f))
    val arches = 9
    for (i in 0 until arches) {
        val cx = w * (i + 0.5f) / arches
        val aw = w / arches * 0.46f
        val top = base + (wallBottom - base) * 0.35f
        val arch = Path().apply {
            moveTo(cx - aw / 2, wallBottom - (wallBottom - base) * 0.12f)
            lineTo(cx - aw / 2, top + aw * 0.5f)
            quadraticTo(cx - aw / 2, top, cx, top - aw * 0.35f)
            quadraticTo(cx + aw / 2, top, cx + aw / 2, top + aw * 0.5f)
            lineTo(cx + aw / 2, wallBottom - (wallBottom - base) * 0.12f)
            close()
        }
        drawPath(arch, Brush.verticalGradient(listOf(Color(0xFFFFD58A), Color(0xFF8A4516)), top, wallBottom))
    }

    // Central great dome.
    val dc = Offset(w * 0.5f, base)
    val dr = w * 0.2f
    drawRect(stone, Offset(dc.x - dr * 0.8f, base - dr * 0.5f), Size(dr * 1.6f, dr * 0.5f))
    val dome = Path().apply {
        moveTo(dc.x - dr, base - dr * 0.45f)
        cubicTo(dc.x - dr * 1.15f, base - dr * 1.4f, dc.x - dr * 0.25f, base - dr * 1.6f, dc.x, base - dr * 2.1f)
        cubicTo(dc.x + dr * 0.25f, base - dr * 1.6f, dc.x + dr * 1.15f, base - dr * 1.4f, dc.x + dr, base - dr * 0.45f)
        close()
    }
    drawPath(dome, Brush.linearGradient(listOf(Color(0xFFFFE08E), Color(0xFFE0A33C), Color(0xFF9A5B1E)), Offset(dc.x - dr, base - dr * 2f), Offset(dc.x + dr, base)))
    drawLine(Color(0xFFFFEDB8), Offset(dc.x, base - dr * 2.1f), Offset(dc.x, base - dr * 2.5f), dr * 0.05f, StrokeCap.Round)
    drawCircle(Color(0xFFFFEDB8), dr * 0.06f, Offset(dc.x, base - dr * 2.5f))

    // Palms framing the scene.
    fun palm(x: Float, lean: Float, height: Float) {
        val baseY = h * 0.64f
        val top = Offset(x + lean, baseY - height)
        val trunk = Path().apply { moveTo(x, baseY); quadraticTo(x + lean * 0.2f, baseY - height * 0.5f, top.x, top.y) }
        drawPath(trunk, Color(0xFF2E1706), style = Stroke(w * 0.018f, cap = StrokeCap.Round))
        for (k in 0 until 7) {
            val a = -PI.toFloat() * (0.1f + 0.8f * k / 6f)
            val len = height * 0.42f
            val tip = top + Offset(cos(a) * len, sin(a) * len * 0.55f + len * 0.35f)
            val frond = Path().apply { moveTo(top.x, top.y); quadraticTo(top.x + cos(a) * len * 0.6f, top.y + sin(a) * len * 0.6f - len * 0.1f, tip.x, tip.y) }
            drawPath(frond, Color(0xFF203A12), style = Stroke(w * 0.012f, cap = StrokeCap.Round))
        }
    }
    palm(w * 0.03f, w * 0.05f, h * 0.3f)
    palm(w * 0.17f, -w * 0.03f, h * 0.24f)
    palm(w * 0.97f, -w * 0.05f, h * 0.3f)
    palm(w * 0.83f, w * 0.03f, h * 0.22f)

    // Dunes in front, falling into shadow toward the bottom.
    val dunes = Path().apply {
        moveTo(0f, h * 0.64f)
        cubicTo(w * 0.25f, h * 0.6f, w * 0.45f, h * 0.7f, w * 0.7f, h * 0.65f)
        cubicTo(w * 0.85f, h * 0.62f, w * 0.95f, h * 0.66f, w, h * 0.64f)
        lineTo(w, h); lineTo(0f, h); close()
    }
    drawPath(dunes, Brush.verticalGradient(listOf(Color(0xFF9C5A1F), Color(0xFF4A250C), Color(0xFF1E0F05)), h * 0.6f, h))
}

// ---- Small art ----------------------------------------------------------------------------------

fun DrawScope.drawGem(c: Offset, r: Float) {
    val p = Path().apply {
        moveTo(c.x - r, c.y - r * 0.25f); lineTo(c.x - r * 0.5f, c.y - r * 0.75f); lineTo(c.x + r * 0.5f, c.y - r * 0.75f)
        lineTo(c.x + r, c.y - r * 0.25f); lineTo(c.x, c.y + r); close()
    }
    drawPath(p, Brush.linearGradient(listOf(Color(0xFFB8F1FF), Color(0xFF3AA8E8), Color(0xFF1E5FB8)), c - Offset(r, r), c + Offset(r, r)))
    drawLine(Color.White.copy(alpha = 0.7f), Offset(c.x - r * 0.5f, c.y - r * 0.25f), Offset(c.x, c.y + r * 0.9f), r * 0.08f)
    drawLine(Color.White.copy(alpha = 0.5f), Offset(c.x - r, c.y - r * 0.25f), Offset(c.x + r, c.y - r * 0.25f), r * 0.08f)
}

fun DrawScope.drawCoin(c: Offset, r: Float) {
    drawCircle(Color(0xFF9A5B12), r, c + Offset(0f, r * 0.1f))
    drawCircle(Brush.radialGradient(listOf(Color(0xFFFFE9A8), Color(0xFFF2B53A), Color(0xFFC07F17)), c - Offset(r * 0.3f, r * 0.3f), r * 1.4f), r, c)
    drawCircle(Color(0xFFFFF3C8), r * 0.72f, c, style = Stroke(r * 0.1f))
    // A little "$" made of two arcs and a bar.
    drawArc(Color(0xFF8A5210), 180f, 250f, false, Offset(c.x - r * 0.28f, c.y - r * 0.45f), Size(r * 0.56f, r * 0.45f), style = Stroke(r * 0.13f, cap = StrokeCap.Round))
    drawArc(Color(0xFF8A5210), 0f, 250f, false, Offset(c.x - r * 0.28f, c.y), Size(r * 0.56f, r * 0.45f), style = Stroke(r * 0.13f, cap = StrokeCap.Round))
    drawLine(Color(0xFF8A5210), Offset(c.x, c.y - r * 0.6f), Offset(c.x, c.y + r * 0.6f), r * 0.1f, StrokeCap.Round)
}

/** A die face with pips; [tumble] (degrees) tilts it while it rolls. */
fun DrawScope.drawDie(c: Offset, s: Float, value: Int, tumble: Float = 0f) {
    rotateAround(c, tumble) {
        drawRoundRect(Color.Black.copy(alpha = 0.35f), Offset(c.x - s / 2, c.y - s / 2 + s * 0.08f), Size(s, s), CornerRadius(s * 0.2f))
        drawRoundRect(Brush.linearGradient(listOf(Color.White, Color(0xFFF1E6CF)), c - Offset(s / 2, s / 2), c + Offset(s / 2, s / 2)), Offset(c.x - s / 2, c.y - s / 2), Size(s, s), CornerRadius(s * 0.2f))
        drawRoundRect(Gold, Offset(c.x - s / 2, c.y - s / 2), Size(s, s), CornerRadius(s * 0.2f), style = Stroke(s * 0.04f))
        val o = s * 0.27f
        val pips = when (value) {
            1 -> listOf(0f to 0f)
            2 -> listOf(-1f to -1f, 1f to 1f)
            3 -> listOf(-1f to -1f, 0f to 0f, 1f to 1f)
            4 -> listOf(-1f to -1f, 1f to -1f, -1f to 1f, 1f to 1f)
            5 -> listOf(-1f to -1f, 1f to -1f, 0f to 0f, -1f to 1f, 1f to 1f)
            else -> listOf(-1f to -1f, 1f to -1f, -1f to 0f, 1f to 0f, -1f to 1f, 1f to 1f)
        }
        for ((x, y) in pips) drawCircle(if (value == 1) Color(0xFFC62828) else Color(0xFF3A1E0C), s * 0.085f, c + Offset(x * o, y * o))
    }
}

private inline fun DrawScope.rotateAround(c: Offset, degrees: Float, block: DrawScope.() -> Unit) {
    drawContext.transform.rotate(degrees, c)
    block()
    drawContext.transform.rotate(-degrees, c)
}

/** Ornate avatar frame: gold ring, bead studs and an optional pulsing turn glow. */
fun DrawScope.drawAvatarFrame(c: Offset, r: Float, accent: Color, glow: Float) {
    if (glow > 0f) drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.6f * glow), Color.Transparent), c, r * 1.6f), r * 1.6f, c)
    drawCircle(Brush.linearGradient(listOf(Color(0xFFFFE9A8), Gold, Color(0xFF9A6A1A)), c - Offset(r, r), c + Offset(r, r)), r, c, style = Stroke(r * 0.14f))
    drawCircle(Color(0xFF3A1E0C), r * 0.9f, c, style = Stroke(r * 0.05f))
    for (i in 0 until 12) {
        val a = i * PI.toFloat() / 6f
        drawCircle(Color(0xFFFFF1C8), r * 0.045f, c + Offset(cos(a) * r, sin(a) * r))
    }
}
