package io.gh.kdbrian.ttac.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.gh.kdbrian.ttac.app.AppViewModel
import io.gh.kdbrian.ttac.app.Match
import io.gh.kdbrian.ttac.app.SeatKind
import io.gh.kdbrian.ttac.data.GameMode
import io.gh.kdbrian.ttac.game.Mark
import io.gh.kdbrian.ttac.ui.components.Avatar
import io.gh.kdbrian.ttac.ui.components.BouncyButton
import io.gh.kdbrian.ttac.ui.components.drawGlass
import io.gh.kdbrian.ttac.ui.components.IconBubble
import io.gh.kdbrian.ttac.ui.components.RollingNumber
import io.gh.kdbrian.ttac.ui.components.TopBar
import io.gh.kdbrian.ttac.ui.components.Txt
import io.gh.kdbrian.ttac.ui.components.popIn
import io.gh.kdbrian.ttac.ui.draw.BoardCanvas
import io.gh.kdbrian.ttac.ui.draw.Glyph
import io.gh.kdbrian.ttac.ui.draw.MarkLook
import io.gh.kdbrian.ttac.ui.draw.drawMark
import io.gh.kdbrian.ttac.ui.theme.LocalPalette
import io.gh.kdbrian.ttac.ui.theme.Type

@Composable
fun GameScreen(vm: AppViewModel) {
    // The match is cleared as this screen animates out; keep showing the last one meanwhile.
    val live = vm.match
    val lastMatch = remember { Ref<Match?>(null) }
    if (live != null) lastMatch.value = live
    val match = lastMatch.value ?: return
    val palette = LocalPalette.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    val cfg = match.config

    val look = MarkLook(
        xColor = Color(cfg.x.player.color),
        oColor = Color(cfg.o.player.color),
        style = settings.markStyle,
        strokeFraction = settings.strokeWidth,
        animSpeed = settings.animSpeed,
    )

    val heat = if (settings.heatmapOverlay) stats.heatFor(cfg.size) else null
    val heatValues = heat?.normalizedWins(cfg.size * cfg.size)
    val threats = if (settings.threatHeat && !match.isOver) {
        mapOf(Mark.X to match.board.threats(Mark.X), Mark.O to match.board.threats(Mark.O))
    } else null

    // A draw gives the board a disappointed wobble.
    val shake = remember(match.round) { Animatable(0f) }
    LaunchedEffect(match.isDraw, match.round) {
        if (match.isDraw) shake.animateTo(0f, keyframes {
            durationMillis = 520
            -14f at 60; 12f at 140; -9f at 220; 6f at 300; -3f at 380; 0f at 520
        })
    }

    ScreenColumn(scroll = true) {
        TopBar(
            title = "${cfg.mode.label} · ${cfg.size}×${cfg.size}",
            onBack = { vm.back() },
        ) {
            IconBubble(Glyph.FLAME, { vm.updateSettings { it.copy(heatmapOverlay = !it.heatmapOverlay) }; vm.tapSound() }, active = settings.heatmapOverlay)
        }
        Gap(6)

        // Session score counter.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PlayerCard(match, Mark.X, look, Modifier.weight(1f))
            Column(Modifier.padding(horizontal = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                RollingNumber(match.draws, Type.heading, palette.textDim)
                Txt("DRAWS", Type.label, palette.textDim)
                Gap(4)
                Txt("R${match.round}", Type.label, palette.accent)
            }
            PlayerCard(match, Mark.O, look, Modifier.weight(1f))
        }
        Gap(14)

        StatusLine(match)
        Gap(10)

        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .graphicsLayer {
                    translationX = shake.value.dp.toPx()
                    rotationZ = shake.value * 0.15f
                }
        ) {
            BoardCanvas(
                board = match.board,
                roundKey = match.round,
                win = match.win,
                look = look,
                threats = threats,
                heatmap = heatValues,
                enabled = match.canTap,
                onCell = match::tap,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f).padding(4.dp),
            )
        }
        Gap(18)

        AnimatedVisibility(
            visible = match.isOver || match.remoteGone != null,
            enter = scaleIn(spring(dampingRatio = 0.45f, stiffness = 380f), initialScale = 0.6f) + fadeIn(),
            exit = fadeOut(tween(120)),
        ) {
            Column {
                val gone = match.remoteGone
                if (gone != null) {
                    Txt(gone, Type.body, palette.bad, Modifier.fillMaxWidth(), TextAlign.Center)
                    Gap(10)
                    BouncyButton("Back to menu", { vm.back() }, Modifier.fillMaxWidth(), palette.surfaceHi, Glyph.BACK)
                } else {
                    val label = when {
                        cfg.mode != GameMode.LAN -> "Play again"
                        match.localRematch -> "Waiting for opponent…"
                        match.remoteRematch -> "Accept rematch!"
                        else -> "Rematch"
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        BouncyButton(label, { match.requestNextRound() }, Modifier.weight(1f), palette.accent, Glyph.REFRESH, enabled = !match.localRematch)
                        BouncyButton("", { vm.back() }, Modifier, palette.surfaceHi, Glyph.CLOSE)
                    }
                }
            }
        }
        Gap(24)
    }
}

