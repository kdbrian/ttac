package io.gh.kdbrian.ttac.ui.screens

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.gh.kdbrian.ttac.app.AppViewModel
import io.gh.kdbrian.ttac.app.LudoController
import io.gh.kdbrian.ttac.app.Screen
import io.gh.kdbrian.ttac.data.MarkStyle
import io.gh.kdbrian.ttac.game.Board
import io.gh.kdbrian.ttac.game.Difficulty
import io.gh.kdbrian.ttac.game.Ludo
import io.gh.kdbrian.ttac.game.LudoAction
import io.gh.kdbrian.ttac.game.LudoBoard
import io.gh.kdbrian.ttac.game.LudoColor
import io.gh.kdbrian.ttac.game.LudoPhase
import io.gh.kdbrian.ttac.game.LudoSeat
import io.gh.kdbrian.ttac.game.LudoState
import io.gh.kdbrian.ttac.game.Mark
import io.gh.kdbrian.ttac.game.SeatKind
import io.gh.kdbrian.ttac.net.JoinLink
import io.gh.kdbrian.ttac.net.LudoLinkStatus
import io.gh.kdbrian.ttac.net.LudoWire
import io.gh.kdbrian.ttac.net.SessionCode
import io.gh.kdbrian.ttac.ui.components.Avatar
import io.gh.kdbrian.ttac.ui.components.BouncyButton
import io.gh.kdbrian.ttac.ui.components.IconBubble
import io.gh.kdbrian.ttac.ui.components.NameField
import io.gh.kdbrian.ttac.ui.components.Panel
import io.gh.kdbrian.ttac.ui.components.QrCode
import io.gh.kdbrian.ttac.ui.components.QrScanner
import io.gh.kdbrian.ttac.ui.components.SectionLabel
import io.gh.kdbrian.ttac.ui.components.Segmented
import io.gh.kdbrian.ttac.ui.components.TopBar
import io.gh.kdbrian.ttac.ui.components.Txt
import io.gh.kdbrian.ttac.ui.components.bouncyClick
import io.gh.kdbrian.ttac.ui.components.drawGlass
import io.gh.kdbrian.ttac.ui.components.onColor
import io.gh.kdbrian.ttac.ui.components.popIn
import io.gh.kdbrian.ttac.ui.draw.BoardCanvas
import io.gh.kdbrian.ttac.ui.draw.Glyph
import io.gh.kdbrian.ttac.ui.draw.LudoGeometry
import io.gh.kdbrian.ttac.ui.draw.MarkLook
import io.gh.kdbrian.ttac.ui.draw.PalaceBackdrop
import io.gh.kdbrian.ttac.ui.draw.dark
import io.gh.kdbrian.ttac.ui.draw.drawAvatarFrame
import io.gh.kdbrian.ttac.ui.draw.drawCoin
import io.gh.kdbrian.ttac.ui.draw.drawDie
import io.gh.kdbrian.ttac.ui.draw.drawGem
import io.gh.kdbrian.ttac.ui.draw.drawPawn
import io.gh.kdbrian.ttac.ui.draw.ludoBoardBackground
import io.gh.kdbrian.ttac.ui.draw.tone
import io.gh.kdbrian.ttac.ui.theme.LocalPalette
import io.gh.kdbrian.ttac.ui.theme.Type
import kotlinx.coroutines.launch

private val SeatKind.label: String
    get() = when (this) {
        SeatKind.EMPTY -> "Empty"; SeatKind.CPU -> "CPU"; SeatKind.LOCAL -> "Here"; SeatKind.REMOTE -> "LAN"
    }

