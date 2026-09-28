package io.gh.kdbrian.ttac.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.gh.kdbrian.ttac.app.AppViewModel
import io.gh.kdbrian.ttac.app.Screen
import io.gh.kdbrian.ttac.data.MarkStyle
import io.gh.kdbrian.ttac.game.Mark
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.gh.kdbrian.ttac.ui.components.IconBubble
import io.gh.kdbrian.ttac.data.ArcadeGame
import io.gh.kdbrian.ttac.data.Unlocks
import io.gh.kdbrian.ttac.ui.components.BouncyButton
import io.gh.kdbrian.ttac.ui.components.SectionLabel
import io.gh.kdbrian.ttac.ui.draw.ArcadeArt
import io.gh.kdbrian.ttac.ui.draw.ludoBoardBackground
import androidx.compose.foundation.layout.Box
import io.gh.kdbrian.ttac.ui.draw.tones
import androidx.compose.foundation.layout.height
import io.gh.kdbrian.ttac.ui.components.RoundAction
import io.gh.kdbrian.ttac.ui.components.bouncyClick
import io.gh.kdbrian.ttac.ui.draw.GlyphIcon
import io.gh.kdbrian.ttac.ui.components.Panel
import io.gh.kdbrian.ttac.ui.components.Txt
import io.gh.kdbrian.ttac.ui.components.popIn
import io.gh.kdbrian.ttac.ui.draw.Glyph
import io.gh.kdbrian.ttac.ui.draw.drawMark
import io.gh.kdbrian.ttac.ui.theme.LocalPalette
import io.gh.kdbrian.ttac.ui.theme.Type
import io.gh.kdbrian.ttac.ui.theme.heatAt

