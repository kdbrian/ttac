package io.gh.kdbrian.ttac.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.gh.kdbrian.ttac.app.AppViewModel
import io.gh.kdbrian.ttac.data.MarkStyle
import io.gh.kdbrian.ttac.game.Mark
import io.gh.kdbrian.ttac.game.Rules
import io.gh.kdbrian.ttac.net.LanStatus
import io.gh.kdbrian.ttac.ui.components.Avatar
import io.gh.kdbrian.ttac.ui.components.BouncyButton
import io.gh.kdbrian.ttac.ui.components.NameField
import io.gh.kdbrian.ttac.ui.components.Panel
import io.gh.kdbrian.ttac.ui.components.SectionLabel
import io.gh.kdbrian.ttac.ui.components.Segmented
import io.gh.kdbrian.ttac.ui.components.TopBar
import io.gh.kdbrian.ttac.ui.components.Txt
import io.gh.kdbrian.ttac.ui.components.bouncyClick
import io.gh.kdbrian.ttac.ui.components.popIn
import io.gh.kdbrian.ttac.ui.draw.Glyph
import io.gh.kdbrian.ttac.ui.draw.GlyphIcon
import io.gh.kdbrian.ttac.ui.draw.drawMark
import io.gh.kdbrian.ttac.ui.theme.LocalPalette
import io.gh.kdbrian.ttac.ui.theme.Type
import io.gh.kdbrian.ttac.ui.theme.heatAt
import androidx.compose.ui.draw.drawBehind
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun LanScreen(vm: AppViewModel) {
    val palette = LocalPalette.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    val status by vm.lan.status.collectAsStateWithLifecycle()
    val hosts by vm.lan.hosts.collectAsStateWithLifecycle()
    var meId by rememberSaveable { mutableStateOf(settings.lanProfileId) }
    var size by rememberSaveable { mutableStateOf(settings.boardSize) }
    var code by rememberSaveable { mutableStateOf("") }
    var codeError by rememberSaveable { mutableStateOf(false) }
    val me = stats.profiles.firstOrNull { it.id == meId } ?: stats.profiles.firstOrNull()
    var scanning by rememberSaveable { mutableStateOf(false) }
    val myAlias = settings.lanAlias ?: "…"
    androidx.activity.compose.BackHandler(scanning) { scanning = false }
    val x = Color(settings.xColor)
    val o = Color(settings.oColor)

    // Browse for hosts while this screen is idle.
    LaunchedEffect(status) {
        if (status is LanStatus.Idle) vm.lan.startDiscovery()
    }
    DisposableEffect(Unit) { onDispose { vm.lan.stopDiscovery() } }

    val hosting = status as? LanStatus.Hosting
    val connecting = status as? LanStatus.Connecting
    val busy = hosting != null || connecting != null

    Box(Modifier.fillMaxSize()) {
        // Broadcasting waves while hosting; converging signals while connecting.
        AnimatedVisibility(busy, enter = fadeIn(tween(500)), exit = fadeOut(tween(300))) {
            SignalBackdrop(inbound = connecting != null, x = x, o = o, modifier = Modifier.fillMaxSize())
        }

        ScreenColumn {
            TopBar("LAN Match", { vm.back() })
            Txt("Both phones need to be on the same Wi‑Fi.", Type.body, palette.textDim, Modifier.fillMaxWidth(), TextAlign.Center)

            when {
                hosting != null -> HostingCard(hosting, x) { vm.lan.close(); vm.tapSound() }
                connecting != null -> ConnectingCard(connecting.alias, o) { vm.lan.close(); vm.tapSound() }
                else -> {
                    Gap(10)
                    Panel(Modifier.fillMaxWidth().popIn(), contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Avatar(myAlias, x, size = 46.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Txt("ON THE NETWORK YOU'RE", Type.label, palette.textDim)
                                Txt(myAlias, Type.body.copy(fontWeight = FontWeight.ExtraBold, fontSize = 18.sp), maxLines = 1)
                            }
                        }
                    }
                    SectionLabel("Play as")
                    ProfileChips(stats.profiles, me?.id, { meId = it.id; vm.tapSound() })

                    SectionLabel("Host a session")
                    Panel(Modifier.fillMaxWidth()) {
                        Segmented(Rules.SUPPORTED_SIZES, size, { "$it×$it" }, { size = it; vm.tapSound() }, accent = palette.accent2)
                        Spacer(Modifier.padding(6.dp))
                        BouncyButton("Host as ✕", { me?.let { vm.hostLan(it, size) } }, Modifier.fillMaxWidth(), x, Glyph.WIFI, enabled = me != null)
                    }

                    SectionLabel("Sessions nearby")
                    Panel(Modifier.fillMaxWidth()) {
                        if (hosts.isEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                MiniRadar(Modifier.size(36.dp))
                                Spacer(Modifier.width(12.dp))
                                Txt("Looking for sessions…", Type.body, palette.textDim)
                            }
                        }
                        hosts.forEachIndexed { i, h ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .popIn(i * 60)
                                    .bouncyClick(me != null) { me?.let { vm.joinLan(it, h) } }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Avatar(h.hostName.ifBlank { h.alias }, o, size = 40.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Txt(h.alias, Type.body.copy(fontWeight = FontWeight.ExtraBold, fontSize = 16.sp), maxLines = 1)
                                    Txt("hosted by ${h.hostName}", Type.label.copy(letterSpacing = 0.sp), palette.textDim, maxLines = 1)
                                }
                                BouncyButton("Join", { me?.let { vm.joinLan(it, h) } }, color = o, compact = true, enabled = me != null)
                            }
                        }
                        Spacer(Modifier.padding(6.dp))
                        Txt("HAVE A SESSION CODE?", Type.label, palette.textDim)
                        Spacer(Modifier.padding(3.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            NameField(
                                code,
                                { raw -> code = raw.uppercase().filter { it.isLetterOrDigit() || it == '-' }.take(11); codeError = false },
                                "K7Q2M-9XZ4P",
                                Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(10.dp))
                            io.gh.kdbrian.ttac.ui.components.IconBubble(Glyph.QR, { scanning = true; vm.tapSound() }, size = 50.dp, label = "Scan a QR code")
                        }
                        if (codeError) Txt("That code doesn't look right.", Type.label.copy(letterSpacing = 0.sp), palette.bad, Modifier.padding(top = 6.dp, start = 6.dp))
                        Spacer(Modifier.padding(5.dp))
                        BouncyButton(
                            "Join with code", { if (me != null) codeError = !vm.joinLanByCode(me, code) },
                            Modifier.fillMaxWidth(), o, Glyph.WIFI, enabled = me != null && code.count { it != '-' } == 10, compact = true,
                        )
                    }

                    // Rivals: everyone this device has met on the LAN, with their streaks.
                    val rivals = io.gh.kdbrian.ttac.data.LanBoard.members(stats, myAlias).filter { it.isRemote }
                    if (rivals.isNotEmpty()) {
                        SectionLabel("Rivals")
                        Panel(Modifier.fillMaxWidth()) {
                            rivals.take(3).forEach { r -> RivalRow(r) }
                            Spacer(Modifier.padding(4.dp))
                            BouncyButton("Full LAN leaderboard", { vm.go(io.gh.kdbrian.ttac.app.Screen.SCOREBOARD) }, Modifier.fillMaxWidth(), palette.surfaceHi, Glyph.TROPHY, compact = true)
                        }
                    }
                }
            }

            when (val s = status) {
                is LanStatus.Failed -> Txt(s.reason, Type.body, palette.bad, Modifier.fillMaxWidth().padding(top = 12.dp), TextAlign.Center)
                is LanStatus.Disconnected -> Txt(s.reason, Type.body, palette.bad, Modifier.fillMaxWidth().padding(top = 12.dp), TextAlign.Center)
                else -> {}
            }
            Gap(24)
        }

        if (scanning) {
            io.gh.kdbrian.ttac.ui.components.QrScanner(
                accept = { io.gh.kdbrian.ttac.net.JoinLink.parse(it) != null },
                onResult = { text ->
                    scanning = false
                    val (scanned, alias) = io.gh.kdbrian.ttac.net.JoinLink.parse(text) ?: return@QrScanner
                    me?.let { vm.joinLanByCode(it, scanned, alias) }
                },
                onClose = { scanning = false },
            )
        }
    }
}

