package io.gh.kdbrian.ttac.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.sp
import io.gh.kdbrian.ttac.ui.components.Avatar
import io.gh.kdbrian.ttac.ui.components.bouncyClick
import androidx.compose.animation.AnimatedVisibility
import io.gh.kdbrian.ttac.ui.draw.tones
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.gh.kdbrian.ttac.app.AppViewModel
import io.gh.kdbrian.ttac.data.GameMode
import io.gh.kdbrian.ttac.data.MatchRecord
import io.gh.kdbrian.ttac.data.StatsData
import io.gh.kdbrian.ttac.game.Difficulty
import io.gh.kdbrian.ttac.game.Rules
import io.gh.kdbrian.ttac.ui.components.BouncyButton
import io.gh.kdbrian.ttac.ui.components.Panel
import io.gh.kdbrian.ttac.ui.components.SectionLabel
import io.gh.kdbrian.ttac.ui.components.Segmented
import io.gh.kdbrian.ttac.ui.components.TopBar
import io.gh.kdbrian.ttac.ui.components.Txt
import io.gh.kdbrian.ttac.ui.components.popIn
import io.gh.kdbrian.ttac.ui.draw.Glyph
import io.gh.kdbrian.ttac.ui.theme.LocalPalette
import io.gh.kdbrian.ttac.ui.theme.Type
import io.gh.kdbrian.ttac.ui.theme.heatAt
import kotlinx.coroutines.delay

private enum class Tab(val label: String) { SOLO("Solo"), VERSUS("Versus"), LAN("LAN"), HISTORY("History"), HEAT("Heat") }

@Composable
fun ScoreboardScreen(vm: AppViewModel) {
    val stats by vm.stats.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(Tab.SOLO) }
    var confirmReset by remember { mutableStateOf(false) }
    val palette = LocalPalette.current

    LaunchedEffect(confirmReset) {
        if (confirmReset) { delay(3000); confirmReset = false }
    }

    ScreenColumn {
        TopBar("Scoreboard", { vm.back() })
        Gap(4)
        Segmented(Tab.entries, tab, { it.label }, { tab = it; vm.tapSound() })
        Gap(8)
        AnimatedContent(tab, transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) }, label = "tab") { t ->
            Column {
                when (t) {
                    Tab.SOLO -> SoloTab(stats)
                    Tab.VERSUS -> VersusTab(stats)
                    Tab.LAN -> LanTab(stats, vm)
                    Tab.HISTORY -> HistoryTab(stats)
                    Tab.HEAT -> HeatTab(stats, vm)
                }
            }
        }
        Gap(24)
        BouncyButton(
            if (confirmReset) "Tap again to erase all scores" else "Reset scores",
            { if (confirmReset) { vm.resetScores(); confirmReset = false } else confirmReset = true; vm.tapSound() },
            Modifier.fillMaxWidth(), if (confirmReset) palette.bad else palette.surfaceHi, Glyph.TRASH, compact = true,
        )
        Txt("Players are kept; wins, history and heat are cleared.", Type.label, palette.textDim, Modifier.fillMaxWidth().padding(top = 6.dp), TextAlign.Center)
        Gap(24)
    }
}

@Composable
private fun SoloTab(stats: StatsData) {
    Difficulty.entries.forEachIndexed { i, d ->
        val r = stats.soloRecord(d)
        SectionLabel("${d.label} CPU")
        Panel(Modifier.fillMaxWidth().popIn(i * 70)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RecordDonut(r, Modifier.size(78.dp))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Txt("${r.played} played", Type.heading)
                    Txt(if (r.streak > 0) "${r.streak} in a row" else "Best run: ${r.bestStreak}", Type.body, LocalPalette.current.textDim)
                }
            }
            Gap(12)
            RecordRow(r)
        }
    }
}