@Composable
fun HomeScreen(vm: AppViewModel) {
    val palette = LocalPalette.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    val x = Color(settings.xColor)
    val o = Color(settings.oColor)
    // Tell the system (and the startup benchmark) the screen is fully drawn once stats have loaded —
    // saved data always contains at least the default profiles.
    androidx.activity.compose.ReportDrawnWhen { stats.profiles.isNotEmpty() }

    ScreenColumn {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBubble(Glyph.TROPHY, { vm.go(Screen.SCOREBOARD) }, Modifier.popIn(0), label = "Scores")
            Spacer(Modifier.width(10.dp))
            IconBubble(Glyph.MEDAL, { vm.go(Screen.AWARDS) }, Modifier.popIn(30), tint = palette.accent, label = "Awards")
            Spacer(Modifier.weight(1f))
            // Quick controls: sound and vibration without digging into settings.
            IconBubble(
                if (settings.sound) Glyph.SOUND else Glyph.SOUND_OFF,
                { vm.updateSettings { it.copy(sound = !it.sound) } },
                Modifier.popIn(40), label = if (settings.sound) "Mute sound" else "Unmute sound",
            )
            Spacer(Modifier.width(10.dp))
            IconBubble(
                if (settings.haptics) Glyph.VIBRATE else Glyph.VIBRATE_OFF,
                { vm.updateSettings { it.copy(haptics = !it.haptics) } },
                Modifier.popIn(50), label = if (settings.haptics) "Turn vibration off" else "Turn vibration on",
            )
            Spacer(Modifier.width(10.dp))
            IconBubble(Glyph.GEAR, { vm.go(Screen.SETTINGS) }, Modifier.popIn(60), label = "Style and settings")
        }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            LogoBoard(x, o, settings.markStyle, Modifier.size(150.dp).popIn(40))
            Gap(6)
            Txt("TTac", Type.display, modifier = Modifier.popIn(100))
            Txt("draw · bounce · burn the line", Type.label, palette.textDim, Modifier.popIn(150))
        }
        // Pick up the last unfinished game — saved automatically when the app was left.
        val saved by vm.sessions.saved.collectAsStateWithLifecycle()
        saved?.let { s ->
            Gap(20)
            Panel(
                Modifier.fillMaxWidth().popIn(120).bouncyClick { vm.resume() },
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                highlight = palette.good.copy(alpha = 0.8f),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBubble(Glyph.PLAY, { vm.resume() }, tint = palette.good, label = "Continue")
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Txt("CONTINUE WHERE YOU LEFT OFF", Type.label, palette.good)
                        Txt(s.summary, Type.body.copy(fontWeight = FontWeight.Bold), maxLines = 2)
                    }
                    IconBubble(Glyph.CLOSE, { vm.discardSession() }, size = 36.dp, tint = palette.textDim, label = "Discard saved game")
                }
            }
            Gap(10)
        }
        Gap(30)

        // Modes: the headline mode sits in the middle, bigger, with a breathing halo.
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom,
        ) {
            RoundAction(Glyph.PEOPLE, "Pass & Play", { vm.go(Screen.LOCAL_SETUP) }, x, Modifier.popIn(220).padding(bottom = 6.dp), size = 74.dp)
            RoundAction(Glyph.BOT, "Solo vs AI", { vm.go(Screen.SOLO_SETUP) }, palette.accent2, Modifier.popIn(180), size = 108.dp, emphasized = true)
            RoundAction(Glyph.WIFI, "LAN", { vm.go(Screen.LAN) }, o, Modifier.popIn(260).padding(bottom = 6.dp), size = 74.dp)
        }
        Gap(40)

        val solo = stats.soloRecord(settings.difficulty)
        Panel(Modifier.fillMaxWidth().popIn(320).bouncyClick { vm.go(Screen.SCOREBOARD) }, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RecordDonut(solo, Modifier.size(54.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Txt("YOU VS ${settings.difficulty.label.uppercase()} CPU", Type.label, palette.textDim)
                    Txt("${solo.wins}W · ${solo.draws}D · ${solo.losses}L", Type.body.copy(fontWeight = FontWeight.ExtraBold, fontSize = 17.sp))
                    Txt(if (solo.streak > 0) "On a ${solo.streak}-win streak" else "Best streak ${solo.bestStreak}", Type.label.copy(letterSpacing = 0.sp), palette.textDim)
                }
                GlyphIcon(Glyph.BACK, palette.textDim, Modifier.graphicsLayer { rotationZ = 180f }, size = 18.dp)
            }
        }

        SectionLabel("Board games")
        Panel(
            Modifier.fillMaxWidth().popIn(360).bouncyClick { vm.openLudoLobby() },
            contentPadding = PaddingValues(14.dp),
            highlight = Color(0xFFE8B84A).copy(alpha = 0.7f),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(76.dp).ludoBoardBackground())
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Txt("Ludo Palace", Type.body.copy(fontWeight = FontWeight.ExtraBold, fontSize = 17.sp))
                    Txt("2–4 players, CPUs, this phone or LAN. Win a Tic-Tac-Toe duel to roll the die.", Type.label.copy(letterSpacing = 0.sp, fontSize = 12.sp), palette.textDim)
                }
                Txt("PLAY", Type.label, Color(0xFFE8B84A))
            }
        }

        SectionLabel("Arcade")
        ArcadeCard(
            vm, ArcadeGame.BLOCKS,
            concept = "Stack falling blocks and clear the line goal before the well fills. Harder levels mean bigger wells and faster drops.",
            unlockRule = "Win ${Unlocks.BLOCKS_STREAK} in a row to unlock",
            progress = stats.bestLocalStreak, target = Unlocks.BLOCKS_STREAK,
            modifier = Modifier.popIn(380),
        )
        Gap(12)
        ArcadeCard(
            vm, ArcadeGame.HIVE,
            concept = "Trace letters through a honeycomb to find hidden words. Extra words, long-word bonuses, shuffles and hints.",
            unlockRule = "Win ${Unlocks.HIVE_STREAK} Blocks rounds in a row to unlock",
            progress = stats.blocks.streak, target = Unlocks.HIVE_STREAK,
            modifier = Modifier.popIn(440),
        )
        Gap(24)
    }
}