/** One LAN rival: alias, name, record and a flame for any live streak. */
@Composable
fun RivalRow(member: io.gh.kdbrian.ttac.data.LanMember, rank: Int? = null) {
    val palette = LocalPalette.current
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (rank != null) Txt("$rank", Type.heading, if (rank == 1) palette.accent else palette.textDim, Modifier.width(28.dp))
        Avatar(member.alias, if (member.isRemote) palette.accent2 else palette.accent, size = 38.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Txt(member.alias, Type.body.copy(fontWeight = FontWeight.Bold), maxLines = 1)
            Txt("${member.name} · ${member.record.wins}W ${member.record.draws}D ${member.record.losses}L", Type.label.copy(letterSpacing = 0.sp), palette.textDim, maxLines = 1)
        }
        StreakChip(member.record)
    }
}

/** Heat-coloured flame chip for a win streak, cold chip for a losing one. */
@Composable
fun StreakChip(record: io.gh.kdbrian.ttac.data.Record) {
    val palette = LocalPalette.current
    val (glyph, count, color) = when {
        record.streak > 0 -> Triple(Glyph.FLAME, record.streak, palette.heatAt((record.streak / 10f).coerceIn(0.4f, 1f)))
        record.lossStreak > 0 -> Triple(Glyph.SNOW, record.lossStreak, Color(0xFF59B8FF))
        else -> Triple(Glyph.FLAME, 0, palette.textDim)
    }
    Row(
        Modifier
            .drawBehind { drawRoundRect(color.copy(alpha = 0.16f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2)) }
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphIcon(glyph, color, size = 14.dp)
        Spacer(Modifier.width(4.dp))
        Txt("$count", Type.label, color)
    }
}

