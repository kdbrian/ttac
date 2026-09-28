package io.gh.kdbrian.ttac.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.gh.kdbrian.ttac.app.AppViewModel
import io.gh.kdbrian.ttac.data.Profile
import io.gh.kdbrian.ttac.game.Difficulty
import io.gh.kdbrian.ttac.game.Rules
import io.gh.kdbrian.ttac.net.LanSession
import io.gh.kdbrian.ttac.net.LanStatus
import io.gh.kdbrian.ttac.ui.components.BouncyButton
import io.gh.kdbrian.ttac.ui.components.ColorSwatches
import io.gh.kdbrian.ttac.ui.components.IconBubble
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
import io.gh.kdbrian.ttac.ui.theme.LocalPalette
import io.gh.kdbrian.ttac.ui.theme.MarkColors
import io.gh.kdbrian.ttac.ui.theme.Type

private fun sizeLabel(n: Int) = "$n×$n"
private fun rulesHint(n: Int) = "Get ${Rules.of(n).winLength} in a row"

// ---- Solo ------------------------------------------------------------------------------------

@Composable
fun SoloSetupScreen(vm: AppViewModel) {
    val palette = LocalPalette.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    var difficulty by rememberSaveable { mutableStateOf(settings.difficulty) }
    var size by rememberSaveable { mutableIntStateOf(settings.boardSize) }

    ScreenColumn {
        TopBar("Solo vs AI", { vm.back() })
        SectionLabel("Difficulty")
        Segmented(Difficulty.entries, difficulty, { it.label }, { difficulty = it; vm.tapSound() }, Modifier.popIn(40))
        Txt(
            when (difficulty) {
                Difficulty.EASY -> "Plays loose. Good for warming up."
                Difficulty.MEDIUM -> "Always blocks, sometimes schemes."
                Difficulty.HARD -> "Searches ahead. Unbeatable on 3×3."
            },
            Type.body, palette.textDim, Modifier.padding(top = 8.dp, start = 4.dp),
        )
        SectionLabel("Board")
        Segmented(Rules.SUPPORTED_SIZES, size, ::sizeLabel, { size = it; vm.tapSound() }, Modifier.popIn(80), palette.accent2)
        Txt(rulesHint(size), Type.body, palette.textDim, Modifier.padding(top = 8.dp, start = 4.dp))

        SectionLabel("Your record · ${difficulty.label}")
        val record = stats.soloRecord(difficulty)
        Panel(Modifier.fillMaxWidth().popIn(120)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RecordDonut(record, Modifier.size(84.dp))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Txt("${record.played} games", Type.heading)
                    Txt(if (record.streak > 0) "Current streak: ${record.streak}" else "No active streak", Type.body, palette.textDim)
                }
            }
            Gap(12)
            RecordRow(record)
        }
        Gap(24)
        BouncyButton("Start", { vm.startSolo(difficulty, size) }, Modifier.fillMaxWidth().popIn(180), palette.accent, Glyph.BOT)
        Gap(24)
    }
}

// ---- Pass & play -----------------------------------------------------------------------------