@Composable
private fun VersusTab(stats: StatsData) {
    val palette = LocalPalette.current
    val ranked = stats.players.entries.sortedWith(compareByDescending<Map.Entry<String, io.gh.kdbrian.ttac.data.Record>> { it.value.wins }.thenByDescending { it.value.winRate })
    SectionLabel("Leaderboard")
    if (ranked.isEmpty()) {
        EmptyNote("Play a Pass & Play or LAN match to start the leaderboard.")
    } else {
        fun colorOf(id: String) = stats.profiles.firstOrNull { it.id == id }?.color?.let { Color(it) } ?: palette.accent2
        fun nameOf(id: String) = stats.playerNames[id] ?: "Unknown"
        Podium(ranked.take(3).map { (id, rec) -> PodiumEntry(nameOf(id), colorOf(id), rec) })
        ranked.drop(3).forEachIndexed { i, (id, rec) ->
            Panel(Modifier.fillMaxWidth().padding(top = 8.dp).popIn(200 + i * 40), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Txt("${i + 4}", Type.heading, palette.textDim, Modifier.width(30.dp))
                    Avatar(nameOf(id), colorOf(id), size = 34.dp)
                    Spacer(Modifier.width(12.dp))
                    Txt(nameOf(id), Type.body.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f), maxLines = 1)
                    Txt("${rec.wins}W ${rec.draws}D ${rec.losses}L", Type.label, palette.textDim)
                }
            }
        }
    }

    SectionLabel("Head to head")
    val pairs = stats.headToHead.values.filter { it.played > 0 }.sortedByDescending { it.played }
    if (pairs.isEmpty()) EmptyNote("Rivalries show up here.")
    pairs.forEachIndexed { i, h ->
        val ac = stats.profiles.firstOrNull { it.id == h.aId }?.color?.let { Color(it) } ?: palette.accent
        val bc = stats.profiles.firstOrNull { it.id == h.bId }?.color?.let { Color(it) } ?: palette.accent2
        Panel(Modifier.fillMaxWidth().padding(bottom = 10.dp).popIn(i * 50), contentPadding = PaddingValues(14.dp)) {
            Row {
                Txt(h.aName, Type.body.copy(fontWeight = FontWeight.Bold), ac, Modifier.weight(1f), maxLines = 1)
                Txt(h.bName, Type.body.copy(fontWeight = FontWeight.Bold), bc, Modifier.weight(1f), TextAlign.End, maxLines = 1)
            }
            Gap(6)
            SplitBar(h.aWins, h.draws, h.bWins, ac, bc)
            Gap(4)
            Row {
                Txt("${h.aWins}", Type.heading, ac, Modifier.weight(1f))
                Txt("${h.draws} draws · ${h.played} games", Type.label, palette.textDim)
                Txt("${h.bWins}", Type.heading, bc, Modifier.weight(1f), TextAlign.End)
            }
        }
    }
}