// ==== Lobby ======================================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LudoLobbyScreen(vm: AppViewModel) {
    val palette = LocalPalette.current
    val ludo = vm.ludo
    val link by vm.ludoLink.status.collectAsStateWithLifecycle()
    val tables by vm.ludoLink.tables.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var code by rememberSaveable { mutableStateOf("") }
    var scanning by rememberSaveable { mutableStateOf(false) }
    androidx.activity.compose.BackHandler(scanning) { scanning = false }

    // The moment a game exists (started here, or received from a host), go to the table.
    LaunchedEffect(ludo.state != null) { if (ludo.state != null) vm.openLudoTable() }
    // Browse for tables while idle so joining is one tap.
    LaunchedEffect(link) { if (link is LudoLinkStatus.Idle) vm.ludoLink.discover() }

    Box(Modifier.fillMaxSize()) {
        ScreenColumn {
            TopBar("Ludo Palace", { vm.back() })
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(Modifier.size(180.dp).popIn().ludoBoardBackground())
            }
            Txt(
                "On your turn, challenge anyone at the table to Tic-Tac-Toe. Rounds repeat until someone wins — the winner rolls the die and moves.",
                Type.body, palette.textDim, Modifier.fillMaxWidth().padding(top = 8.dp), TextAlign.Center,
            )

            if (ludo.isGuest) {
                GuestLobby(ludo, link)
            } else {
                SectionLabel("Seats")
                ludo.seats.forEach { seat -> SeatEditor(vm, ludo, seat) }

                SectionLabel("CPU difficulty")
                Segmented(Difficulty.entries, ludo.difficulty, { it.label }, { ludo.difficulty = it; vm.tapSound() })

                val lanSeats = ludo.seats.count { it.kind == SeatKind.REMOTE }
                if (lanSeats > 0) {
                    SectionLabel("LAN · $lanSeats open seat${if (lanSeats > 1) "s" else ""}")
                    val hosting = link as? LudoLinkStatus.Hosting
                    Panel(Modifier.fillMaxWidth()) {
                        if (hosting == null) {
                            BouncyButton("Open this table on LAN", { vm.hostLudo() }, Modifier.fillMaxWidth(), LudoColor.BLUE.tone, Glyph.WIFI)
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                hosting.code?.let { QrCode(JoinLink.build(it, hosting.alias), LudoColor.YELLOW.tone, Modifier.size(110.dp)) }
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Txt(hosting.alias, Type.body.copy(fontWeight = FontWeight.ExtraBold, fontSize = 17.sp))
                                    hosting.code?.let { Txt(it, Type.heading.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 16.sp), LudoColor.YELLOW.tone) }
                                    Txt("${hosting.guests.size} joined · nearby phones see this table", Type.label.copy(letterSpacing = 0.sp), palette.textDim)
                                }
                            }
                        }
                    }
                }

                Gap(18)
                BouncyButton(
                    if (ludo.canStart) "Start game" else if (ludo.seats.any { it.kind == SeatKind.REMOTE && it.device == 0 }) "Waiting for LAN players…" else "Need at least 2 players",
                    { ludo.start() }, Modifier.fillMaxWidth(), Color(0xFFE0A21B), Glyph.PLAY, enabled = ludo.canStart,
                )

                SectionLabel("Or join a table")
                Panel(Modifier.fillMaxWidth()) {
                    if (tables.isEmpty()) Txt("Looking for tables nearby…", Type.body, palette.textDim)
                    tables.forEach { t ->
                        Row(
                            Modifier.fillMaxWidth().bouncyClick { vm.joinLudo(t.address, t.port, t.alias) }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Avatar(t.hostName.ifBlank { t.alias }, LudoColor.RED.tone, size = 38.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Txt(t.alias, Type.body.copy(fontWeight = FontWeight.Bold))
                                Txt("hosted by ${t.hostName}", Type.label.copy(letterSpacing = 0.sp), palette.textDim)
                            }
                            Txt("JOIN", Type.label, palette.accent)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NameField(code, { code = it.uppercase().filter { c -> c.isLetterOrDigit() || c == '-' }.take(11) }, "Session code", Modifier.weight(1f))
                        Spacer(Modifier.width(10.dp))
                        IconBubble(Glyph.QR, { scanning = true }, size = 50.dp, label = "Scan a table's QR code")
                    }
                    Spacer(Modifier.height(8.dp))
                    BouncyButton("Join with code", {
                        SessionCode.decode(code)?.let { (a, p) -> vm.joinLudo(a, p, "table $code") }
                    }, Modifier.fillMaxWidth(), palette.surfaceHi, Glyph.WIFI, enabled = code.count { it != '-' } == 10, compact = true)
                }
                if (link is LudoLinkStatus.Failed) Txt((link as LudoLinkStatus.Failed).reason, Type.body, palette.bad, Modifier.padding(top = 10.dp))
                Gap(24)
            }
        }
        if (scanning) {
            QrScanner(
                accept = { JoinLink.parse(it) != null },
                onResult = { text ->
                    scanning = false
                    val (c, alias) = JoinLink.parse(text) ?: return@QrScanner
                    SessionCode.decode(c)?.let { (a, p) -> vm.joinLudo(a, p, alias) }
                },
                onClose = { scanning = false },
            )
        }
    }
}