@Composable
private fun HostingCard(hosting: LanStatus.Hosting, color: Color, onCancel: () -> Unit) {
    val palette = LocalPalette.current
    Gap(40)
    Column(Modifier.fillMaxWidth().popIn(), horizontalAlignment = Alignment.CenterHorizontally) {
        Txt("YOUR SESSION", Type.label, palette.textDim)
        Spacer(Modifier.padding(4.dp))
        Txt(hosting.alias, Type.display.copy(fontSize = 34.sp), align = TextAlign.Center)
        Spacer(Modifier.padding(6.dp))
        WaitingDots("Waiting for a challenger")
        Gap(120)
        if (hosting.code != null) {
            // Three ways in: auto-discovery (nothing to do), scan this QR, or type the code.
            // No card behind this: the QR and code sit straight on the signal animation.
            Column(Modifier.fillMaxWidth().padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    io.gh.kdbrian.ttac.ui.components.QrCode(io.gh.kdbrian.ttac.net.JoinLink.build(hosting.code, hosting.alias), color, Modifier.size(128.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Txt("SCAN OR TAP TO COPY", Type.label, palette.textDim)
                        Spacer(Modifier.padding(2.dp))
                        CopyableCode(hosting.code, color)
                        Spacer(Modifier.padding(4.dp))
                        Txt("Nearby phones also see this session automatically.", Type.label.copy(letterSpacing = 0.sp), palette.textDim)
                    }
                }
            }
        }
        Gap(20)
        BouncyButton("Stop hosting", onCancel, Modifier.width(220.dp), palette.surfaceHi, Glyph.CLOSE, compact = true)
    }
}