@Composable
private fun HistoryTab(stats: StatsData) {
    val palette = LocalPalette.current
    SectionLabel("Recent matches")
    if (stats.history.isEmpty()) EmptyNote("No games yet. Go draw some lines!")
    stats.history.take(60).forEachIndexed { i, m ->
        Panel(Modifier.fillMaxWidth().padding(bottom = 10.dp).popIn((i * 40).coerceAtMost(400)), contentPadding = PaddingValues(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MiniBoard(m.board, m.winCells, Color(m.xColor), Color(m.oColor), Modifier.size(56.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Txt(resultText(m), Type.body.copy(fontWeight = FontWeight.Bold), resultColor(m), maxLines = 1)
                    Txt("${m.xName} vs ${m.oName}", Type.body, palette.text, maxLines = 1)
                    Txt("${m.mode.label} · ${m.size}×${m.size} · ${ago(m.timestamp)}", Type.label, palette.textDim, maxLines = 1)
                }
            }
        }
    }
}

private fun resultText(m: MatchRecord) = when (m.result) {
    "DRAW" -> "Draw"
    "X" -> "${m.xName} won"
    else -> "${m.oName} won"
}

@Composable
private fun resultColor(m: MatchRecord): Color {
    val palette = LocalPalette.current
    return when (m.result) {
        "DRAW" -> palette.accent
        "X" -> if (m.mode == GameMode.SOLO) palette.good else Color(m.xColor)
        else -> if (m.mode == GameMode.SOLO) palette.bad else Color(m.oColor)
    }
}

internal fun ago(ts: Long): String {
    val s = (System.currentTimeMillis() - ts) / 1000
    return when {
        s < 60 -> "just now"
        s < 3600 -> "${s / 60}m ago"
        s < 86_400 -> "${s / 3600}h ago"
        else -> "${s / 86_400}d ago"
    }
}

private enum class HeatKind(val label: String) { WINS("Winning cells"), PLAYS("Moves played") }

@Composable
private fun HeatTab(stats: StatsData, vm: AppViewModel) {
    val palette = LocalPalette.current
    var size by rememberSaveable { mutableIntStateOf(3) }
    var kind by rememberSaveable { mutableStateOf(HeatKind.WINS) }
    val heat = stats.heatFor(size)
    val n = size * size
    val values = if (kind == HeatKind.WINS) heat.normalizedWins(n) else heat.normalizedPlays(n)

    SectionLabel("Board")
    Segmented(Rules.SUPPORTED_SIZES, size, { "$it×$it" }, { size = it; vm.tapSound() }, accent = palette.accent2)
    Gap(10)
    Segmented(HeatKind.entries, kind, { it.label }, { kind = it; vm.tapSound() })
    Gap(14)
    Panel(Modifier.fillMaxWidth()) {
        Txt("${heat.games} games on ${size}×$size", Type.label, palette.textDim)
        Gap(10)
        HeatmapCanvas(size, values, Modifier.fillMaxWidth().aspectRatio(1f))
        Gap(12)
        Canvas(Modifier.fillMaxWidth().height(12.dp)) {
            drawRoundRect(Brush.horizontalGradient(palette.heat), cornerRadius = CornerRadius(this.size.height / 2))
        }
        Row {
            Txt("cold", Type.label, palette.textDim, Modifier.weight(1f))
            Txt("hot", Type.label, palette.textDim)
        }
        Gap(4)
        Txt(
            if (kind == HeatKind.WINS) "The hotter the zone, the more often it sits on a winning line." else "The hotter the zone, the more often it gets filled.",
            Type.body, palette.textDim,
        )
    }
}

/** Thermal field for the whole board: no numbers, just how hot each zone runs. */
@Composable
private fun HeatmapCanvas(size: Int, values: FloatArray, modifier: Modifier) {
    val palette = LocalPalette.current
    val reveal = remember(size, values.contentHashCode()) { Animatable(0f) }
    LaunchedEffect(size, values.contentHashCode()) { reveal.animateTo(1f, tween(1100, easing = FastOutSlowInEasing)) }
    val pulse by rememberInfiniteTransition(label = "heat").animateFloat(0f, 1f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "p")
    Canvas(modifier) {
        val cell = this.size.minDimension / size
        val side = cell * size
        val corner = CornerRadius(cell * 0.2f)
        val clip = androidx.compose.ui.graphics.Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, side, side, corner)) }
        clipPath(clip) {
            drawRect(palette.heatAt(0f).copy(alpha = if (palette.isDark) 0.55f else 0.35f), size = Size(side, side))
            for (i in (0 until size * size).sortedBy { values.getOrElse(it) { 0f } }) {
                val r = i / size
                val c = i % size
                val local = ((reveal.value * 1.6f) - (r + c) * 0.08f).coerceIn(0f, 1f)
                val v = values.getOrElse(i) { 0f } * local
                if (v <= 0.02f) continue
                val center = Offset((c + 0.5f) * cell, (r + 0.5f) * cell)
                val radius = cell * (0.6f + 0.6f * v) * (1f + 0.08f * v * pulse)
                drawCircle(
                    Brush.radialGradient(
                        0f to palette.heatAt(v),
                        0.5f to palette.heatAt(v * 0.7f).copy(alpha = 0.6f),
                        1f to Color.Transparent,
                        center = center, radius = radius,
                    ),
                    radius, center,
                )
            }
        }
        for (k in 1 until size) {
            drawLine(Color.White.copy(alpha = 0.18f), Offset(k * cell, cell * 0.2f), Offset(k * cell, side - cell * 0.2f), 1.5f)
            drawLine(Color.White.copy(alpha = 0.18f), Offset(cell * 0.2f, k * cell), Offset(side - cell * 0.2f, k * cell), 1.5f)
        }
        drawRoundRect(palette.outline, size = Size(side, side), cornerRadius = corner, style = androidx.compose.ui.graphics.drawscope.Stroke(1.2.dp.toPx()))
    }
}

@Composable
private fun EmptyNote(text: String) {
    Txt(text, Type.body, LocalPalette.current.textDim, Modifier.fillMaxWidth().padding(vertical = 12.dp), TextAlign.Center)
}

private data class PodiumEntry(val name: String, val color: Color, val record: io.gh.kdbrian.ttac.data.Record)

