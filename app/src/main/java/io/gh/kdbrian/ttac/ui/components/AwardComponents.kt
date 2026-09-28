package io.gh.kdbrian.ttac.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.gh.kdbrian.ttac.app.Celebration
import io.gh.kdbrian.ttac.data.ArcadeGame
import io.gh.kdbrian.ttac.data.Medal
import io.gh.kdbrian.ttac.ui.draw.ArcadeArt
import io.gh.kdbrian.ttac.data.MedalAward
import io.gh.kdbrian.ttac.data.MedalKind
import io.gh.kdbrian.ttac.ui.draw.Glyph
import io.gh.kdbrian.ttac.ui.draw.MedalArt
import io.gh.kdbrian.ttac.ui.draw.tones
import io.gh.kdbrian.ttac.ui.theme.LocalPalette
import io.gh.kdbrian.ttac.ui.theme.Type
import io.gh.kdbrian.ttac.ui.theme.heatAt
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Cold ramp for losing streaks — the mirror image of the heat ramp. */
private val ColdRamp = listOf(Color(0xFF1B2A6B), Color(0xFF2F5BD9), Color(0xFF59B8FF), Color(0xFFBFF1FF))

private fun coldAt(t: Float): Color {
    val x = t.coerceIn(0f, 1f) * (ColdRamp.size - 1)
    val i = x.toInt().coerceAtMost(ColdRamp.size - 2)
    return androidx.compose.ui.graphics.lerp(ColdRamp[i], ColdRamp[i + 1], x - i)
}

/**
 * A streak gauge: one segment per game up to the next medal. Filled segments glow along the
 * heat ramp (or the cold ramp for a losing streak) and flicker harder the closer the medal is.
 */
@Composable
fun StreakMeter(
    title: String,
    current: Int,
    best: Int,
    target: Int,
    nextMedal: Medal,
    cold: Boolean,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    val fill = remember(current, target) { Animatable(0f) }
    LaunchedEffect(current, target) { fill.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 120f)) }
    val flicker by rememberInfiniteTransition(label = "flicker").animateFloat(0f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "f")
    val ramp: (Float) -> Color = if (cold) ::coldAt else { t -> palette.heatAt(t) }
    val base = ((target - 1) / io.gh.kdbrian.ttac.data.Medals.GAP) * io.gh.kdbrian.ttac.data.Medals.GAP
    val inWindow = (current - base).coerceIn(0, target - base)
    val windowSize = (target - base).coerceAtLeast(1)

    Panel(modifier, contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Txt(title.uppercase(), Type.label, palette.textDim, Modifier.weight(1f))
            GlyphBadge(if (cold) Glyph.SNOW else Glyph.FLAME, ramp(0.8f))
        }
        Row(verticalAlignment = Alignment.Bottom) {
            RollingNumber(current, Type.score, if (current > 0) ramp(0.75f) else palette.text)
            Txt(if (cold) " lost" else " won", Type.body, palette.textDim, Modifier.padding(bottom = 8.dp))
        }
        Spacer(Modifier.height(6.dp))
        Canvas(Modifier.fillMaxWidth().height(14.dp)) {
            val gap = 4.dp.toPx()
            val w = (size.width - gap * (windowSize - 1)) / windowSize
            for (i in 0 until windowSize) {
                val x = i * (w + gap)
                val r = CornerRadius(size.height / 2)
                drawRoundRect(palette.surfaceHi, Offset(x, 0f), Size(w, size.height), r)
                val lit = (inWindow * fill.value - i).coerceIn(0f, 1f)
                if (lit > 0f) {
                    val t = (i + 1f) / windowSize
                    val glow = 0.6f + 0.4f * flicker * t
                    drawRoundRect(ramp(t).copy(alpha = 0.35f * glow), Offset(x - 2f, -2f), Size(w * lit + 4f, size.height + 4f), r)
                    drawRoundRect(Brush.horizontalGradient(listOf(ramp(t * 0.7f), ramp(t)), x, x + w), Offset(x, 0f), Size(w * lit, size.height), r)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        val left = target - current
        Txt(
            if (left > 0) "$left more for ${nextMedal.title}" else "${nextMedal.title} earned!",
            Type.body.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold), palette.text,
        )
        Txt("${if (cold) "Worst" else "Best"} ever: $best", Type.label.copy(letterSpacing = 0.sp), palette.textDim)
    }
}

@Composable
private fun GlyphBadge(glyph: Glyph, color: Color) {
    io.gh.kdbrian.ttac.ui.draw.GlyphIcon(glyph, color, size = 18.dp)
}

/** Cabinet tile: medal art, name, and either its count or what it takes to earn it. */
@Composable
fun MedalTile(medal: Medal, count: Int, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    val earned = count > 0
    Column(modifier.padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            MedalArt(medal, earned, Modifier.size(78.dp))
            if (count > 1) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .drawBehind { drawRoundRect(medal.tones().second, cornerRadius = CornerRadius(size.height / 2)) }
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) { Txt("×$count", Type.label.copy(letterSpacing = 0.sp), Color.White) }
            }
        }
        Txt(medal.title, Type.body.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp), if (earned) palette.text else palette.textDim, align = TextAlign.Center, maxLines = 1)
        Txt(medal.description, Type.label.copy(letterSpacing = 0.sp, fontSize = 10.sp), palette.textDim, align = TextAlign.Center, maxLines = 2)
    }
}

/**
 * Full-screen celebration for a freshly earned medal: dim scrim, rotating sunburst in the medal's
 * colours, the medal bouncing in, and a tap anywhere to continue.
 */