/** The session code; a tap copies it to the clipboard and briefly confirms. */
@Composable
private fun CopyableCode(code: String, color: Color) {
    val palette = LocalPalette.current
    @Suppress("DEPRECATION")
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) { if (copied) { kotlinx.coroutines.delay(1500); copied = false } }
    Row(
        Modifier
            .bouncyClick {
                clipboard.setText(androidx.compose.ui.text.AnnotatedString(code))
                copied = true
            }
            .semantics { contentDescription = "Copy session code $code" }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Txt(code, Type.heading.copy(fontFamily = FontFamily.Monospace, letterSpacing = 1.sp, fontSize = 17.sp), color, maxLines = 1)
        Spacer(Modifier.width(8.dp))
        GlyphIcon(if (copied) Glyph.CHECK else Glyph.COPY, if (copied) palette.good else palette.textDim, size = 16.dp)
    }
    AnimatedVisibility(copied, enter = fadeIn(), exit = fadeOut()) {
        Txt("Copied!", Type.label.copy(letterSpacing = 0.sp), palette.good)
    }
}

@Composable
private fun ConnectingCard(alias: String, color: Color, onCancel: () -> Unit) {
    val palette = LocalPalette.current
    Gap(40)
    Column(Modifier.fillMaxWidth().popIn(), horizontalAlignment = Alignment.CenterHorizontally) {
        Txt("JOINING", Type.label, palette.textDim)
        Spacer(Modifier.padding(4.dp))
        Txt(alias, Type.display.copy(fontSize = 34.sp), color, align = TextAlign.Center)
        Spacer(Modifier.padding(6.dp))
        WaitingDots("Connecting")
        Gap(200)
        BouncyButton("Cancel", onCancel, Modifier.width(200.dp), palette.surfaceHi, Glyph.CLOSE, compact = true)
    }
}

@Composable
private fun WaitingDots(text: String) {
    val palette = LocalPalette.current
    val t by rememberInfiniteTransition(label = "dots").animateFloat(0f, 3f, infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "t")
    Txt(text + ".".repeat(t.toInt() + 1).padEnd(3, ' '), Type.body.copy(fontWeight = FontWeight.Bold), palette.textDim)
}

@Composable
private fun MiniRadar(modifier: Modifier) {
    val palette = LocalPalette.current
    val t by rememberInfiniteTransition(label = "radar").animateFloat(0f, 1f, infiniteRepeatable(tween(1600)), label = "t")
    Canvas(modifier) {
        val r = size.minDimension / 2
        for (k in 0..2) {
            val p = (t + k / 3f) % 1f
            drawCircle(palette.accent.copy(alpha = (1f - p) * 0.8f), r * p, style = Stroke(3.dp.toPx() * (1f - p) + 1f))
        }
        drawCircle(palette.accent, r * 0.16f)
    }
}

/**
 * Full-screen signal animation behind the LAN screen.
 * Hosting: sonar waves radiate outward while ✕ and ◯ orbit the centre.
 * Connecting: rings and sparks converge inward toward the centre.
 */
