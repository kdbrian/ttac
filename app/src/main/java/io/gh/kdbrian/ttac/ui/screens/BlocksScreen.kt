package io.gh.kdbrian.ttac.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.gh.kdbrian.ttac.app.AppViewModel
import io.gh.kdbrian.ttac.data.ArcadeGame
import io.gh.kdbrian.ttac.data.Unlocks
import io.gh.kdbrian.ttac.fx.Sfx
import io.gh.kdbrian.ttac.game.BlocksDifficulty
import io.gh.kdbrian.ttac.game.BlocksGame
import io.gh.kdbrian.ttac.game.Tetromino
import io.gh.kdbrian.ttac.ui.components.BouncyButton
import io.gh.kdbrian.ttac.ui.components.IconBubble
import io.gh.kdbrian.ttac.ui.components.Panel
import io.gh.kdbrian.ttac.ui.components.RollingNumber
import io.gh.kdbrian.ttac.ui.components.SectionLabel
import io.gh.kdbrian.ttac.ui.components.Segmented
import io.gh.kdbrian.ttac.ui.components.TopBar
import io.gh.kdbrian.ttac.ui.components.Txt
import io.gh.kdbrian.ttac.ui.components.drawGlass
import io.gh.kdbrian.ttac.ui.components.onColor
import io.gh.kdbrian.ttac.ui.components.popIn
import io.gh.kdbrian.ttac.ui.draw.ArcadeArt
import io.gh.kdbrian.ttac.ui.draw.BlockColors
import io.gh.kdbrian.ttac.ui.draw.Glyph
import io.gh.kdbrian.ttac.ui.draw.drawBlock
import io.gh.kdbrian.ttac.ui.theme.LocalPalette
import io.gh.kdbrian.ttac.ui.theme.Type
import io.gh.kdbrian.ttac.ui.theme.heatAt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.random.Random

@Composable
fun BlocksScreen(vm: AppViewModel) {
    var difficulty by rememberSaveable { mutableStateOf(BlocksDifficulty.MEDIUM) }
    // A demo skips the lobby and gives exactly one medium run.
    var game by remember { mutableStateOf(if (vm.demoMode) BlocksGame(BlocksDifficulty.MEDIUM) else null) }
    val g = game
    if (g == null) {
        BlocksLobby(vm, difficulty, { difficulty = it }) { game = BlocksGame(difficulty, Random.Default) }
    } else {
        BlocksPlay(vm, g, onRestart = { game = BlocksGame(g.difficulty) }, onLobby = { game = null; vm.arcadeHeat = 0f })
    }
}

@Composable
private fun BlocksLobby(vm: AppViewModel, difficulty: BlocksDifficulty, onDifficulty: (BlocksDifficulty) -> Unit, onStart: () -> Unit) {
    val palette = LocalPalette.current
    val stats by vm.stats.collectAsStateWithLifecycle()
    val b = stats.blocks
    ScreenColumn {
        TopBar("Blocks", { vm.back() })
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            ArcadeArt(ArcadeGame.BLOCKS, locked = false, modifier = Modifier.size(150.dp).popIn())
        }
        Txt("Endless: keep stacking until the well tops out. It only gets faster.", Type.body, palette.textDim, Modifier.fillMaxWidth(), TextAlign.Center)
        SectionLabel("Difficulty")
        Segmented(BlocksDifficulty.entries, difficulty, { it.label }, { onDifficulty(it); vm.tapSound() })
        Txt(
            "${difficulty.width}×${difficulty.height} well · ${if (difficulty == BlocksDifficulty.HARD) "fast" else if (difficulty == BlocksDifficulty.MEDIUM) "brisk" else "gentle"} start · pass ${difficulty.goalLines} lines for a win",
            Type.body, palette.textDim, Modifier.padding(top = 8.dp, start = 4.dp),
        )
        SectionLabel("Your blocks")
        Panel(Modifier.fillMaxWidth().popIn(80)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("BEST SCORE", b.bestScore.toString(), palette.accent, Modifier.weight(1f))
                StatTile("WINS", b.wins.toString(), palette.good, Modifier.weight(1f))
                StatTile("STREAK", b.streak.toString(), palette.accent2, Modifier.weight(1f))
            }
            if (!stats.isUnlocked(ArcadeGame.HIVE)) {
                Spacer(Modifier.height(12.dp))
                Txt("Win ${Unlocks.HIVE_STREAK} runs in a row to unlock Word Hive · ${b.streak}/${Unlocks.HIVE_STREAK}", Type.body.copy(fontWeight = FontWeight.Bold), palette.accent)
            }
        }
        Gap(24)
        BouncyButton("Start", onStart, Modifier.fillMaxWidth().popIn(140), palette.accent2, Glyph.PLAY)
        Gap(24)
    }
}