@Composable
private fun SeatEditor(vm: AppViewModel, ludo: LudoController, seat: LudoSeat) {
    val palette = LocalPalette.current
    Panel(Modifier.fillMaxWidth().padding(bottom = 8.dp), contentPadding = PaddingValues(12.dp), highlight = seat.color.tone.copy(alpha = 0.7f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(28.dp)) { drawPawn(center, size.minDimension * 1.3f, seat.color) }
            Spacer(Modifier.width(10.dp))
            if (seat.kind == SeatKind.LOCAL) {
                NameField(seat.name, { ludo.rename(seat.color, it) }, seat.color.label, Modifier.weight(1f))
            } else {
                Txt(seat.name, Type.body.copy(fontWeight = FontWeight.Bold), if (seat.kind == SeatKind.EMPTY) palette.textDim else palette.text, Modifier.weight(1f), maxLines = 1)
            }
        }
        Spacer(Modifier.height(8.dp))
        Segmented(SeatKind.entries, seat.kind, { it.label }, { ludo.setSeat(seat.color, it); vm.tapSound() }, accent = seat.color.tone)
    }
}

@Composable
private fun GuestLobby(ludo: LudoController, link: LudoLinkStatus) {
    val palette = LocalPalette.current
    Gap(16)
    Panel(Modifier.fillMaxWidth()) {
        when (link) {
            is LudoLinkStatus.Joining -> Txt("Joining ${link.alias}…", Type.heading)
            is LudoLinkStatus.Joined -> {
                Txt("You're at ${ludo.hostAlias.ifBlank { link.hostAlias }}'s table", Type.heading)
                Txt("Waiting for the host to start…", Type.body, palette.textDim)
            }
            is LudoLinkStatus.Failed -> Txt(link.reason, Type.body, palette.bad)
            else -> Txt("Connecting…", Type.body, palette.textDim)
        }
    }
    SectionLabel("Seats")
    ludo.seats.filter { it.kind != SeatKind.EMPTY }.forEach { seat ->
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(24.dp)) { drawPawn(center, size.minDimension * 1.3f, seat.color) }
            Spacer(Modifier.width(10.dp))
            Txt(seat.name, Type.body.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
            Txt(if (ludo.controls(seat.color)) "YOU" else seat.kind.label.uppercase(), Type.label, if (ludo.controls(seat.color)) seat.color.tone else palette.textDim)
        }
    }
}

// ==== The table ==================================================================================

@Composable
fun LudoTableScreen(vm: AppViewModel) {
    val ludo = vm.ludo
    val s = ludo.state ?: return
    val stats by vm.stats.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize()) {
        PalaceBackdrop(Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            // Top bar: menu, trophy, gems and coins.
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBubble(Glyph.BACK, { vm.back() }, size = 42.dp, label = "Leave table")
                Spacer(Modifier.width(8.dp))
                IconBubble(Glyph.TROPHY, { vm.go(Screen.SCOREBOARD) }, size = 42.dp, tint = Color(0xFFE8B84A), label = "Scores")
                Spacer(Modifier.weight(1f))
                CurrencyPill(stats.gems, gem = true)
                Spacer(Modifier.width(8.dp))
                CurrencyPill(stats.coins, gem = false)
            }
            Spacer(Modifier.weight(0.5f))
            // Top avatars: blue (left) and red (right).
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                SeatBadge(ludo, s, LudoColor.BLUE)
                SeatBadge(ludo, s, LudoColor.RED)
            }
            LudoBoardView(ludo, s, Modifier.fillMaxWidth().aspectRatio(1f).padding(horizontal = 4.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                SeatBadge(ludo, s, LudoColor.YELLOW)
                SeatBadge(ludo, s, LudoColor.GREEN)
            }
            Spacer(Modifier.weight(0.5f))
            ActionPanel(vm, ludo, s, Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp))
        }
    }
}