@Composable
private fun SignalBackdrop(inbound: Boolean, x: Color, o: Color, modifier: Modifier) {
    val palette = LocalPalette.current
    val t by rememberInfiniteTransition(label = "signal").animateFloat(0f, 1f, infiniteRepeatable(tween(4200, easing = LinearEasing)), label = "t")
    val spin by rememberInfiniteTransition(label = "orbit").animateFloat(0f, 1f, infiniteRepeatable(tween(5200, easing = LinearEasing)), label = "s")
    val mix by animateFloatAsState(if (inbound) 1f else 0f, tween(500), label = "mix")
    Canvas(modifier) {
        val c = Offset(size.width / 2, size.height * 0.36f)
        val reach = size.maxDimension * 0.75f
        val wave = androidx.compose.ui.graphics.lerp(x, o, mix)

        // Viscous, cloth-like ripples: outward when hosting, inward when connecting. Each ripple is a
        // closed path bent by drifting sine folds; it eases out (quick start, slow drag) like thick liquid.
        val tau = 2f * PI.toFloat()
        for (k in 0 until 6) {
            val phase = (t + k / 6f) % 1f
            val eased = 1f - (1f - phase) * (1f - phase) * (1f - phase)
            val p = if (inbound) 1f - eased else eased
            val r = reach * (0.04f + 0.96f * p)
            val a = (if (inbound) phase else 1f - phase).coerceIn(0f, 1f)
            // Folds are deepest near the source and relax as the ripple spreads.
            val fold = 0.09f * (1f - 0.6f * p)
            val ripple = androidx.compose.ui.graphics.Path()
            val steps = 120
            for (i in 0..steps) {
                val th = i / steps.toFloat() * tau
                val wobble = 1f +
                    fold * 0.55f * sin(3f * th + t * tau + k * 1.7f) +
                    fold * 0.30f * sin(5f * th - t * tau * 1.4f + k * 0.9f) +
                    fold * 0.15f * cos(8f * th + t * tau * 0.6f + spin * tau)
                val pt = Offset(c.x + cos(th) * r * wobble, c.y + sin(th) * r * wobble * 0.92f)
                if (i == 0) ripple.moveTo(pt.x, pt.y) else ripple.lineTo(pt.x, pt.y)
            }
            ripple.close()
            // A faint fill per ripple so layers stack like sheets of cloth…
            drawPath(ripple, wave.copy(alpha = 0.05f * a))
            // …a soft, thick body…
            drawPath(ripple, wave.copy(alpha = 0.22f * a), style = Stroke((14.dp.toPx() * a).coerceAtLeast(1f), join = androidx.compose.ui.graphics.StrokeJoin.Round))
            // …and a thin sheen riding the fold.
            drawPath(ripple, Color.White.copy(alpha = 0.28f * a), style = Stroke((2.dp.toPx() * a).coerceAtLeast(0.5f), join = androidx.compose.ui.graphics.StrokeJoin.Round))
        }
        drawCircle(Brush.radialGradient(listOf(wave.copy(alpha = 0.35f), Color.Transparent), c, reach * 0.45f), reach * 0.45f, c)

        // Sparks streaming in along spokes while connecting.
        if (mix > 0.01f) {
            for (k in 0 until 18) {
                val ang = k * (2 * PI.toFloat() / 18) + k * 0.37f
                val p = 1f - ((t * 1.3f + k * 0.13f) % 1f)
                val d = reach * 0.9f * p
                val pos = c + Offset(cos(ang) * d, sin(ang) * d)
                drawCircle((if (k % 2 == 0) x else o).copy(alpha = mix * (1f - p) * 0.9f), 3.dp.toPx() + 3.dp.toPx() * (1f - p), pos)
            }
        }

        // ✕ and ◯ orbiting the session — two players circling each other.
        val orbit = size.minDimension * 0.28f
        val a1 = spin * 2 * PI.toFloat()
        val cell = 56.dp.toPx()
        val pull = if (inbound) 0.55f + 0.45f * (1f - t) else 1f
        drawMark(Mark.X, MarkStyle.NEON, 1, c + Offset(cos(a1) * orbit * pull, sin(a1) * orbit * 0.55f * pull), cell, x, 0.12f, 1f, darkBackground = palette.isDark)
        drawMark(Mark.O, MarkStyle.NEON, 2, c + Offset(cos(a1 + PI.toFloat()) * orbit * pull, sin(a1 + PI.toFloat()) * orbit * 0.55f * pull), cell, o, 0.12f, 1f, darkBackground = palette.isDark)
        drawCircle(Color.White.copy(alpha = 0.9f), 5.dp.toPx(), c)
        drawCircle(wave, 9.dp.toPx(), c, style = Stroke(2.dp.toPx()))
    }
}