@Composable
fun CelebrationOverlay(item: Celebration?, onDismiss: () -> Unit, onPlay: (ArcadeGame) -> Unit) {
    val palette = LocalPalette.current
    AnimatedVisibility(item != null, enter = fadeIn(tween(200)), exit = fadeOut(tween(200))) {
        val a = item ?: return@AnimatedVisibility
        val (light, dark) = when (a) {
            is Celebration.MedalWon -> a.award.medal.tones()
            is Celebration.Unlocked -> a.game.tones()
        }
        val pop = remember(a) { Animatable(0f) }
        LaunchedEffect(a) { pop.animateTo(1f, spring(dampingRatio = 0.38f, stiffness = 260f)) }
        val spin by rememberInfiniteTransition(label = "burst").animateFloat(0f, 360f, infiniteRepeatable(tween(14_000, easing = LinearEasing)), label = "spin")
        val burst = remember(a) { Animatable(0f) }
        LaunchedEffect(a) { burst.animateTo(1f, tween(1400, easing = FastOutSlowInEasing)) }

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(a) { detectTapGestures { onDismiss() } },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val c =Offset(size.width / 2, size.height * 0.42f)
                val reach = size.maxDimension * 0.7f
                rotate(spin, c) {
                    for (k in 0 until 16) {
                        val a0 = k * (2 * PI.toFloat() / 16)
                        val a1 = a0 + 0.12f
                        val ray = Path().apply {
                            moveTo(c.x, c.y)
                            lineTo(c.x + cos(a0) * reach, c.y + sin(a0) * reach)
                            lineTo(c.x + cos(a1) * reach, c.y + sin(a1) * reach)
                            close()
                        }
                        drawPath(ray, Brush.radialGradient(listOf(light.copy(alpha = 0.35f), Color.Transparent), c, reach))
                    }
                }
                drawCircle(Brush.radialGradient(listOf(light.copy(alpha = 0.55f), dark.copy(alpha = 0.15f), Color.Transparent), c, size.minDimension * 0.45f), size.minDimension * 0.45f, c)
                // Confetti ring bursting outward.
                val b = burst.value
                if (b < 1f) {
                    for (k in 0 until 36) {
                        val ang = k * (2 * PI.toFloat() / 36) + k * 0.3f
                        val dist = size.minDimension * (0.15f + 0.45f * b) * (0.7f + (k % 5) * 0.08f)
                        val p = c + Offset(cos(ang) * dist, sin(ang) * dist + b * b * 120f)
                        val col = if (k % 3 == 0) light else if (k % 3 == 1) palette.heatAt(0.9f) else Color.White
                        drawCircle(col.copy(alpha = 1f - b), 5f + (k % 4) * 2f, p)
                    }
                }
            }
            Column(
                Modifier.padding(horizontal = 32.dp).padding(top = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val artModifier = Modifier
                    .size(190.dp)
                    .graphicsLayer {
                        val p = pop.value
                        scaleX = p; scaleY = p
                        rotationZ = (1f - p) * -40f
                    }
                when (a) {
                    is Celebration.MedalWon -> MedalArt(a.award.medal, earned = true, artModifier)
                    is Celebration.Unlocked -> ArcadeArt(a.game, locked = false, modifier = artModifier)
                }
                Spacer(Modifier.height(18.dp))
                when (a) {
                    is Celebration.MedalWon -> {
                        val award = a.award
                        Txt(
                            when (award.medal.kind) {
                                MedalKind.WIN -> "MEDAL EARNED"
                                MedalKind.LOSS -> "GRIT BADGE"
                                MedalKind.COMEBACK -> "COMEBACK!"
                            },
                            Type.label, light,
                        )
                        Txt(award.medal.title, Type.display.copy(fontSize = 38.sp), palette.text, align = TextAlign.Center)
                        Spacer(Modifier.height(4.dp))
                        Txt(
                            when (award.medal.kind) {
                                MedalKind.WIN -> "${award.ownerName} · ${award.streak} wins in a row"
                                MedalKind.LOSS -> "${award.ownerName} · ${award.streak} losses in a row. Keep going."
                                MedalKind.COMEBACK -> "${award.ownerName} snapped a ${award.streak}-game losing streak"
                            },
                            Type.body, palette.textDim, align = TextAlign.Center,
                        )
                    }
                    is Celebration.Unlocked -> {
                        Txt("NEW GAME UNLOCKED", Type.label, light)
                        Txt(a.game.title, Type.display.copy(fontSize = 40.sp), palette.text, align = TextAlign.Center)
                        Spacer(Modifier.height(4.dp))
                        Txt(
                            when (a.game) {
                                ArcadeGame.BLOCKS -> "Your ${io.gh.kdbrian.ttac.data.Unlocks.BLOCKS_STREAK}-win streak opened the arcade. Stack, clear, win."
                                ArcadeGame.HIVE -> "${io.gh.kdbrian.ttac.data.Unlocks.HIVE_STREAK} Blocks wins in a row! The hive is buzzing."
                            },
                            Type.body, palette.textDim, align = TextAlign.Center,
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
                when (a) {
                    is Celebration.MedalWon -> BouncyButton("Collect", onDismiss, Modifier.width(180.dp), light, Glyph.CHECK)
                    is Celebration.Unlocked -> Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                        BouncyButton("Later", onDismiss, color = LocalPalette.current.surfaceHi)
                        BouncyButton("Play now", { onPlay(a.game) }, Modifier.width(170.dp), light, Glyph.CHECK)
                    }
                }
            }
        }
    }
}