@Composable
private fun CurrencyPill(amount: Int, gem: Boolean) {
    Row(
        Modifier
            .drawBehind {
                drawRoundRect(Color(0xCC2A1407), cornerRadius = CornerRadius(size.height / 2))
                drawRoundRect(Color(0xFFE8B84A).copy(alpha = 0.7f), cornerRadius = CornerRadius(size.height / 2), style = Stroke(1.5.dp.toPx()))
            }
            .padding(start = 6.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(22.dp)) { if (gem) drawGem(center, size.minDimension * 0.45f) else drawCoin(center, size.minDimension * 0.45f) }
        Spacer(Modifier.width(6.dp))
        Txt("%,d".format(amount), Type.body.copy(fontWeight = FontWeight.ExtraBold), Color(0xFFFFF1D0))
        Spacer(Modifier.width(6.dp))
        Canvas(Modifier.size(14.dp)) {
            drawCircle(Color(0xFFE8B84A), size.minDimension / 2)
            drawLine(Color(0xFF2A1407), Offset(size.width * 0.25f, center.y), Offset(size.width * 0.75f, center.y), 2.dp.toPx())
            drawLine(Color(0xFF2A1407), Offset(center.x, size.height * 0.25f), Offset(center.x, size.height * 0.75f), 2.dp.toPx())
        }
    }
}

/** Ornate framed avatar with a mic-mute badge and a name ribbon in the seat's colour. */
@Composable
private fun SeatBadge(ludo: LudoController, s: LudoState, color: LudoColor) {
    val seat = s.seat(color)
    if (seat.kind == SeatKind.EMPTY) { Spacer(Modifier.size(96.dp, 118.dp)); return }
    val acting = s.actor == color
    val pulse by rememberInfiniteTransition(label = "turn").animateFloat(0.3f, 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "p")
    val home = s.pawns.getValue(color).count { it == LudoBoard.HOME }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(78.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) { drawAvatarFrame(center, size.minDimension * 0.44f, color.tone, if (acting) pulse else 0f) }
            Avatar(seat.name, seat.avatarColor.let(::Color), size = 58.dp, ring = Color.Transparent)
            // Mic-mute badge, bottom-left, like the original's voice-chat indicator.
            Canvas(Modifier.size(22.dp).align(Alignment.BottomStart)) {
                drawCircle(Color(0xFF2A1407), size.minDimension / 2)
                drawCircle(Color(0xFFE8B84A), size.minDimension / 2, style = Stroke(1.5.dp.toPx()))
                val w = size.width
                drawRoundRect(Color(0xFFFFF1D0), Offset(w * 0.4f, w * 0.22f), Size(w * 0.2f, w * 0.34f), CornerRadius(w * 0.1f))
                drawLine(Color(0xFFFFF1D0), Offset(w * 0.5f, w * 0.62f), Offset(w * 0.5f, w * 0.76f), 1.5.dp.toPx())
                drawLine(Color(0xFFE53935), Offset(w * 0.26f, w * 0.26f), Offset(w * 0.74f, w * 0.74f), 2.dp.toPx())
            }
        }
        // Name ribbon with notched ends.
        Box(
            Modifier
                .drawBehind {
                    val n = size.height * 0.35f
                    val p = Path().apply {
                        moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(size.width - n, size.height / 2); lineTo(size.width, size.height)
                        lineTo(0f, size.height); lineTo(n, size.height / 2); close()
                    }
                    drawPath(p, color.dark)
                    drawPath(p, Color(0xFFE8B84A), style = Stroke(1.2.dp.toPx()))
                }
                .padding(horizontal = 16.dp, vertical = 3.dp),
        ) {
            Txt(seat.name.take(12) + if (ludo.controls(color)) " ·You" else "", Type.label.copy(letterSpacing = 0.sp, fontSize = 11.sp), Color.White, maxLines = 1)
        }
        Txt("$home/4 home", Type.label.copy(letterSpacing = 0.sp, fontSize = 10.sp), Color(0xFFFFE3A0).copy(alpha = 0.85f))
    }
}

