package io.gh.kdbrian.ttac.ui.draw

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import io.gh.kdbrian.ttac.data.MarkStyle
import io.gh.kdbrian.ttac.game.Board
import io.gh.kdbrian.ttac.game.Mark
import io.gh.kdbrian.ttac.game.WinLine
import io.gh.kdbrian.ttac.ui.theme.LocalPalette
import io.gh.kdbrian.ttac.ui.theme.heatAt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

data class MarkLook(
    val xColor: Color,
    val oColor: Color,
    val style: MarkStyle,
    val strokeFraction: Float,
    val animSpeed: Float,
) {
    fun colorOf(mark: Mark) = if (mark == Mark.X) xColor else oColor
    fun duration(baseMs: Int) = (baseMs / animSpeed.coerceIn(0.25f, 4f)).toInt()
}

/**
 * The whole game board, drawn on one Canvas. [roundKey] resets every animation when a new round
 * starts, so the grid re-draws itself and marks animate in from scratch.
 */
@Composable
fun BoardCanvas(
    board: Board,
    roundKey: Int,
    win: WinLine?,
    look: MarkLook,
    threats: Map<Mark, Set<Int>>?,
    heatmap: FloatArray?,
    enabled: Boolean,
    onCell: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    val n = board.size
    val scope = rememberCoroutineScope()

    val grid = remember(roundKey, n) { Animatable(0f) }
    val draw = remember(roundKey, n) { List(n * n) { Animatable(0f) } }
    val pop = remember(roundKey, n) { List(n * n) { Animatable(1f) } }
    val winProgress = remember(roundKey) { Animatable(0f) }
    val burst = remember(roundKey) { Animatable(0f) }
    val press = remember(roundKey) { Animatable(0f) }
    val pressedCell = remember(roundKey) { mutableIntStateOf(-1) }
    val particleSeed = remember(roundKey) { Random.nextInt() }

    LaunchedEffect(roundKey, n) {
        grid.animateTo(1f, tween(look.duration(750), easing = FastOutSlowInEasing))
    }

    // Launch each mark's own animation from a scope that survives later board changes, so a quick
    // reply move doesn't cancel the previous mark mid-stroke.
    LaunchedEffect(board, roundKey) {
        for (i in 0 until board.cellCount) {
            if (board[i] == null) {
                if (draw[i].value != 0f) draw[i].snapTo(0f)
                continue
            }
            if (draw[i].targetValue == 1f) continue
            scope.launch { draw[i].animateTo(1f, tween(look.duration(if (board[i] == Mark.X) 420 else 380), easing = FastOutSlowInEasing)) }
            scope.launch {
                pop[i].snapTo(0.45f)
                pop[i].animateTo(1f, spring(dampingRatio = 0.32f, stiffness = 420f))
            }
        }
    }

    LaunchedEffect(win, roundKey) {
        if (win == null) return@LaunchedEffect
        delay(look.duration(320).toLong())
        launch { burst.animateTo(1f, tween(1700, easing = LinearEasing)) }
        winProgress.animateTo(1f, tween(look.duration(620), easing = FastOutSlowInEasing))
    }

    val infinite = rememberInfiniteTransition(label = "board")
    val pulse by infinite.animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse")
    val flow by infinite.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "flow")

    val currentOnCell by rememberUpdatedState(onCell)
    val currentEnabled by rememberUpdatedState(enabled)
    val currentBoard by rememberUpdatedState(board)

    Canvas(
        modifier.pointerInput(n, roundKey) {
            detectTapGestures { pos ->
                val side = min(size.width, size.height).toFloat()
                val origin = Offset((size.width - side) / 2f, (size.height - side) / 2f)
                val cellSize = side / n
                val c = ((pos.x - origin.x) / cellSize).toInt()
                val r = ((pos.y - origin.y) / cellSize).toInt()
                if (c !in 0 until n || r !in 0 until n) return@detectTapGestures
                val idx = r * n + c
                pressedCell.intValue = idx
                scope.launch {
                    press.snapTo(0f)
                    press.animateTo(1f, tween(420))
                }
                if (currentEnabled && currentBoard[idx] == null) currentOnCell(idx)
            }
        }
    ) {
        val side = min(size.width, size.height)
        val origin = Offset((size.width - side) / 2f, (size.height - side) / 2f)
        val cell = side / n
        fun centerOf(i: Int) = Offset(origin.x + (i % n + 0.5f) * cell, origin.y + (i / n + 0.5f) * cell)

        // Frosted glass panel.
        val corner = CornerRadius(cell * 0.16f)
        val g = grid.value
        drawRoundRect(palette.surface, origin, Size(side, side), corner)
        drawRoundRect(
            Brush.verticalGradient(listOf(Color.White.copy(alpha = if (palette.isDark) 0.08f else 0.3f), Color.Transparent), origin.y, origin.y + side * 0.5f),
            origin, Size(side, side), corner,
        )
        drawRoundRect(palette.outline, origin, Size(side, side), corner, style = Stroke(1.2.dp.toPx()))

        // Heatmap of historical play, felt rather than read: a thermal field of overlapping glows.
        if (heatmap != null) {
            val clip = Path().apply { addRoundRect(RoundRect(origin.x, origin.y, origin.x + side, origin.y + side, corner)) }
            clipPath(clip) {
                drawRect(palette.heatAt(0f).copy(alpha = 0.18f * g), origin, Size(side, side))
                // Coolest first so the hottest glows sit on top.
                for (i in (0 until n * n).sortedBy { heatmap.getOrElse(it) { 0f } }) {
                    val v = heatmap.getOrElse(i) { 0f } * g
                    if (v <= 0.02f) continue
                    val c = centerOf(i)
                    val breathe = 1f + 0.10f * v * pulse
                    val r = cell * (0.55f + 0.55f * v) * breathe
                    drawCircle(
                        Brush.radialGradient(
                            0f to palette.heatAt(v).copy(alpha = 0.35f + 0.5f * v),
                            0.45f to palette.heatAt(v * 0.75f).copy(alpha = 0.22f + 0.3f * v),
                            1f to Color.Transparent,
                            center = c, radius = r,
                        ),
                        r, c,
                    )
                }
            }
        }

        // Grid lines draw themselves in, staggered.
        val gridWidth = cell * 0.035f
        val lines = n - 1
        for (k in 1..lines) {
            val pV = ((g * 1.5f) - (k - 1) * 0.18f).coerceIn(0f, 1f)
            val pH = ((g * 1.5f) - (k - 1) * 0.18f - 0.12f).coerceIn(0f, 1f)
            val x = origin.x + k * cell
            val y = origin.y + k * cell
            val pad = cell * 0.18f
            drawGridLine(Offset(x, origin.y + pad), Offset(x, origin.y + pad + (side - pad * 2) * pV), palette.grid, gridWidth, palette.isDark)
            drawGridLine(Offset(origin.x + pad, y), Offset(origin.x + pad + (side - pad * 2) * pH, y), palette.grid, gridWidth, palette.isDark)
        }

        // Live threat heat: empty cells that would finish a line glow in that player's colour.
        if (threats != null && win == null) {
            for ((mark, cells) in threats) {
                val color = look.colorOf(mark)
                for (i in cells) {
                    val c = centerOf(i)
                    val r = cell * (0.42f + 0.08f * pulse)
                    drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.34f + 0.16f * pulse), Color.Transparent), c, r), r, c)
                    drawCircle(color.copy(alpha = 0.35f * (1f - pulse * 0.6f)), cell * (0.2f + 0.12f * pulse), c, style = Stroke(cell * 0.015f))
                }
            }
        }

        // Tap ripple.
        val pc = pressedCell.intValue
        if (pc >= 0 && press.value in 0.001f..0.999f) {
            val c = centerOf(pc)
            val t = press.value
            drawCircle(palette.text.copy(alpha = 0.18f * (1 - t)), cell * (0.15f + 0.4f * t), c, style = Stroke(cell * 0.03f * (1 - t) + 1f))
        }

        // Win zonal heat sits beneath the marks.
        val wp = winProgress.value
        if (win != null && wp > 0f) drawWinHeat(win, ::centerOf, cell, wp, pulse, flow, { palette.heatAt(it) })

        // Marks.
        for (i in 0 until n * n) {
            val mark = board[i] ?: continue
            val dim = if (win != null && i !in win.cells) 1f - 0.6f * wp else 1f
            drawMark(
                mark = mark, style = look.style, seed = i + roundKey * 31,
                center = centerOf(i), cell = cell, color = look.colorOf(mark),
                strokeFraction = look.strokeFraction, progress = draw[i].value, pop = pop[i].value,
                alpha = dim, darkBackground = palette.isDark,
            )
        }

        // The win stroke itself, over the marks.
        if (win != null && wp > 0f) drawWinStroke(win, ::centerOf, cell, wp, pulse) { palette.heatAt(it) }

        // Celebration particles.
        val b = burst.value
        if (win != null && b in 0.001f..0.999f) {
            val rnd = Random(particleSeed)
            repeat(56) {
                val from = centerOf(win.cells[rnd.nextInt(win.cells.size)])
                val angle = rnd.nextFloat() * 6.283f
                val speed = cell * (0.8f + rnd.nextFloat() * 1.8f)
                val gravity = cell * 1.6f
                val pos = Offset(from.x + cos(angle) * speed * b, from.y + sin(angle) * speed * b + gravity * b * b)
                val color = if (rnd.nextBoolean()) look.colorOf(win.mark) else palette.heatAt(0.5f + rnd.nextFloat() * 0.5f)
                val sz = cell * (0.025f + rnd.nextFloat() * 0.03f) * (1f - b * 0.5f)
                val a = (1f - b).coerceIn(0f, 1f)
                if (rnd.nextBoolean()) {
                    drawCircle(color.copy(alpha = a), sz, pos)
                } else {
                    val spin = b * 8f + angle
                    val dx = cos(spin) * sz * 1.4f
                    val dy = sin(spin) * sz * 1.4f
                    drawLine(color.copy(alpha = a), pos - Offset(dx, dy), pos + Offset(dx, dy), sz * 0.7f, StrokeCap.Round)
                    drawLine(color.copy(alpha = a), pos - Offset(-dy, dx), pos + Offset(-dy, dx), sz * 0.7f, StrokeCap.Round)
                }
            }
        }
    }
}

