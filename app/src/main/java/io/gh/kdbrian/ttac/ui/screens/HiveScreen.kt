package io.gh.kdbrian.ttac.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.gh.kdbrian.ttac.app.AppViewModel
import io.gh.kdbrian.ttac.fx.Sfx
import io.gh.kdbrian.ttac.game.Hex
import io.gh.kdbrian.ttac.game.HivePuzzle
import io.gh.kdbrian.ttac.game.HiveSpec
import io.gh.kdbrian.ttac.game.HiveVerdict
import io.gh.kdbrian.ttac.game.hiveHintCost
import io.gh.kdbrian.ttac.game.hiveScore
import io.gh.kdbrian.ttac.ui.components.BouncyButton
import io.gh.kdbrian.ttac.ui.components.IconBubble
import io.gh.kdbrian.ttac.ui.components.Panel
import io.gh.kdbrian.ttac.ui.components.RollingNumber
import io.gh.kdbrian.ttac.ui.components.TopBar
import io.gh.kdbrian.ttac.ui.components.Txt
import io.gh.kdbrian.ttac.ui.components.drawGlass
import io.gh.kdbrian.ttac.ui.components.onColor
import io.gh.kdbrian.ttac.ui.components.popIn
import io.gh.kdbrian.ttac.ui.draw.Glyph
import io.gh.kdbrian.ttac.ui.draw.HoneyDark
import io.gh.kdbrian.ttac.ui.draw.HoneyLight
import io.gh.kdbrian.ttac.ui.draw.hexPath
import io.gh.kdbrian.ttac.ui.theme.LocalPalette
import io.gh.kdbrian.ttac.ui.theme.Type
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private val WordColors = listOf(
    Color(0xFFFF3D7F), Color(0xFF33E1FF), Color(0xFF3DFFA8), Color(0xFF9B6BFF), Color(0xFFFF8A1F), Color(0xFF4D7CFF),
    Color(0xFFFF5A36), Color(0xFF00C2A8), Color(0xFFE040FB), Color(0xFF7CB342), Color(0xFFFFB300),
)