/** The board, pawns (animated square by square) and the die in the centre medallion. */
@Composable
private fun LudoBoardView(ludo: LudoController, s: LudoState, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    // One Animatable per pawn tracks its displayed progress.
    val shown = remember { LudoColor.entries.associateWith { c -> List(LudoBoard.PAWNS) { i -> Animatable(s.pawns.getValue(c)[i].toFloat()) } } }
    LaunchedEffect(s.version) {
        for (c in LudoColor.entries) for (i in 0 until LudoBoard.PAWNS) {
            val target = s.pawns.getValue(c)[i].toFloat()
            val a = shown.getValue(c)[i]
            if (a.targetValue == target) continue
            if (target < a.value) scope.launch { a.snapTo(target) }                               // captured: back to base
            else {
                val from = if (a.value < 0f && target >= 0f) -1f else a.value
                val steps = (target - from).coerceAtLeast(1f)
                scope.launch { a.animateTo(target, tween((steps * 140).toInt().coerceAtMost(1400), easing = LinearEasing)) }
            }
        }
    }
    val tumble = remember { Animatable(0f) }
    LaunchedEffect(s.die, s.version) { if (s.die != null) { tumble.snapTo(540f); tumble.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = 90f)) } }
    val glow by rememberInfiniteTransition(label = "movable").animateFloat(0.2f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "g")
    val myMove = s.phase == LudoPhase.MOVE && s.roller != null && ludo.controls(s.roller)

    Box(
        modifier
            .ludoBoardBackground()
            .pointerInput(s.version) {
                detectTapGestures { p ->
                    if (!myMove) return@detectTapGestures
                    val g = LudoGeometry(Offset(size.width / 2f, size.height / 2f), minOf(size.width, size.height).toFloat())
                    val color = s.roller ?: return@detectTapGestures
                    val pick = s.movable.minByOrNull { i -> (g.spot(color, s.pawns.getValue(color)[i], i) - p).getDistance() } ?: return@detectTapGestures
                    if ((g.spot(color, s.pawns.getValue(color)[pick], pick) - p).getDistance() < g.cell * 1.1f) ludo.act(LudoAction.MovePawn(color, pick))
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val g = LudoGeometry(center, size.minDimension)
            for (c in s.active) for (i in 0 until LudoBoard.PAWNS) {
                val (at, lift) = g.animated(c, shown.getValue(c)[i].value, i)
                val movable = myMove && c == s.roller && i in s.movable
                drawPawn(at, g.cell, c, lift, if (movable) glow else 0f)
            }
            val face = s.die ?: 0
            if (face > 0) drawDie(g.cellCenter(7, 7), g.cell * 1.5f, face, tumble.value)
        }
    }
}