/** Top three on rising podium blocks, gold in the middle. */
@Composable
private fun Podium(entries: List<PodiumEntry>) {
    val palette = LocalPalette.current
    val order = listOf(1, 0, 2).filter { it < entries.size }
    val heights = mapOf(0 to 124.dp, 1 to 92.dp, 2 to 70.dp)
    val medals = mapOf(0 to io.gh.kdbrian.ttac.data.Medal.GOLD, 1 to io.gh.kdbrian.ttac.data.Medal.SILVER, 2 to io.gh.kdbrian.ttac.data.Medal.BRONZE)
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
        for (rank in order) {
            val e = entries[rank]
            val rise = remember(e) { Animatable(0f) }
            LaunchedEffect(e) { delay(rank * 120L); rise.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 160f)) }
            val (light, dark) = medals.getValue(rank).tones()
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Avatar(e.name, e.color, size = if (rank == 0) 62.dp else 50.dp, ring = light)
                Spacer(Modifier.height(6.dp))
                Txt(e.name, Type.body.copy(fontWeight = FontWeight.Bold), maxLines = 1)
                Txt("${e.record.wins} wins", Type.label, palette.textDim)
                Spacer(Modifier.height(6.dp))
                Canvas(Modifier.fillMaxWidth().height(heights.getValue(rank))) {
                    val h = size.height * rise.value
                    val top = size.height - h
                    val r = CornerRadius(14.dp.toPx())
                    drawRoundRect(Brush.verticalGradient(listOf(light.copy(alpha = 0.95f), dark.copy(alpha = 0.75f)), top, size.height), Offset(0f, top), Size(size.width, h), r)
                    drawRoundRect(Color.White.copy(alpha = 0.35f), Offset(0f, top), Size(size.width, h), r, style = androidx.compose.ui.graphics.drawscope.Stroke(1.2.dp.toPx()))
                }
                Txt("${rank + 1}", Type.display.copy(fontSize = 30.sp), Color.White, Modifier.graphicsLayer { translationY = -heights.getValue(rank).toPx() * rise.value + 34.dp.toPx() }.alpha(rise.value))
            }
        }
    }
}

/** LAN standings by alias, each with their streaks and a tap-to-open match history. */
@Composable
private fun LanTab(stats: StatsData, vm: AppViewModel) {
    val palette = LocalPalette.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val members = io.gh.kdbrian.ttac.data.LanBoard.members(stats, settings.lanAlias ?: "You")
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    SectionLabel("LAN leaderboard")
    if (members.isEmpty()) {
        EmptyNote("Host or join a LAN match to start the rivalry board.")
        return
    }
    members.forEachIndexed { i, m ->
        Panel(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
                .popIn((i * 40).coerceAtMost(300))
                .bouncyClick { open = if (open == m.id) null else m.id; vm.tapSound() },
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            highlight = if (open == m.id) palette.accent else null,
        ) {
            RivalRow(m, rank = i + 1)
            AnimatedVisibility(open == m.id) {
                Column {
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatTile("BEST RUN", m.record.bestStreak.toString(), palette.good, Modifier.weight(1f))
                        StatTile("WORST SLIDE", m.record.worstLossStreak.toString(), palette.bad, Modifier.weight(1f))
                        StatTile("PLAYED", m.record.played.toString(), palette.accent2, Modifier.weight(1f))
                    }
                    io.gh.kdbrian.ttac.data.LanBoard.historyOf(stats, m.id).take(10).forEach { h ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            MiniBoard(h.board, h.winCells, Color(h.xColor), Color(h.oColor), Modifier.size(40.dp))
                            Spacer(Modifier.width(10.dp))
                            val won = (h.result == "X" && h.xId == m.id) || (h.result == "O" && h.oId == m.id)
                            val verdict = when {
                                h.result == "DRAW" -> "Draw"
                                won -> "Won"
                                else -> "Lost"
                            }
                            val opponent = if (h.xId == m.id) h.oName else h.xName
                            Txt("$verdict vs $opponent", Type.body.copy(fontWeight = FontWeight.Bold), when (verdict) { "Won" -> palette.good; "Lost" -> palette.bad; else -> palette.accent }, Modifier.weight(1f), maxLines = 1)
                            Txt(ago(h.timestamp), Type.label, palette.textDim)
                        }
                    }
                }
            }
        }
    }
}