@Composable
private fun BlocksPlay(vm: AppViewModel, g: BlocksGame, onRestart: () -> Unit, onLobby: () -> Unit) {
    val palette = LocalPalette.current
    val stats by vm.stats.collectAsStateWithLifecycle()
    var frame by remember(g) { mutableIntStateOf(0) }
    var paused by remember(g) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val flash = remember(g) { Animatable(0f) }
    val thump = remember(g) { Animatable(0f) }
    var flashRows by remember(g) { mutableStateOf(emptyList<Int>()) }
    var seenClears by remember(g) { mutableIntStateOf(0) }
    var recorded by remember(g) { mutableStateOf(false) }
    var milestoneShown by remember(g) { mutableStateOf(false) }
    var toast by remember(g) { mutableStateOf<String?>(null) }

    fun refresh() {
        frame++
        if (g.clearCount != seenClears) {
            seenClears = g.clearCount
            flashRows = g.lastCleared
            vm.play(if (g.lastCleared.size >= 4) Sfx.QUAD else Sfx.CLEAR)
            scope.launch { flash.snapTo(1f); flash.animateTo(0f, tween(520)) }
        }
        // Passing the milestone is celebrated, then the run carries on.
        if (g.won && !milestoneShown) {
            milestoneShown = true
            vm.play(Sfx.WIN)
            toast = "Goal cleared! Keep going"
        }
        vm.arcadeHeat = (0.15f + (g.level - 1) / 6f).coerceIn(0f, 1f)
    }

    fun act(block: () -> Unit) {
        if (g.over || paused) return
        block()
        refresh()
    }

    // Gravity.
    LaunchedEffect(g, paused) {
        while (!g.over && !paused) {
            delay(g.gravityMs)
            g.tick()
            refresh()
        }
    }
    // Record the run once it ends.
    LaunchedEffect(g.over, frame) {
        if (g.over && !recorded) {
            recorded = true
            vm.play(Sfx.LOSE)
            if (!vm.demoMode) vm.statsRepo.recordBlocks(g.won, g.score, g.lines)
        }
    }
    LaunchedEffect(toast) { if (toast != null) { delay(1800); toast = null } }

    // The active piece glides between cells; it snaps whenever a new piece spawns.
    val vx = remember(g) { Animatable(g.px.toFloat()) }
    val vy = remember(g) { Animatable(g.py.toFloat()) }
    var seenLocks by remember(g) { mutableIntStateOf(g.lockCount) }
    LaunchedEffect(frame) {
        if (g.lockCount != seenLocks) {
            seenLocks = g.lockCount
            vx.snapTo(g.px.toFloat())
            vy.snapTo(g.py.toFloat())
        } else {
            launch { vx.animateTo(g.px.toFloat(), spring(dampingRatio = 0.85f, stiffness = 1600f)) }
            launch { vy.animateTo(g.py.toFloat(), tween(minOf(110, (g.gravityMs * 0.6f).toInt()))) }
        }
    }

    // Full-bleed play area: the well rests directly on the animated background, edge to edge.
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        // One compact HUD row instead of a title bar plus a stats strip.
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val big = Type.body.copy(fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
            IconBubble(Glyph.BACK, { vm.back() }, size = 40.dp, label = "Back")
            MiniStat("SCORE", Modifier.weight(1.3f)) { RollingNumber(g.score, big, palette.text) }
            MiniStat("LINES", Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    RollingNumber(g.lines, big, if (g.won) palette.good else palette.text)
                    if (!g.won) Txt("/${g.difficulty.goalLines}", Type.label, palette.textDim)
                }
            }
            MiniStat(if (vm.demoMode) "DEMO" else "LVL", Modifier.weight(0.8f)) {
                RollingNumber(g.level, big, palette.heatAt((g.level / 7f).coerceIn(0.3f, 1f)))
            }
            NextPiece(g.next, Modifier.size(44.dp))
            IconBubble(if (paused) Glyph.PLAY else Glyph.PAUSE, { paused = !paused; vm.tapSound() }, size = 40.dp, label = if (paused) "Resume" else "Pause")
        }

        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
            val cellW = maxWidth / g.width
            val cellH = maxHeight / g.height
            val cell = if (cellW < cellH) cellW else cellH
            Box(
                Modifier
                    .size(cell * g.width, cell * g.height)
                    .graphicsLayer { translationY = thump.value * 6.dp.toPx() }
                    .pointerInput(g) {
                        val cellPx = cell.toPx()
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            val tracker = VelocityTracker()
                            tracker.addPosition(down.uptimeMillis, down.position)
                            var accX = 0f
                            var accY = 0f
                            var moved = false
                            var totalY = 0f
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                val d = change.positionChange()
                                tracker.addPosition(change.uptimeMillis, change.position)
                                accX += d.x
                                accY += d.y
                                totalY += d.y
                                while (abs(accX) >= cellPx * 0.9f) {
                                    val dir = if (accX > 0) 1 else -1
                                    act { if (g.move(dir)) vm.play(Sfx.MOVE) }
                                    accX -= dir * cellPx * 0.9f
                                    moved = true
                                }
                                while (accY >= cellPx) {
                                    act { g.softDrop() }
                                    accY -= cellPx
                                    moved = true
                                }
                                change.consume()
                            }
                            val v = tracker.calculateVelocity()
                            when {
                                v.y > 2600f && totalY > cellPx -> act {
                                    if (g.hardDrop() > 0) vm.play(Sfx.DROP)
                                    scope.launch { thump.snapTo(1f); thump.animateTo(0f, spring(dampingRatio = 0.3f, stiffness = 600f)) }
                                }
                                !moved -> act { if (g.rotate()) vm.play(Sfx.ROTATE) }
                            }
                        }
                    }
            ) {
                BlocksBoard(g, frame, flashRows, flash.value, vx.value, vy.value, Modifier.fillMaxSize())
            }
            // Milestone toast floats over the well without stopping play.
            androidx.compose.animation.AnimatedVisibility(toast != null, Modifier.align(Alignment.TopCenter).padding(top = 24.dp), enter = fadeIn() + scaleIn(spring(dampingRatio = 0.4f)), exit = fadeOut()) {
                Txt(
                    toast ?: "", Type.heading, onColor(palette.good),
                    Modifier
                        .drawBehind { drawRoundRect(palette.good, cornerRadius = CornerRadius(size.height / 2)) }
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                )
            }
        }

        // Slim control strip for anyone who prefers buttons to gestures.
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            IconBubble(Glyph.LEFT, { act { if (g.move(-1)) vm.play(Sfx.MOVE) } }, size = 46.dp, label = "Move left")
            IconBubble(Glyph.REFRESH, { act { if (g.rotate()) vm.play(Sfx.ROTATE) } }, size = 46.dp, label = "Rotate")
            IconBubble(Glyph.DOWN, { act { g.softDrop() } }, size = 46.dp, label = "Soft drop")
            IconBubble(Glyph.DROP, {
                act { if (g.hardDrop() > 0) vm.play(Sfx.DROP) }
                scope.launch { thump.snapTo(1f); thump.animateTo(0f, spring(dampingRatio = 0.3f, stiffness = 600f)) }
            }, size = 46.dp, label = "Hard drop")
            IconBubble(Glyph.RIGHT, { act { if (g.move(1)) vm.play(Sfx.MOVE) } }, size = 46.dp, label = "Move right")
        }
    }

    // Pause / run-over overlays.
    AnimatedVisibility(paused || g.over, enter = fadeIn() + scaleIn(spring(dampingRatio = 0.5f), initialScale = 0.8f), exit = fadeOut()) {
        Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
            Panel(Modifier.fillMaxWidth(), color = palette.bg.copy(alpha = 0.9f)) {
                val hiveLocked = !stats.isUnlocked(ArcadeGame.HIVE)
                Txt(
                    when {
                        !g.over -> "Paused"
                        g.won -> "Great run!"
                        else -> "Topped out"
                    },
                    Type.display.copy(fontSize = 34.sp), if (g.over && g.won) palette.good else palette.text, Modifier.fillMaxWidth(), TextAlign.Center,
                )
                Txt("${g.score} points · ${g.lines} lines · level ${g.level}", Type.body, palette.textDim, Modifier.fillMaxWidth(), TextAlign.Center)
                if (g.over && vm.demoMode) {
                    Spacer(Modifier.height(8.dp))
                    Txt("That was your free try. Win ${Unlocks.BLOCKS_STREAK} Tic-Tac-Toe games in a row to unlock Blocks for good.", Type.body.copy(fontWeight = FontWeight.Bold), palette.accent, Modifier.fillMaxWidth(), TextAlign.Center)
                } else if (g.over && hiveLocked) {
                    Spacer(Modifier.height(8.dp))
                    Txt(
                        (if (g.won) "Counted as a win! " else "Pass ${g.difficulty.goalLines} lines to count a win. ") + "Streak ${stats.blocks.streak}/${Unlocks.HIVE_STREAK} toward Word Hive",
                        Type.body.copy(fontWeight = FontWeight.Bold), palette.accent, Modifier.fillMaxWidth(), TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(16.dp))
                if (!g.over) {
                    BouncyButton("Resume", { paused = false }, Modifier.fillMaxWidth(), palette.accent2, Glyph.PLAY)
                } else if (vm.demoMode) {
                    BouncyButton("Back to menu", { vm.back() }, Modifier.fillMaxWidth(), palette.accent2, Glyph.BACK)
                } else {
                    BouncyButton("Play again", { onRestart(); vm.tapSound() }, Modifier.fillMaxWidth(), palette.accent2, Glyph.REFRESH)
                }
                if (!vm.demoMode) {
                    Spacer(Modifier.height(10.dp))
                    BouncyButton("Change difficulty", { onLobby(); vm.tapSound() }, Modifier.fillMaxWidth(), palette.surfaceHi, compact = true)
                }
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, modifier: Modifier, value: @Composable () -> Unit) {
    val palette = LocalPalette.current
    Column(
        modifier
            .drawBehind { drawGlass(palette, 14.dp.toPx()) }
            .padding(horizontal = 9.dp, vertical = 4.dp),
    ) {
        Txt(label, Type.label.copy(fontSize = 9.sp), palette.textDim)
        value()
    }
}

@Composable
private fun NextPiece(type: Tetromino, modifier: Modifier) {
    val palette = LocalPalette.current
    Canvas(modifier) {
        drawGlass(palette, 14.dp.toPx())
        val cells = type.cells(0)
        val minX = cells.minOf { it.first }; val maxX = cells.maxOf { it.first }
        val minY = cells.minOf { it.second }; val maxY = cells.maxOf { it.second }
        val cell = size.minDimension / 5f
        val ox = (size.width - (maxX - minX + 1) * cell) / 2 - minX * cell
        val oy = (size.height - (maxY - minY + 1) * cell) / 2 - minY * cell
        for ((x, y) in cells) drawBlock(Offset(ox + x * cell, oy + y * cell), cell, BlockColors[type.ordinal])
    }
}

@Composable
private fun BlocksBoard(g: BlocksGame, frame: Int, flashRows: List<Int>, flash: Float, pieceX: Float, pieceY: Float, modifier: Modifier) {
    val palette = LocalPalette.current
    val danger by rememberInfiniteTransition(label = "danger").animateFloat(0f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "d")
    Canvas(modifier) {
        frame.let { } // captured so each game step produces a fresh draw
        val cell = size.width / g.width
        // No window: soft lanes painted straight onto the backdrop.
        for (x in 0 until g.width step 2) {
            drawRect(
                Brush.verticalGradient(listOf(Color.Transparent, palette.text.copy(alpha = 0.035f), palette.text.copy(alpha = 0.06f))),
                Offset(x * cell, 0f), Size(cell, size.height),
            )
        }
        // Floor: a glowing line that heats up toward the milestone, then stays hot.
        val goal = (g.lines.toFloat() / g.difficulty.goalLines).coerceIn(0f, 1f)
        val floor = 3.dp.toPx()
        drawRect(palette.text.copy(alpha = 0.12f), Offset(0f, size.height - floor), Size(size.width, floor))
        if (goal > 0f) {
            val w = size.width * goal
            drawRect(palette.heatAt(0.5f + 0.5f * goal).copy(alpha = 0.35f), Offset(0f, size.height - floor * 3), Size(w, floor * 3))
            drawRect(Brush.horizontalGradient(listOf(palette.heatAt(0.35f), palette.heatAt(0.5f + 0.5f * goal)), 0f, w), Offset(0f, size.height - floor), Size(w, floor))
        }
        // Danger glow when the stack nears the top.
        val highest = (0 until g.height).firstOrNull { y -> (0 until g.width).any { x -> g[x, y] != 0 } } ?: g.height
        if (highest < 5) {
            val a = (1f - highest / 5f) * (0.35f + 0.25f * danger)
            drawRect(Brush.verticalGradient(listOf(palette.bad.copy(alpha = a), Color.Transparent), 0f, cell * 6))
        }
        // Settled stack.
        for (y in 0 until g.height) for (x in 0 until g.width) {
            val v = g[x, y]
            if (v != 0) drawBlock(Offset(x * cell, y * cell), cell, BlockColors[v - 1], glow = false)
        }
        if (!g.over) {
            // Ghost.
            for ((x, y) in g.cellsOf(y = g.ghostY())) {
                if (y < 0) continue
                val inset = cell * 0.12f
                drawRoundRect(BlockColors[g.piece.ordinal].copy(alpha = 0.5f), Offset(x * cell + inset, y * cell + inset), Size(cell - inset * 2, cell - inset * 2), CornerRadius(cell * 0.2f), style = Stroke(cell * 0.06f))
            }
            // Active piece, drawn at its gliding position.
            for ((cx, cy) in g.piece.cells(g.rot)) {
                val y = pieceY + cy
                if (y > -1f) drawBlock(Offset((pieceX + cx) * cell, y * cell), cell, BlockColors[g.piece.ordinal])
            }
        }
        // Line-clear flash: a bright band with sparks where rows vanished.
        if (flash > 0.01f) {
            for (row in flashRows) {
                val y = row * cell
                drawRect(Color.White.copy(alpha = 0.7f * flash), Offset(0f, y), Size(size.width, cell))
                for (k in 0 until g.width) {
                    val sx = (k + 0.5f) * cell + (1f - flash) * cell * (if (k % 2 == 0) -1.5f else 1.5f)
                    drawCircle(palette.heatAt(0.6f + 0.4f * (k % 3) / 2f).copy(alpha = flash), cell * 0.12f * flash + 1f, Offset(sx, y + cell / 2 - (1f - flash) * cell))
                }
            }
        }
    }
}
