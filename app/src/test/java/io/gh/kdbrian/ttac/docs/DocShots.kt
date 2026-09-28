package io.gh.kdbrian.ttac.docs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import io.gh.kdbrian.ttac.app.AppViewModel
import io.gh.kdbrian.ttac.app.Celebration
import io.gh.kdbrian.ttac.data.ArcadeGame
import io.gh.kdbrian.ttac.data.MarkStyle
import io.gh.kdbrian.ttac.data.Medal
import io.gh.kdbrian.ttac.data.MedalAward
import io.gh.kdbrian.ttac.data.ThemeMode
import io.gh.kdbrian.ttac.game.Board
import io.gh.kdbrian.ttac.game.Difficulty
import io.gh.kdbrian.ttac.game.Mark
import io.gh.kdbrian.ttac.ui.components.Avatar
import io.gh.kdbrian.ttac.ui.components.BouncyButton
import io.gh.kdbrian.ttac.ui.components.CanvasSlider
import io.gh.kdbrian.ttac.ui.components.CanvasSwitch
import io.gh.kdbrian.ttac.ui.components.CelebrationOverlay
import io.gh.kdbrian.ttac.ui.components.IconBubble
import io.gh.kdbrian.ttac.ui.components.MedalTile
import io.gh.kdbrian.ttac.ui.components.Panel
import io.gh.kdbrian.ttac.ui.components.QrCode
import io.gh.kdbrian.ttac.ui.components.RoundAction
import io.gh.kdbrian.ttac.ui.components.Segmented
import io.gh.kdbrian.ttac.ui.components.StreakMeter
import io.gh.kdbrian.ttac.ui.components.Txt
import io.gh.kdbrian.ttac.ui.draw.AnimatedBackdrop
import io.gh.kdbrian.ttac.ui.draw.ArcadeArt
import io.gh.kdbrian.ttac.ui.draw.BoardCanvas
import io.gh.kdbrian.ttac.ui.draw.Glyph
import io.gh.kdbrian.ttac.ui.draw.GlyphIcon
import io.gh.kdbrian.ttac.ui.draw.MarkLook
import io.gh.kdbrian.ttac.ui.draw.MedalArt
import io.gh.kdbrian.ttac.ui.screens.AwardsScreen
import io.gh.kdbrian.ttac.ui.screens.BlocksScreen
import io.gh.kdbrian.ttac.ui.screens.GameScreen
import io.gh.kdbrian.ttac.ui.screens.HiveScreen
import io.gh.kdbrian.ttac.ui.screens.HomeScreen
import io.gh.kdbrian.ttac.ui.screens.LanScreen
import io.gh.kdbrian.ttac.ui.screens.ScoreboardScreen
import io.gh.kdbrian.ttac.ui.screens.SettingsScreen
import io.gh.kdbrian.ttac.ui.screens.SoloSetupScreen
import io.gh.kdbrian.ttac.ui.theme.LocalPalette
import io.gh.kdbrian.ttac.ui.theme.TTacTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val DIR = "../docs/docs/images"
private val X = Color(0xFFFF3D7F)
private val O = Color(0xFF33E1FF)

