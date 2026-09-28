package io.gh.kdbrian.ttac.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.gh.kdbrian.ttac.data.MarkStyle
import io.gh.kdbrian.ttac.data.Profile
import io.gh.kdbrian.ttac.data.Record
import io.gh.kdbrian.ttac.game.Board
import io.gh.kdbrian.ttac.game.Mark
import io.gh.kdbrian.ttac.ui.components.Avatar
import io.gh.kdbrian.ttac.ui.components.Txt
import io.gh.kdbrian.ttac.ui.components.drawGlass
import androidx.compose.ui.graphics.graphicsLayer
import io.gh.kdbrian.ttac.ui.components.bouncyClick
import io.gh.kdbrian.ttac.ui.components.popIn
import io.gh.kdbrian.ttac.ui.draw.drawMark
import io.gh.kdbrian.ttac.ui.theme.LocalPalette
import io.gh.kdbrian.ttac.ui.theme.Type

/** Edge-to-edge scrolling column: content is inset from system bars and the keyboard. */
@Composable
fun ScreenColumn(scroll: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = 560.dp)
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding()
                .let { if (scroll) it.verticalScroll(rememberScrollState()) else it }
                .padding(horizontal = 20.dp, vertical = 8.dp),
            content = content,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileChips(
    profiles: List<Profile>,
    selectedId: String?,
    onSelect: (Profile) -> Unit,
    disabledId: String? = null,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        profiles.forEachIndexed { i, p ->
            ProfileChip(p, p.id == selectedId, p.id != disabledId, Modifier.popIn(i * 40)) { onSelect(p) }
        }
    }
}

@Composable
fun ProfileChip(profile: Profile, selected: Boolean, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val palette = LocalPalette.current
    val sel by animateFloatAsState(if (selected) 1f else 0f, spring(dampingRatio = 0.45f), label = "chip")
    val color = Color(profile.color)
    Row(
        modifier
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
            .bouncyClick(enabled) { onClick() }
            .drawBehind {
                drawGlass(
                    palette, size.height / 2,
                    fill = androidx.compose.ui.graphics.lerp(palette.surface, color.copy(alpha = 0.45f), sel),
                    border = androidx.compose.ui.graphics.lerp(palette.outline, color, sel),
                )
            }
            .padding(start = 5.dp, end = 16.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(profile.name, color, size = 32.dp)
        Spacer(Modifier.width(10.dp))
        Txt(profile.name, Type.body.copy(fontWeight = FontWeight.Bold), palette.text, maxLines = 1)
    }
}

/** Win/loss/draw ring that sweeps in when shown. */
@Composable
fun RecordDonut(record: Record, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    val sweep = remember(record) { Animatable(0f) }
    LaunchedEffect(record) { sweep.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.14f
            val inset = stroke / 2
            val arcSize = Size(size.minDimension - stroke, size.minDimension - stroke)
            val tl = Offset(inset, inset)
            drawArc(palette.surfaceHi, 0f, 360f, false, tl, arcSize, style = Stroke(stroke))
            val total = record.played
            if (total > 0) {
                var start = -90f
                val parts = listOf(record.wins to palette.good, record.draws to palette.accent, record.losses to palette.bad)
                for ((count, color) in parts) {
                    val s = 360f * count / total * sweep.value
                    if (s > 0.5f) drawArc(color, start, (s - 3f).coerceAtLeast(0.5f), false, tl, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                    start += 360f * count / total * sweep.value
                }
            }
        }
        androidx.compose.foundation.layout.BoxWithConstraints(contentAlignment = Alignment.Center) {
            val small = maxWidth < 70.dp
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Txt("${(record.winRate * 100).toInt()}%", if (small) Type.body.copy(fontWeight = FontWeight.ExtraBold, fontSize = 13.sp) else Type.heading)
                if (!small) Txt("WIN", Type.label, palette.textDim)
            }
        }
    }
}

@Composable
fun StatTile(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    Column(
        modifier
            .drawBehind {
                drawGlass(palette, 16.dp.toPx(), fill = palette.surfaceHi)
                drawCircle(color, 3.dp.toPx(), Offset(size.width / 2, size.height - 7.dp.toPx()))
            }
            .padding(start = 8.dp, end = 8.dp, top = 10.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Txt(value, Type.heading, color)
        Txt(label, Type.label, palette.textDim)
    }
}

@Composable
fun RecordRow(record: Record) {
    val palette = LocalPalette.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile("WINS", record.wins.toString(), palette.good, Modifier.weight(1f))
        StatTile("DRAWS", record.draws.toString(), palette.accent, Modifier.weight(1f))
        StatTile("LOSSES", record.losses.toString(), palette.bad, Modifier.weight(1f))
        StatTile("BEST", record.bestStreak.toString(), palette.accent2, Modifier.weight(1f))
    }
}

/** Tiny static render of a finished board, with its win cells highlighted. */
@Composable
fun MiniBoard(encoded: String, winCells: List<Int>, xColor: Color, oColor: Color, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    val board = remember(encoded) { runCatching { Board.decode(encoded) }.getOrNull() }
    Canvas(modifier) {
        val b = board ?: return@Canvas
        val n = b.size
        val cell = size.minDimension / n
        drawRoundRect(palette.surfaceHi, cornerRadius = CornerRadius(cell * 0.25f))
        for (i in winCells) {
            val c = Offset((i % n + 0.5f) * cell, (i / n + 0.5f) * cell)
            drawCircle(palette.accent.copy(alpha = 0.35f), cell * 0.5f, c)
        }
        for (k in 1 until n) {
            drawLine(palette.grid.copy(alpha = 0.5f), Offset(k * cell, cell * 0.15f), Offset(k * cell, size.height - cell * 0.15f), 1.5f)
            drawLine(palette.grid.copy(alpha = 0.5f), Offset(cell * 0.15f, k * cell), Offset(size.width - cell * 0.15f, k * cell), 1.5f)
        }
        for (i in 0 until b.cellCount) {
            val m = b[i] ?: continue
            drawMark(
                m, MarkStyle.SOLID, i, Offset((i % n + 0.5f) * cell, (i / n + 0.5f) * cell), cell,
                if (m == Mark.X) xColor else oColor, 0.13f, 1f, darkBackground = palette.isDark,
            )
        }
    }
}

@Composable
fun Gap(h: Int = 12) = Spacer(Modifier.height(h.dp))