private fun DrawScope.drawGridLine(a: Offset, b: Offset, color: Color, width: Float, glow: Boolean) {
    if ((b - a).getDistance() < 1f) return
    if (glow) drawLine(color.copy(alpha = 0.25f), a, b, width * 3f, StrokeCap.Round)
    drawLine(color, a, b, width, StrokeCap.Round)
}

private fun winEnds(win: WinLine, centerOf: (Int) -> Offset, cell: Float): Pair<Offset, Offset> {
    val a = centerOf(win.cells.first())
    val b = centerOf(win.cells.last())
    val dir = (b - a) / (b - a).getDistance()
    return (a - dir * cell * 0.38f) to (b + dir * cell * 0.38f)
}

/** Radial "heat zones" around each winning cell plus hot spots that flow along the line. */
private fun DrawScope.drawWinHeat(
    win: WinLine, centerOf: (Int) -> Offset, cell: Float, progress: Float, pulse: Float, flow: Float, heat: (Float) -> Color,
) {
    win.cells.forEachIndexed { k, i ->
        // Zones ignite one after another as the stroke passes over them.
        val local = ((progress * (win.cells.size + 1)) - k).coerceIn(0f, 1f)
        if (local <= 0f) return@forEachIndexed
        val c = centerOf(i)
        val r = cell * (0.62f + 0.07f * pulse) * local
        drawCircle(
            Brush.radialGradient(
                0f to heat(1f).copy(alpha = 0.55f * local),
                0.35f to heat(0.75f).copy(alpha = 0.45f * local),
                0.7f to heat(0.35f).copy(alpha = 0.22f * local),
                1f to Color.Transparent,
                center = c, radius = r.coerceAtLeast(1f),
            ),
            r, c,
        )
    }
    val (a, b) = winEnds(win, centerOf, cell)
    repeat(3) { k ->
        val t = (flow + k / 3f) % 1f
        val p = Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
        val r = cell * 0.4f * progress
        drawCircle(Brush.radialGradient(listOf(heat(0.95f).copy(alpha = 0.4f), Color.Transparent), p, r.coerceAtLeast(1f)), r, p)
    }
}

private fun DrawScope.drawWinStroke(
    win: WinLine, centerOf: (Int) -> Offset, cell: Float, progress: Float, pulse: Float, heat: (Float) -> Color,
) {
    val (a, b) = winEnds(win, centerOf, cell)
    val end = Offset(a.x + (b.x - a.x) * progress, a.y + (b.y - a.y) * progress)
    val brush = Brush.linearGradient(listOf(heat(0.55f), heat(0.8f), heat(1f), heat(0.8f), heat(0.55f)), a, b)
    val w = cell * (0.12f + 0.02f * pulse)
    drawLine(heat(0.3f).copy(alpha = 0.22f), a, end, w * 4.2f, StrokeCap.Round)
    drawLine(heat(0.6f).copy(alpha = 0.35f), a, end, w * 2.4f, StrokeCap.Round)
    drawLine(brush, a, end, w, StrokeCap.Round)
    drawLine(Color.White.copy(alpha = 0.85f), a, end, w * 0.3f, StrokeCap.Round)
}