/**
 * Captures the guide's images from the app's real composables — screens, the board, the backdrop, medals and
 * components — at chosen moments on a paused clock. Nothing here draws its own pictures.
 *
 * Refresh with `./gradlew recordRoborazziDebug`; ordinary unit-test runs skip the capture.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class DocShots {

    @get:Rule
    val rule = createComposeRule()

    /** Renders [content] with the app theme on a paused clock, advances [atMs], and captures it. */
    private fun shot(name: String, w: Int, h: Int, atMs: Long, dark: Boolean = true, content: @Composable () -> Unit) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            TTacTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
                val p = LocalPalette.current
                Box(Modifier.size(w.dp, h.dp).background(Brush.verticalGradient(listOf(p.bgTop, p.bg)))) { content() }
            }
        }
        rule.mainClock.advanceTimeBy(atMs)
        rule.onRoot().captureRoboImage("$DIR/$name.png")
    }

    // ---- Whole screens over the live backdrop --------------------------------------------------

    private fun vm() = AppViewModel(ApplicationProvider.getApplicationContext())

    private fun screen(name: String, dark: Boolean = true, atMs: Long = 3500, model: AppViewModel = vm(), content: @Composable (AppViewModel) -> Unit) =
        shot(name, 380, 820, atMs, dark) {
            Box(Modifier.fillMaxSize()) {
                AnimatedBackdrop(X, O, Modifier.fillMaxSize())
                content(model)
            }
        }

    @Test fun screenHome() = screen("screen-home") { HomeScreen(it) }
    @Test fun screenHomeLight() = screen("screen-home-light", dark = false) { HomeScreen(it) }
    @Test fun screenSoloSetup() = screen("screen-solo-setup") { SoloSetupScreen(it) }
    @Test fun screenAwards() = screen("screen-awards") { AwardsScreen(it) }
    @Test fun screenScores() = screen("screen-scores") { ScoreboardScreen(it) }
    @Test fun screenSettings() = screen("screen-settings") { SettingsScreen(it) }
    @Test fun screenLan() = screen("screen-lan") { LanScreen(it) }
    @Test fun screenHive() = screen("screen-hive") { HiveScreen(it) }

    @Test fun screenGame() {
        val model = vm()
        model.startSolo(Difficulty.EASY, 3)
        model.match?.tap(4)
        screen("screen-game", model = model) { GameScreen(it) }
    }

    @Test fun screenBlocks() {
        // The demo drops straight into a live run, so gravity has pieces falling by capture time.
        val model = vm()
        model.startDemo(ArcadeGame.BLOCKS)
        screen("screen-blocks", atMs = 9000, model = model) { BlocksScreen(it) }
    }

    // ---- The real board, frozen at moments of its animations ------------------------------------

    private fun look(style: MarkStyle = MarkStyle.NEON) = MarkLook(X, O, style, 0.10f, 1f)

    private fun board(name: String, board: Board, atMs: Long, style: MarkStyle = MarkStyle.NEON, threats: Boolean = false, heat: FloatArray? = null) =
        shot(name, 340, 340, atMs) {
            BoardCanvas(
                board = board, roundKey = 1, win = board.winner(), look = look(style),
                threats = if (threats) mapOf(Mark.X to board.threats(Mark.X), Mark.O to board.threats(Mark.O)) else null,
                heatmap = heat, enabled = false, onCell = {}, modifier = Modifier.fillMaxSize().padding(12.dp),
            )
        }

    // Grid draw-in.
    @Test fun boardDrawIn150() = board("board-drawin-150", Board.empty(3), 150)
    @Test fun boardDrawIn350() = board("board-drawin-350", Board.empty(3), 350)
    @Test fun boardDrawIn900() = board("board-drawin-900", Board.decode("X.O.X...."), 900)

    // A mark drawing itself and popping (stroke tween + spring), frame by frame.
    private val single = Board.decode("X...O....")
    @Test fun markFrame80() = board("mark-frame-80", single, 80)
    @Test fun markFrame180() = board("mark-frame-180", single, 180)
    @Test fun markFrame300() = board("mark-frame-300", single, 300)
    @Test fun markFrame700() = board("mark-frame-700", single, 700)

    // The three mark styles, mid-stroke and settled.
    private val styled = Board.decode("XO..X..O.")
    @Test fun styleNeonMid() = board("style-neon-mid", Board.decode("XO......."), 200, MarkStyle.NEON)
    @Test fun styleNeon() = board("style-neon", styled, 1500, MarkStyle.NEON)
    @Test fun styleSolid() = board("style-solid", styled, 1500, MarkStyle.SOLID)
    @Test fun styleSketch() = board("style-sketch", styled, 1500, MarkStyle.SKETCH)

    // Win: ignite → burst → settled.
    private val won = Board.decode("XOO.X.O.X")
    @Test fun winIgnite() = board("win-ignite", won, 820)
    @Test fun winBurst() = board("win-burst", won, 1250)
    @Test fun winSettled() = board("win-settled", won, 2600)
    @Test fun win4x4() = board("win-4x4", Board.decode("XOO.OXO..OX....X"), 2600)

    // Heat.
    @Test fun threatHeat() = board("threat-heat", Board.decode("XX..O.O.."), 1500, threats = true)
    @Test fun heatmapField() = board(
        "heatmap-field", Board.empty(3), 1500,
        heat = floatArrayOf(0.8f, 0.2f, 0.6f, 0.3f, 1f, 0.25f, 0.55f, 0.15f, 0.7f),
    )

    // ---- The real backdrop over time: stars appear and fade ---------------------------------------

    @Test fun backdrop2s() = shot("backdrop-2s", 360, 640, 2000) { AnimatedBackdrop(X, O, Modifier.fillMaxSize()) }
    @Test fun backdrop4s() = shot("backdrop-4s", 360, 640, 4000) { AnimatedBackdrop(X, O, Modifier.fillMaxSize()) }
    @Test fun backdropTint() = shot("backdrop-tint", 360, 640, 4000) { AnimatedBackdrop(X, O, Modifier.fillMaxSize(), tint = X, tintSide = -1f) }
    @Test fun backdropLight() = shot("backdrop-light", 360, 640, 4000, dark = false) { AnimatedBackdrop(X, O, Modifier.fillMaxSize()) }

    // ---- Real medals, art and components ----------------------------------------------------------

    @Test fun medals() = shot("medals", 380, 170, 1200) {
        Column(Modifier.padding(8.dp)) {
            for (row in Medal.entries.chunked(5)) Row { row.forEach { MedalArt(it, true, Modifier.size(72.dp)) } }
        }
    }

    @Test fun medalsLocked() = shot("medals-locked", 380, 100, 800) {
        Row(Modifier.padding(8.dp)) { listOf(Medal.BRONZE, Medal.GOLD, Medal.DIAMOND, Medal.GRIT_I, Medal.COMEBACK).forEach { MedalArt(it, false, Modifier.size(72.dp)) } }
    }

    @Test fun medalTiles() = shot("medal-tiles", 380, 170, 800) {
        Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            MedalTile(Medal.GOLD, 2, Modifier.size(110.dp, 150.dp))
            MedalTile(Medal.GRIT_II, 1, Modifier.size(110.dp, 150.dp))
            MedalTile(Medal.DIAMOND, 0, Modifier.size(110.dp, 150.dp))
        }
    }

    @Test fun arcadeArt() = shot("arcade-art", 380, 150, 800) {
        Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            ArcadeArt(ArcadeGame.BLOCKS, false, Modifier.size(120.dp))
            ArcadeArt(ArcadeGame.HIVE, false, Modifier.size(120.dp))
            ArcadeArt(ArcadeGame.HIVE, true, Modifier.size(120.dp))
        }
    }

    @Test fun components() = shot("components", 380, 430, 1500) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BouncyButton("Start", {}, color = Color(0xFF9B6BFF), glyph = Glyph.PLAY)
                BouncyButton("Glass", {}, color = LocalPalette.current.surfaceHi, glyph = Glyph.GEAR)
                BouncyButton("Compact", {}, compact = true)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IconBubble(Glyph.TROPHY, {})
                IconBubble(Glyph.MEDAL, {}, active = true)
                Avatar("Ann", X)
                Avatar("Bo", O)
                CanvasSwitch(true, {})
                CanvasSwitch(false, {})
            }
            Segmented(listOf("Easy", "Medium", "Hard"), "Medium", { it }, {})
            CanvasSlider(0.6f, 0f..1f, {})
            Panel { Txt("A glass Panel: translucent fill, top sheen, hairline border.") }
            Row(horizontalArrangement = Arrangement.SpaceEvenly) {
                RoundAction(Glyph.PEOPLE, "Pass & Play", {}, X, size = 64.dp)
                RoundAction(Glyph.BOT, "Solo", {}, Color(0xFF9B6BFF), size = 84.dp, emphasized = true)
                RoundAction(Glyph.WIFI, "LAN", {}, O, size = 64.dp)
            }
        }
    }

    @Test fun glyphs() = shot("glyphs", 380, 160, 300) {
        Column(Modifier.padding(10.dp)) {
            for (row in Glyph.entries.chunked(10)) Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                row.forEach { GlyphIcon(it, Color.White, size = 26.dp) }
            }
        }
    }

    @Test fun streakMeters() = shot("streak-meters", 380, 200, 1500) {
        Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StreakMeter("Win streak", 4, 7, 6, Medal.SILVER, cold = false, modifier = Modifier.size(170.dp, 180.dp))
            StreakMeter("Losing streak", 2, 3, 3, Medal.GRIT_I, cold = true, modifier = Modifier.size(170.dp, 180.dp))
        }
    }

    @Test fun qrCode() = shot("qr", 220, 220, 300) {
        QrCode("ttac://join?c=0A580-5N31Z&a=Velvet%20Badger%2091", O, Modifier.fillMaxSize().padding(24.dp))
    }

    @Test fun medalCelebration() = shot("celebration", 380, 700, 1400) {
        Box(Modifier.fillMaxSize()) {
            AnimatedBackdrop(X, O, Modifier.fillMaxSize())
            CelebrationOverlay(Celebration.MedalWon(MedalAward(Medal.GOLD, "you", "You", 9, 0L)), {}, {})
        }
    }
}