@Composable
fun LocalSetupScreen(vm: AppViewModel) {
    val palette = LocalPalette.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    val profiles = stats.profiles
    var xId by rememberSaveable { mutableStateOf(settings.lastXProfileId) }
    var oId by rememberSaveable { mutableStateOf(settings.lastOProfileId) }
    var size by rememberSaveable { mutableIntStateOf(settings.boardSize) }

    // Fall back to the first two profiles when nothing (valid) is remembered.
    val x = profiles.firstOrNull { it.id == xId } ?: profiles.getOrNull(0)
    val o = profiles.firstOrNull { it.id == oId && it.id != x?.id } ?: profiles.firstOrNull { it.id != x?.id }

    ScreenColumn {
        TopBar("Pass & Play", { vm.back() })
        SectionLabel("Player ✕")
        ProfileChips(profiles, x?.id, { xId = it.id; if (it.id == o?.id) oId = null; vm.tapSound() })
        SectionLabel("Player ◯")
        ProfileChips(profiles, o?.id, { oId = it.id; vm.tapSound() }, disabledId = x?.id)

        if (x != null && o != null) {
            val h2h = stats.headToHead[io.gh.kdbrian.ttac.data.HeadToHead.key(x.id, o.id)]
            if (h2h != null && h2h.played > 0) {
                val xw = if (h2h.aId == x.id) h2h.aWins else h2h.bWins
                val ow = if (h2h.aId == x.id) h2h.bWins else h2h.aWins
                Gap(12)
                Panel(Modifier.fillMaxWidth().popIn(), contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
                    Txt("HEAD TO HEAD", Type.label, palette.textDim)
                    Gap(6)
                    SplitBar(xw, h2h.draws, ow, Color(x.color), Color(o.color))
                    Gap(6)
                    Row {
                        Txt("${x.name} $xw", Type.body.copy(fontWeight = FontWeight.Bold), Color(x.color), Modifier.weight(1f))
                        Txt("${h2h.draws} draws", Type.body, palette.textDim)
                        Txt("$ow ${o.name}", Type.body.copy(fontWeight = FontWeight.Bold), Color(o.color), Modifier.weight(1f), align = androidx.compose.ui.text.style.TextAlign.End)
                    }
                }
            }
        }

        SectionLabel("Board")
        Segmented(Rules.SUPPORTED_SIZES, size, ::sizeLabel, { size = it; vm.tapSound() }, accent = palette.accent2)
        Txt(rulesHint(size), Type.body, palette.textDim, Modifier.padding(top = 8.dp, start = 4.dp))

        ProfileManager(vm, profiles)

        Gap(20)
        BouncyButton(
            "Start match", { if (x != null && o != null) vm.startLocal(x, o, size) },
            Modifier.fillMaxWidth(), Color(settings.xColor), Glyph.PEOPLE, enabled = x != null && o != null,
        )
        Gap(24)
    }
}

/** Add new players and remove old ones. */
@Composable
fun ProfileManager(vm: AppViewModel, profiles: List<Profile>) {
    val palette = LocalPalette.current
    var open by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var color by rememberSaveable { mutableLongStateOf(MarkColors[profiles.size % MarkColors.size]) }

    Gap(16)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().bouncyClick { open = !open; vm.tapSound() }.padding(vertical = 6.dp)) {
        GlyphIcon(if (open) Glyph.CLOSE else Glyph.PLUS, palette.accent)
        Spacer(Modifier.width(10.dp))
        Txt(if (open) "Close player editor" else "Add or remove players", Type.body.copy(fontWeight = FontWeight.Bold), palette.accent)
    }
    AnimatedVisibility(open, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        Panel(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            NameField(name, { name = it }, "New player name", Modifier.fillMaxWidth())
            Gap(12)
            ColorSwatches(MarkColors, color, { color = it })
            Gap(12)
            BouncyButton("Add player", {
                if (name.isNotBlank()) {
                    vm.addProfile(name, color)
                    name = ""
                    color = MarkColors[(profiles.size + 1) % MarkColors.size]
                }
            }, Modifier.fillMaxWidth(), Color(color), Glyph.PLUS, enabled = name.isNotBlank(), compact = true)
            if (profiles.size > 2) {
                SectionLabel("Remove")
                profiles.forEach { p ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Canvas(Modifier.size(14.dp)) { drawCircle(Color(p.color)) }
                        Spacer(Modifier.width(10.dp))
                        Txt(p.name, Type.body, modifier = Modifier.weight(1f))
                        IconBubble(Glyph.TRASH, { vm.deleteProfile(p.id) }, tint = palette.bad)
                    }
                }
            }
        }
    }
}

@Composable
fun SplitBar(a: Int, draws: Int, b: Int, aColor: Color, bColor: Color, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    val total = (a + draws + b).coerceAtLeast(1)
    val grow by rememberInfiniteTransition(label = "split").animateFloat(0.98f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "g")
    Canvas(modifier.fillMaxWidth().padding(vertical = 2.dp).height(14.dp)) {
        val h = size.height
        val r = androidx.compose.ui.geometry.CornerRadius(h / 2)
        drawRoundRect(palette.surfaceHi, cornerRadius = r)
        val wa = size.width * a / total
        val wd = size.width * draws / total
        val wb = size.width * b / total
        if (wa > 0) drawRoundRect(aColor, Offset.Zero, Size(wa * grow, h), r)
        if (wd > 0) drawRoundRect(palette.textDim.copy(alpha = 0.4f), Offset(wa, 0f), Size(wd, h))
        if (wb > 0) drawRoundRect(bColor, Offset(size.width - wb * grow, 0f), Size(wb * grow, h), r)
    }
}