private class Ref<T>(var value: T)

@Composable
private fun PlayerCard(match: Match, mark: Mark, look: MarkLook, modifier: Modifier) {
    val palette = LocalPalette.current
    val seat = match.config.seat(mark)
    val active = !match.isOver && match.turn == mark
    val won = match.win?.mark == mark
    val color = look.colorOf(mark)
    val lift by animateFloatAsState(if (active || won) 1f else 0f, spring(dampingRatio = 0.4f, stiffness = 360f), label = "lift")
    val wins = if (mark == Mark.X) match.xWins else match.oWins

    Column(
        modifier
            .graphicsLayer {
                val s = 0.94f + 0.08f * lift
                scaleX = s; scaleY = s
                translationY = -6.dp.toPx() * lift
            }
            .drawBehind {
                // Neutral glass: the screen behind carries the turn colour, not the card.
                drawGlass(palette, 22.dp.toPx(), border = androidx.compose.ui.graphics.lerp(palette.outline, Color.White.copy(alpha = 0.7f), lift))
            }
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(seat.player.name, color, size = 38.dp)
            RollingNumber(wins, Type.score, palette.text, Modifier.padding(start = 10.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            MarkBadge(mark, look, active, Modifier.size(16.dp))
            Txt(seat.player.name, Type.body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), palette.text, Modifier.padding(start = 6.dp), maxLines = 1)
        }
        val tag = when {
            won -> "WINNER"
            active && seat.kind == SeatKind.AI -> "THINKING"
            active -> "TURN"
            seat.kind == SeatKind.AI -> "CPU"
            seat.kind == SeatKind.REMOTE -> "REMOTE"
            else -> " "
        }
        Txt(tag, Type.label, if (active || won) color else palette.textDim)
    }
}

/** A mark that keeps re-drawing itself while it's that player's turn. */
@Composable
private fun MarkBadge(mark: Mark, look: MarkLook, active: Boolean, modifier: Modifier) {
    val palette = LocalPalette.current
    val t by rememberInfiniteTransition(label = "badge").animateFloat(0f, 1f, infiniteRepeatable(tween(1500, easing = LinearEasing)), label = "t")
    Canvas(modifier) {
        val progress = if (active) (t * 1.6f).coerceAtMost(1f) else 1f
        drawMark(mark, look.style, 0, center, size.minDimension * 1.25f, look.colorOf(mark), 0.14f, progress, darkBackground = palette.isDark)
    }
}

@Composable
private fun StatusLine(match: Match) {
    val palette = LocalPalette.current
    val cfg = match.config
    val text = when {
        match.remoteGone != null -> "Connection closed"
        match.win != null -> {
            val w = match.win!!.mark
            when {
                cfg.mode == GameMode.SOLO && w == cfg.localMark -> "You win!"
                cfg.mode == GameMode.SOLO -> "The CPU takes it"
                cfg.mode == GameMode.LAN && w == cfg.localMark -> "You win!"
                else -> "${cfg.seat(w).player.name} wins!"
            }
        }
        match.isDraw -> "Draw — no line left to burn"
        match.aiThinking -> "CPU is thinking…"
        cfg.mode == GameMode.LOCAL -> "${cfg.seat(match.turn).player.name}'s turn"
        match.turn == cfg.localMark -> "Your move"
        else -> "Waiting for ${cfg.seat(match.turn).player.name}…"
    }
    AnimatedContent(
        targetState = text,
        transitionSpec = {
            (slideInVertically(spring(dampingRatio = 0.5f, stiffness = 400f)) { it } + fadeIn())
                .togetherWith(slideOutVertically(tween(150)) { -it } + fadeOut(tween(120)))
        },
        modifier = Modifier.fillMaxWidth().height(36.dp),
        label = "status",
    ) { s ->
        Txt(s, Type.heading.copy(fontSize = Type.heading.fontSize * 1.1f), if (match.win != null) palette.accent else palette.text, Modifier.fillMaxWidth(), TextAlign.Center)
    }
}