private val SQRT3 = sqrt(3f)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HiveScreen(vm: AppViewModel) {
    val palette = LocalPalette.current
    val stats by vm.stats.collectAsStateWithLifecycle()
    val demo = vm.demoMode
    // A demo is always a fresh level-one hive with throwaway points.
    val level = if (demo) 1 else stats.hive.level
    var puzzle by remember(level, demo) { mutableStateOf(HivePuzzle.generate(HiveSpec(level))) }
    var found by remember(level, demo) { mutableStateOf(setOf<String>()) }
    var extras by remember(level, demo) { mutableStateOf(setOf<String>()) }
    val foundPaths = remember(level, demo) { mutableStateMapOf<String, List<Hex>>() }
    val hints = remember(level, demo) { mutableStateMapOf<String, Int>() }
    var selection by remember(level, demo) { mutableStateOf(emptyList<Hex>()) }
    val foundAnim = remember(level, demo) { mutableStateMapOf<String, Animatable<Float, *>>() }
    var demoPoints by remember(level, demo) { mutableStateOf(0) }
    val points = if (demo) demoPoints else stats.hive.points
    val shake = remember { Animatable(0f) }
    val spin = remember { Animatable(0f) }
    val reveal = remember(puzzle) { Animatable(0f) }
    var message by remember { mutableStateOf<Pair<String, Color>?>(null) }
    val scope = rememberCoroutineScope()
    val cleared = puzzle.words.isNotEmpty() && puzzle.words.all { it.word in found }
    var showCleared by remember(level, demo) { mutableStateOf(false) }

    LaunchedEffect(puzzle) { reveal.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
    LaunchedEffect(found.size) { vm.arcadeHeat = 0.12f + 0.55f * found.size / puzzle.words.size.coerceAtLeast(1) }
    LaunchedEffect(cleared) {
        if (cleared) {
            delay(700)
            vm.play(Sfx.WIN)
            showCleared = true
        }
    }
    LaunchedEffect(message) { if (message != null) { delay(1900); message = null } }

    fun addPoints(n: Int) {
        if (demo) demoPoints = (demoPoints + n).coerceAtLeast(0) else vm.statsRepo.recordHive(n, if (n > 0) 1 else 0, levelCleared = false)
    }

    fun submit() {
        val path = selection
        selection = emptyList()
        if (path.isEmpty()) return
        when (val v = puzzle.evaluate(path, found, extras)) {
            is HiveVerdict.Target -> {
                val w = v.word.word
                val score = hiveScore(w, level, extra = false, hinted = (hints[w] ?: 0) > 0)
                found = found + w
                foundPaths[w] = path
                addPoints(score.total)
                vm.play(Sfx.FOUND)
                message = (if (score.tier != null) "${score.tier}! +${score.base} +${score.bonus} bonus" else "+${score.total}  $w") to HoneyLight
                val anim = Animatable(0f)
                foundAnim[w] = anim
                scope.launch { anim.animateTo(1f, tween(700)) }
            }
            is HiveVerdict.Extra -> {
                val score = hiveScore(v.word, level, extra = true, hinted = false)
                extras = extras + v.word
                addPoints(score.total)
                vm.play(Sfx.HINT)
                message = (if (score.tier != null) "Extra ${v.word} · ${score.tier}! +${score.total}" else "Extra word ${v.word} +${score.total}") to palette.accent2
            }
            is HiveVerdict.Repeat -> {
                vm.play(Sfx.WRONG)
                message = "Already found ${v.word}" to palette.textDim
            }
            is HiveVerdict.Rejected -> {
                if (path.size < 2) return
                vm.play(Sfx.WRONG)
                message = (if (v.word.length < HivePuzzle.MIN_WORD) "Words need ${HivePuzzle.MIN_WORD}+ letters" else "${v.word} isn't a word") to palette.bad
                scope.launch {
                    shake.animateTo(0f, keyframes {
                        durationMillis = 380
                        -10f at 50; 9f at 110; -6f at 180; 4f at 250; 0f at 380
                    })
                }
            }
        }
    }

    fun hint() {
        val cost = hiveHintCost(level)
        val target = puzzle.words.filter { it.word !in found }.minByOrNull { hints[it.word] ?: 0 } ?: return
        if ((hints[target.word] ?: 0) >= 2) { message = "Every word is already hinted" to palette.textDim; return }
        if (points < cost) {
            vm.play(Sfx.WRONG)
            message = "Hints cost $cost points" to palette.bad
            return
        }
        addPoints(-cost)
        hints[target.word] = (hints[target.word] ?: 0) + 1
        vm.play(Sfx.HINT)
    }

    fun shuffle() {
        vm.play(Sfx.ROTATE)
        scope.launch {
            spin.snapTo(0f)
            launch { spin.animateTo(1f, tween(650, easing = FastOutSlowInEasing)) }
            delay(300)
            // Swap letters at the midpoint of the spin; found words keep their place in the list.
            puzzle = puzzle.shuffled(found)
            foundPaths.clear()
            selection = emptyList()
        }
    }

    ScreenColumn(scroll = false) {
        TopBar(if (demo) "Word Hive · Demo" else "Word Hive · Level $level", { vm.back() })
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // Points: a honey coin with a rolling total.
            Row(
                Modifier
                    .drawBehind { drawGlass(palette, size.height / 2) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Canvas(Modifier.size(20.dp)) {
                    drawPath(hexPath(center, size.minDimension / 2), Brush.verticalGradient(listOf(HoneyLight, HoneyDark)))
                }
                Spacer(Modifier.width(8.dp))
                RollingNumber(points, Type.heading, palette.text)
            }
            Spacer(Modifier.width(8.dp))
            if (extras.isNotEmpty()) {
                Txt(
                    "+${extras.size} extra", Type.label.copy(letterSpacing = 0.sp), Color.White,
                    Modifier.drawBehind { drawRoundRect(palette.accent2, cornerRadius = CornerRadius(size.height / 2)) }.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            IconBubble(Glyph.REFRESH, { shuffle() }, tint = HoneyLight, label = "Shuffle hive")
            Spacer(Modifier.width(10.dp))
            Box {
                IconBubble(Glyph.BULB, { hint() }, tint = HoneyLight, label = "Hint")
                Txt(
                    "-${hiveHintCost(level)}", Type.label.copy(fontSize = 9.sp), Color.White,
                    Modifier
                        .align(Alignment.BottomCenter)
                        .graphicsLayer { translationY = 8.dp.toPx() }
                        .drawBehind { drawRoundRect(HoneyDark, cornerRadius = CornerRadius(size.height / 2)) }
                        .padding(horizontal = 5.dp),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        // Target words as letter boxes: empty until found; hints fill in first (and last) letters.
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            puzzle.words.forEachIndexed { i, w ->
                WordSlots(
                    word = w.word,
                    found = w.word in found,
                    hintLevel = hints[w.word] ?: 0,
                    color = WordColors[i % WordColors.size],
                    modifier = Modifier.popIn(i * 50),
                )
            }
        }

        // Live preview of the word being traced.
        val spelling = puzzle.spell(selection)
        Box(Modifier.fillMaxWidth().height(44.dp), contentAlignment = Alignment.Center) {
            val msg = message
            when {
                spelling.isNotEmpty() -> Txt(
                    spelling, Type.heading.copy(letterSpacing = 3.sp), Color(0xFF1A0B3D),
                    Modifier
                        .drawBehind { drawRoundRect(Brush.verticalGradient(listOf(Color.White, HoneyLight)), cornerRadius = CornerRadius(size.height / 2)) }
                        .padding(horizontal = 18.dp, vertical = 6.dp),
                )
                msg != null -> Txt(
                    msg.first, Type.body.copy(fontWeight = FontWeight.ExtraBold), onColor(msg.second),
                    Modifier
                        .popIn()
                        .drawBehind { drawRoundRect(msg.second, cornerRadius = CornerRadius(size.height / 2)) }
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
        }

        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val radius = puzzle.spec.radius
            val wPx = constraints.maxWidth.toFloat()
            val hPx = constraints.maxHeight.toFloat()
            val s = minOf(wPx / (SQRT3 * (2 * radius + 1)), hPx / (3f * radius + 2f)) * 0.98f
            HiveBoard(
                puzzle = puzzle,
                hexSize = s,
                foundPaths = foundPaths,
                wordIndex = puzzle.words.withIndex().associate { (i, w) -> w.word to i },
                hints = hints,
                selection = selection,
                foundAnim = foundAnim,
                reveal = reveal.value,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = shake.value.dp.toPx()
                        rotationZ = spin.value * 360f
                        val squash = 1f - 0.25f * sin(PI.toFloat() * spin.value)
                        scaleX = squash; scaleY = squash
                    }
                    .pointerInput(puzzle) {
                        val center = Offset(size.width / 2f, size.height / 2f)
                        fun pos(h: Hex) = center + Offset(SQRT3 * s * (h.q + h.r / 2f), 1.5f * s * h.r)
                        // Only count a cell once the finger is well inside it, so diagonal drags don't clip neighbours.
                        fun hexAt(p: Offset, reach: Float): Hex? = puzzle.cells.minByOrNull { (pos(it) - p).getDistance() }?.takeIf { (pos(it) - p).getDistance() < s * reach }
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            val start = hexAt(down.position, 1f) ?: return@awaitEachGesture
                            selection = listOf(start)
                            vm.play(Sfx.MOVE)
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                val h = hexAt(change.position, 0.72f)
                                val path = selection
                                if (h != null && path.isNotEmpty() && h != path.last()) {
                                    when {
                                        // Slide back onto the previous cell to undo a step.
                                        path.size >= 2 && h == path[path.size - 2] -> selection = path.dropLast(1)
                                        h !in path && HivePuzzle.adjacent(h, path.last()) -> {
                                            selection = path + h
                                            vm.play(Sfx.MOVE)
                                        }
                                    }
                                }
                                change.consume()
                            }
                            submit()
                        }
                    },
            )
        }
        Txt("Trace neighbouring letters. Targets fill the list, real words score as extras.", Type.label.copy(letterSpacing = 0.sp), palette.textDim, Modifier.fillMaxWidth().padding(vertical = 10.dp), TextAlign.Center)
    }

    AnimatedVisibility(showCleared, enter = fadeIn() + scaleIn(spring(dampingRatio = 0.5f), initialScale = 0.8f), exit = fadeOut()) {
        Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
            Panel(Modifier.fillMaxWidth(), color = palette.bg.copy(alpha = 0.92f)) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    io.gh.kdbrian.ttac.ui.draw.ArcadeArt(io.gh.kdbrian.ttac.data.ArcadeGame.HIVE, locked = false, modifier = Modifier.size(110.dp))
                }
                Txt(if (demo) "Demo complete!" else "Hive cleared!", Type.display.copy(fontSize = 34.sp), HoneyLight, Modifier.fillMaxWidth(), TextAlign.Center)
                Txt("${puzzle.words.size} words · ${extras.size} extras · $points points", Type.body, palette.textDim, Modifier.fillMaxWidth(), TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                if (demo) {
                    Txt(
                        "Unlock Word Hive with ${io.gh.kdbrian.ttac.data.Unlocks.HIVE_STREAK} Blocks wins in a row to keep climbing levels.",
                        Type.body.copy(fontWeight = FontWeight.Bold), palette.text, Modifier.fillMaxWidth(), TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    BouncyButton("Back to menu", { vm.back() }, Modifier.fillMaxWidth(), HoneyDark, Glyph.BACK)
                } else {
                    val next = HiveSpec(level + 1)
                    Txt(
                        if (next.radius > puzzle.spec.radius) "Next up: a bigger hive" else "Next up: ${next.wordCount} words, up to ${next.maxLength} letters",
                        Type.body.copy(fontWeight = FontWeight.Bold), palette.text, Modifier.fillMaxWidth(), TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    BouncyButton("Next level", {
                        showCleared = false
                        vm.statsRepo.recordHive(25 * level, 0, levelCleared = true)
                        vm.tapSound()
                    }, Modifier.fillMaxWidth(), HoneyDark, Glyph.PLAY)
                }
            }
        }
    }
}

@Composable
private fun HiveBoard(
    puzzle: HivePuzzle,
    hexSize: Float,
    foundPaths: Map<String, List<Hex>>,
    wordIndex: Map<String, Int>,
    hints: Map<String, Int>,
    selection: List<Hex>,
    foundAnim: Map<String, Animatable<Float, *>>,
    reveal: Float,
    modifier: Modifier,
) {
    val palette = LocalPalette.current
    val measurer = rememberTextMeasurer()
    val pulse by rememberInfiniteTransition(label = "hint").animateFloat(0f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "p")
    val drift by rememberInfiniteTransition(label = "comb").animateFloat(0f, 1f, infiniteRepeatable(tween(20_000, easing = LinearEasing)), label = "d")
    val cellWord = HashMap<Hex, String>().apply {
        for ((w, cells) in foundPaths) for (c in cells) put(c, w)
    }
    val selected = selection.toSet()

    Canvas(modifier) {
        val s = hexSize
        val c = center
        fun pos(h: Hex) = c + Offset(SQRT3 * s * (h.q + h.r / 2f), 1.5f * s * h.r)

        // Drifting honeycomb lattice behind the hive.
        val bgR = s * 0.9f
        val stepX = SQRT3 * bgR
        val stepY = 1.5f * bgR
        val offY = drift * stepY * 2
        var row = -2
        while (row * stepY < size.height + stepY * 2) {
            var col = -1
            while (col * stepX < size.width + stepX) {
                val p = Offset(col * stepX + if (row % 2 == 0) 0f else stepX / 2, row * stepY + offY)
                drawPath(hexPath(p, bgR * 0.96f), HoneyLight.copy(alpha = 0.05f), style = Stroke(1.5f))
                col++
            }
            row++
        }

        val maxDist = puzzle.spec.radius.coerceAtLeast(1)
        for (h in puzzle.cells) {
            val p = pos(h)
            val d = h.distanceTo(Hex(0, 0))
            val pop = ((reveal * 1.6f) - d / maxDist.toFloat() * 0.6f).coerceIn(0f, 1f)
            if (pop <= 0f) continue
            val word = cellWord[h]
            val anim = word?.let { foundAnim[it]?.value } ?: 1f
            val isSel = h in selected
            val bounce = if (word != null) 1f + 0.18f * sin(PI.toFloat() * anim) else 1f
            val scaleBy = pop * bounce * (if (isSel) 1.08f else 1f)
            scale(scaleBy, p) {
                val path = hexPath(p, s * 0.93f)
                val fill = when {
                    isSel -> Brush.verticalGradient(listOf(Color.White, lerp(HoneyLight, Color.White, 0.3f)), p.y - s, p.y + s)
                    word != null -> {
                        val wc = WordColors[(wordIndex[word] ?: 0) % WordColors.size]
                        Brush.verticalGradient(listOf(lerp(wc, Color.White, 0.25f), wc), p.y - s, p.y + s)
                    }
                    palette.isDark -> Brush.verticalGradient(listOf(HoneyLight.copy(alpha = 0.32f), HoneyDark.copy(alpha = 0.22f)), p.y - s, p.y + s)
                    else -> Brush.verticalGradient(listOf(lerp(HoneyLight, Color.White, 0.35f), HoneyLight), p.y - s, p.y + s)
                }
                if (isSel) drawCircle(HoneyLight.copy(alpha = 0.45f), s * 1.05f, p)
                drawPath(path, fill)
                drawPath(path, if (palette.isDark) HoneyLight.copy(alpha = 0.55f) else HoneyDark.copy(alpha = 0.6f), style = Stroke(s * 0.06f))
                val letter = puzzle.letters[h].toString()
                val ink = when {
                    isSel -> Color(0xFF1A0B3D)
                    word != null -> Color.White
                    palette.isDark -> Color.White
                    else -> Color(0xFF3A2400)
                }
                val layout = measurer.measure(letter, TextStyle(color = ink, fontWeight = FontWeight.Black, fontSize = (s * 0.75f).toSp()))
                drawText(layout, topLeft = p - Offset(layout.size.width / 2f, layout.size.height / 2f))
            }
        }

        // Hints: a pulsing ring on the first letter, or the whole path at level two.
        for (w in puzzle.words) {
            val h = hints[w.word] ?: 0
            if (h == 0 || w.word in foundPaths || w.cells.isEmpty()) continue
            val cells = if (h >= 2) w.cells else w.cells.take(1)
            for (cell in cells) {
                drawPath(hexPath(pos(cell), s * (0.98f + 0.08f * pulse)), Color.White.copy(alpha = 0.5f + 0.5f * pulse), style = Stroke(s * 0.09f))
            }
        }

        // Glowing trail through the current selection.
        if (selection.size > 1) {
            val trail = Path().apply {
                selection.forEachIndexed { i, h -> val p = pos(h); if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
            }
            drawPath(trail, HoneyLight.copy(alpha = 0.35f), style = Stroke(s * 0.55f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(trail, Color.White.copy(alpha = 0.7f), style = Stroke(s * 0.12f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/** One word as a row of letter tiles. Found words cascade in with a bounce, one tile at a time. */
@Composable
private fun WordSlots(word: String, found: Boolean, hintLevel: Int, color: Color, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    val fill = remember(word) { Animatable(if (found) 1f else 0f) }
    LaunchedEffect(found) { if (found) fill.animateTo(1f, tween(120 * word.length + 200)) }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        word.forEachIndexed { k, ch ->
            // Each tile lands a little after the one before it.
            val local = ((fill.value * (word.length + 1)) - k).coerceIn(0f, 1f)
            val hinted = !found && ((hintLevel >= 1 && k == 0) || (hintLevel >= 2 && k == word.lastIndex))
            Box(
                Modifier
                    .size(24.dp)
                    .graphicsLayer {
                        val bounce = if (local in 0.01f..0.99f) 1f + 0.25f * sin(PI.toFloat() * local) else 1f
                        scaleX = bounce; scaleY = bounce
                    }
                    .drawBehind {
                        val r = CornerRadius(6.dp.toPx())
                        if (local > 0f) {
                            drawRoundRect(lerp(color, Color.White, 0.2f * (1f - local)), cornerRadius = r, alpha = local)
                        } else {
                            drawRoundRect(palette.surfaceHi, cornerRadius = r)
                            drawRoundRect(if (hinted) HoneyLight else palette.outline, cornerRadius = r, style = Stroke(if (hinted) 2.dp.toPx() else 1.dp.toPx()))
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                when {
                    local > 0f -> Txt(ch.toString(), Type.label.copy(fontSize = 13.sp, letterSpacing = 0.sp, fontWeight = FontWeight.Black), onColor(color))
                    hinted -> Txt(ch.toString(), Type.label.copy(fontSize = 13.sp, letterSpacing = 0.sp, fontWeight = FontWeight.Black), HoneyDark)
                }
            }
        }
    }
}