/** Looping demo game: grid draws in, marks appear, the diagonal wins in a burst of heat, repeat. */
@Composable
private fun LogoBoard(x: Color, o: Color, style: MarkStyle, modifier: Modifier) {
    val palette = LocalPalette.current
    val t by rememberInfiniteTransition(label = "logo").animateFloat(
        0f, 1f, infiniteRepeatable(tween(5200, easing = LinearEasing)), label = "t",
    )
    val moves = listOf(0 to Mark.X, 1 to Mark.O, 4 to Mark.X, 2 to Mark.O, 8 to Mark.X)
    Canvas(modifier) {
        val cell = size.minDimension / 3
        val fadeOut = ((t - 0.88f) / 0.12f).coerceIn(0f, 1f)
        val alpha = 1f - fadeOut
        val grid = (t / 0.12f).coerceIn(0f, 1f)
        for (k in 1..2) {
            val len = size.height * 0.9f * grid
            drawLine(palette.grid.copy(alpha = alpha), Offset(k * cell, size.height * 0.05f), Offset(k * cell, size.height * 0.05f + len), cell * 0.05f, StrokeCap.Round)
            drawLine(palette.grid.copy(alpha = alpha), Offset(size.width * 0.05f, k * cell), Offset(size.width * 0.05f + len, k * cell), cell * 0.05f, StrokeCap.Round)
        }
        moves.forEachIndexed { k, (i, m) ->
            val start = 0.12f + k * 0.1f
            val p = ((t - start) / 0.09f).coerceIn(0f, 1f)
            val bounce = if (p < 1f) 0.6f + 0.4f * p else 1f + 0.08f * kotlin.math.sin(((t - start - 0.09f) * 60f).coerceIn(0f, 3.14f))
            drawMark(m, style, i, Offset((i % 3 + 0.5f) * cell, (i / 3 + 0.5f) * cell), cell, if (m == Mark.X) x else o, 0.11f, p, bounce, alpha, palette.isDark)
        }
        val w = ((t - 0.66f) / 0.12f).coerceIn(0f, 1f)
        if (w > 0f) {
            val a = Offset(cell * 0.25f, cell * 0.25f)
            val b = Offset(size.width - cell * 0.25f, size.height - cell * 0.25f)
            val end = a + (b - a) * w
            for (i in listOf(0, 4, 8)) {
                val c = Offset((i % 3 + 0.5f) * cell, (i / 3 + 0.5f) * cell)
                drawCircle(androidx.compose.ui.graphics.Brush.radialGradient(listOf(palette.heatAt(1f).copy(alpha = 0.5f * w * alpha), Color.Transparent), c, cell * 0.6f), cell * 0.6f, c)
            }
            drawLine(palette.heatAt(0.5f).copy(alpha = 0.35f * alpha), a, end, cell * 0.3f, StrokeCap.Round)
            drawLine(palette.heatAt(0.85f).copy(alpha = alpha), a, end, cell * 0.12f, StrokeCap.Round)
            drawLine(Color.White.copy(alpha = 0.8f * alpha), a, end, cell * 0.04f, StrokeCap.Round)
        }
    }
}

/** Arcade teaser: art, concept, unlock progress, and a single free demo while locked. */
@Composable
private fun ArcadeCard(
    vm: AppViewModel,
    game: ArcadeGame,
    concept: String,
    unlockRule: String,
    progress: Int,
    target: Int,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    val stats by vm.stats.collectAsStateWithLifecycle()
    val unlocked = stats.isUnlocked(game)
    val (light, _) = game.tones()
    val screen = if (game == ArcadeGame.BLOCKS) Screen.BLOCKS else Screen.HIVE
    Panel(
        modifier.fillMaxWidth().bouncyClick(unlocked) { vm.go(screen) },
        contentPadding = PaddingValues(14.dp),
        highlight = if (unlocked) light.copy(alpha = 0.7f) else null,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ArcadeArt(game, locked = !unlocked, modifier = Modifier.size(76.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Txt(game.title, Type.body.copy(fontWeight = FontWeight.ExtraBold, fontSize = 17.sp), modifier = Modifier.weight(1f))
                    if (unlocked) Txt("PLAY", Type.label, light)
                }
                Txt(concept, Type.label.copy(letterSpacing = 0.sp, fontSize = 12.sp), palette.textDim)
            }
        }
        if (!unlocked) {
            Spacer(Modifier.height(10.dp))
            Txt("$unlockRule · ${progress.coerceAtMost(target)}/$target", Type.label.copy(letterSpacing = 0.sp), palette.text)
            Spacer(Modifier.height(6.dp))
            Canvas(Modifier.fillMaxWidth().height(6.dp)) {
                val t = (progress.toFloat() / target).coerceIn(0f, 1f)
                drawRoundRect(palette.surfaceHi, cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height))
                if (t > 0f) drawRoundRect(light, size = androidx.compose.ui.geometry.Size(size.width * t, size.height), cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height))
            }
            Spacer(Modifier.height(10.dp))
            if (stats.canDemo(game)) {
                BouncyButton("Try once", { vm.startDemo(game) }, Modifier.fillMaxWidth(), light, Glyph.PLAY, compact = true)
            } else {
                Txt("Free try used — earn it to play again", Type.label.copy(letterSpacing = 0.sp), palette.textDim)
            }
        }
    }
}