/** The bottom panel: whatever the current phase asks of the players on this phone. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActionPanel(vm: AppViewModel, ludo: LudoController, s: LudoState, modifier: Modifier) {
    val palette = LocalPalette.current
    Column(
        modifier
            .drawBehind {
                drawRoundRect(Color(0xE62A1407), cornerRadius = CornerRadius(22.dp.toPx()))
                drawRoundRect(Color(0xFFE8B84A).copy(alpha = 0.6f), cornerRadius = CornerRadius(22.dp.toPx()), style = Stroke(1.5.dp.toPx()))
            }
            .padding(14.dp),
    ) {
        AnimatedContent(s.lastEvent, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "event") { e ->
            Txt(e, Type.label.copy(letterSpacing = 0.sp, fontSize = 12.sp), Color(0xFFFFE3A0), Modifier.fillMaxWidth(), TextAlign.Center, maxLines = 1)
        }
        Spacer(Modifier.height(8.dp))
        val gold = Color(0xFFE0A21B)
        when (s.phase) {
            LudoPhase.CHOOSE_OPPONENT -> if (ludo.controls(s.turn)) {
                Txt("${s.seat(s.turn).name}, pick your opponent", Type.heading, Color.White)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (c in s.active - s.turn) {
                        BouncyButton(s.seat(c).name, { ludo.act(LudoAction.Challenge(s.turn, c)) }, color = c.tone, compact = true)
                    }
                    BouncyButton("Match me", { ludo.act(LudoAction.Challenge(s.turn, Ludo.autoOpponent(s, s.turn))) }, color = gold, glyph = Glyph.REFRESH, compact = true)
                }
            } else Waiting("${s.seat(s.turn).name} is choosing an opponent…")

            LudoPhase.DUEL -> DuelView(ludo, s)

            LudoPhase.ROLL -> {
                val r = s.roller!!
                if (ludo.controls(r)) BouncyButton("Roll the die", { ludo.act(LudoAction.Roll(r)) }, Modifier.fillMaxWidth(), gold, Glyph.PLAY)
                else Waiting("${s.seat(r).name} is rolling…")
            }

            LudoPhase.MOVE -> {
                val r = s.roller!!
                Waiting(if (ludo.controls(r)) "Rolled ${s.die} — tap a glowing pawn" else "${s.seat(r).name} is moving…")
            }

            LudoPhase.OVER -> {
                val w = s.winner!!
                Txt("${s.seat(w).name} wins!", Type.display.copy(fontSize = 30.sp), w.tone, Modifier.fillMaxWidth(), TextAlign.Center)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!ludo.isGuest) BouncyButton("Play again", { vm.ludoRematch() }, Modifier.weight(1f), gold, Glyph.REFRESH)
                    BouncyButton("Leave", { vm.back() }, Modifier.weight(1f), palette.surfaceHi, Glyph.BACK)
                }
            }
        }
    }
}

@Composable
private fun Waiting(text: String) {
    Txt(text, Type.body.copy(fontWeight = FontWeight.Bold), Color.White.copy(alpha = 0.85f), Modifier.fillMaxWidth().padding(vertical = 6.dp), TextAlign.Center)
}

/** The Tic-Tac-Toe duel for the die. Duelists on this phone play; everyone else spectates live. */
@Composable
private fun DuelView(ludo: LudoController, s: LudoState) {
    val d = s.duel ?: return
    val xColor = d.colorOf(Mark.X)
    val oColor = d.colorOf(Mark.O)
    val mine = ludo.controls(d.current)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Txt("${s.seat(xColor).name} ✕  vs  ◯ ${s.seat(oColor).name}", Type.body.copy(fontWeight = FontWeight.ExtraBold), Color.White, maxLines = 2)
            Txt("Round ${d.round}" + if (d.draws > 0) " · ${d.draws} draw${if (d.draws > 1) "s" else ""}" else "", Type.label.copy(letterSpacing = 0.sp), Color(0xFFFFE3A0))
            Spacer(Modifier.height(6.dp))
            Txt(
                when {
                    mine -> "${s.seat(d.current).name}, your move"
                    s.seat(d.current).kind == SeatKind.CPU -> "CPU is thinking…"
                    else -> "Spectating · ${s.seat(d.current).name} to move"
                },
                Type.label.copy(letterSpacing = 0.sp), if (mine) d.current.tone else Color.White.copy(alpha = 0.7f),
            )
        }
        BoardCanvas(
            board = Board.decode(d.board), roundKey = d.round + s.turn.ordinal * 1000, win = Board.decode(d.board).winner(),
            look = MarkLook(xColor.tone, oColor.tone, MarkStyle.NEON, 0.11f, 1.4f),
            threats = null, heatmap = null, enabled = mine,
            onCell = { cell -> ludo.act(LudoAction.DuelMove(d.current, cell)) },
            modifier = Modifier.size(150.dp),
        )
    }
}
