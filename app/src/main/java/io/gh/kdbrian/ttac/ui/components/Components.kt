package io.gh.kdbrian.ttac.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.gh.kdbrian.ttac.ui.draw.Glyph
import io.gh.kdbrian.ttac.ui.draw.GlyphIcon
import io.gh.kdbrian.ttac.ui.theme.LocalPalette
import io.gh.kdbrian.ttac.ui.theme.Palette
import io.gh.kdbrian.ttac.ui.theme.Type
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Ink = Color(0xFF1A0B3D)

/** Readable foreground for a solid fill. */
fun onColor(fill: Color): Color = if (fill.luminance() > 0.45f) Ink else Color.White

/** Plain text in the current palette. */
@Composable
fun Txt(
    text: String,
    style: TextStyle = Type.body,
    color: Color = LocalPalette.current.text,
    modifier: Modifier = Modifier,
    align: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
) {
    BasicText(
        text, modifier,
        style = style.copy(color = color, textAlign = align ?: TextAlign.Unspecified),
        maxLines = maxLines, overflow = TextOverflow.Ellipsis,
    )
}

// ---- Glass -------------------------------------------------------------------------------------

/** Frosted glass: translucent fill, a soft top sheen and a hairline border. */
fun DrawScope.drawGlass(palette: Palette, radius: Float, fill: Color = palette.surface, border: Color = palette.outline) {
    val r = CornerRadius(radius)
    drawRoundRect(fill, cornerRadius = r)
    drawRoundRect(
        Brush.verticalGradient(listOf(Color.White.copy(alpha = if (palette.isDark) 0.10f else 0.35f), Color.Transparent), endY = size.height * 0.6f),
        cornerRadius = r,
    )
    drawRoundRect(border, cornerRadius = r, style = Stroke(1.2.dp.toPx()))
}

// ---- Motion ------------------------------------------------------------------------------------

/** Springy entrance: scale + fade in after [delayMs]. Stagger lists by index for a cascade. */
fun Modifier.popIn(delayMs: Int = 0): Modifier = composed {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(delayMs.toLong())
        progress.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 300f))
    }
    graphicsLayer {
        val p = progress.value
        scaleX = 0.7f + 0.3f * p
        scaleY = 0.7f + 0.3f * p
        alpha = p.coerceIn(0f, 1f)
        translationY = (1f - p) * 28.dp.toPx()
    }
}

/** Squash on press, overshoot on release. */
fun Modifier.bouncyClick(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val click by rememberUpdatedState(onClick)
    this
        .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
        .semantics { role = Role.Button }
        .pointerInput(enabled) {
            if (!enabled) return@pointerInput
            awaitEachGesture {
                awaitFirstDown()
                scope.launch { scale.animateTo(0.9f, spring(stiffness = Spring.StiffnessHigh)) }
                val up = waitForUpOrCancellation()
                scope.launch { scale.animateTo(1f, spring(dampingRatio = 0.3f, stiffness = 500f)) }
                if (up != null) click()
            }
        }
}

// ---- Buttons -----------------------------------------------------------------------------------

/**
 * Slim pill button. A solid [color] gets a gradient face and a coloured glow beneath; a
 * translucent colour (e.g. `palette.surfaceHi`) renders as glass.
 */
@Composable
fun BouncyButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = LocalPalette.current.accent,
    glyph: Glyph? = null,
    enabled: Boolean = true,
    compact: Boolean = false,
) {
    val palette = LocalPalette.current
    val glass = color.alpha < 0.99f
    val face = if (enabled) color else lerp(color, palette.surfaceHi, 0.65f)
    val fg = if (glass) palette.text else onColor(face)
    val height = if (compact) 40.dp else 50.dp

    Box(
        modifier
            .heightIn(min = height)
            .bouncyClick(enabled, onClick)
            .drawBehind {
                val r = size.height / 2
                if (glass) {
                    drawGlass(palette, r, fill = face)
                } else {
                    // Coloured glow under the pill, then the face.
                    drawRoundRect(face.copy(alpha = if (enabled) 0.35f else 0.1f), Offset(size.width * 0.06f, 5.dp.toPx()), Size(size.width * 0.88f, size.height), CornerRadius(r))
                    drawRoundRect(Brush.verticalGradient(listOf(lerp(face, Color.White, 0.22f), face, lerp(face, Color.Black, 0.12f))), cornerRadius = CornerRadius(r))
                    drawRoundRect(Color.White.copy(alpha = 0.35f), Offset(r * 0.8f, 2.dp.toPx()), Size(size.width - r * 1.6f, 1.5.dp.toPx()), CornerRadius(1.dp.toPx()))
                }
            }
            .padding(horizontal = if (compact) 16.dp else 22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (glyph != null) {
                GlyphIcon(glyph, fg, size = if (compact) 16.dp else 19.dp)
                if (text.isNotEmpty()) Spacer(Modifier.width(8.dp))
            }
            if (text.isNotEmpty()) Txt(text, if (compact) Type.label else Type.body.copy(fontWeight = FontWeight.ExtraBold, fontSize = 16.sp), fg, maxLines = 1)
        }
    }
}

/** Round glass icon button. */
@Composable
fun IconBubble(
    glyph: Glyph,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = LocalPalette.current.text,
    active: Boolean = false,
    size: Dp = 44.dp,
    label: String? = null,
) {
    val palette = LocalPalette.current
    val on by animateFloatAsState(if (active) 1f else 0f, spring(dampingRatio = 0.5f), label = "on")
    Box(
        modifier
            .size(size)
            .semantics { if (label != null) contentDescription = label }
            .bouncyClick(onClick = onClick)
            .drawBehind {
                drawGlass(palette, this.size.minDimension / 2, fill = lerp(palette.surface, palette.accent, on))
            },
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(glyph, if (active) Ink else tint, size = size * 0.46f)
    }
}

/**
 * Big round action (home screen): a glass ring around a bright disc holding a coloured icon,
 * with a label underneath. [emphasized] adds a slow pulsing halo.
 */
@Composable
fun RoundAction(
    glyph: Glyph,
    label: String,
    onClick: () -> Unit,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 76.dp,
    emphasized: Boolean = false,
) {
    val palette = LocalPalette.current
    val pulse by rememberInfiniteTransition(label = "halo").animateFloat(0f, 1f, infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "p")
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(size)
                .semantics { contentDescription = label }
                .bouncyClick(onClick = onClick)
                .drawBehind {
                    val r = this.size.minDimension / 2
                    if (emphasized) {
                        val halo = r * (1.08f + 0.06f * pulse)
                        drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.45f), Color.Transparent), center, halo * 1.25f), halo * 1.25f)
                    }
                    drawCircle(palette.surface, r)
                    drawCircle(palette.outline, r, style = Stroke(1.2.dp.toPx()))
                    val inner = r * 0.76f
                    drawCircle(color.copy(alpha = 0.35f), inner, center + Offset(0f, 3.dp.toPx()))
                    drawCircle(Brush.verticalGradient(listOf(Color.White, Color(0xFFEDE6FF)), center.y - inner, center.y + inner), inner)
                },
            contentAlignment = Alignment.Center,
        ) {
            GlyphIcon(glyph, color, size = size * 0.36f)
        }
        Spacer(Modifier.height(10.dp))
        Txt(label, if (emphasized) Type.body.copy(fontWeight = FontWeight.ExtraBold, fontSize = 16.sp) else Type.body.copy(fontWeight = FontWeight.Bold), palette.text, align = TextAlign.Center, maxLines = 1)
    }
}

/** Canvas avatar: a gradient disc in the player's colour with their initial. */
@Composable
fun Avatar(name: String, color: Color, modifier: Modifier = Modifier, size: Dp = 40.dp, ring: Color = Color.White) {
    Box(
        modifier
            .size(size)
            .drawBehind {
                val r = this.size.minDimension / 2
                drawCircle(color.copy(alpha = 0.4f), r, center + Offset(0f, 2.dp.toPx()))
                drawCircle(Brush.linearGradient(listOf(lerp(color, Color.White, 0.3f), color, lerp(color, Color.Black, 0.25f)), Offset.Zero, Offset(this.size.width, this.size.height)), r)
                // Two little eyes, because every game avatar deserves a face.
                val eye = r * 0.11f
                drawCircle(Color.White, eye * 1.6f, center + Offset(-r * 0.3f, -r * 0.42f))
                drawCircle(Ink, eye, center + Offset(-r * 0.3f, -r * 0.4f))
                drawCircle(Color.White, eye * 1.6f, center + Offset(r * 0.3f, -r * 0.42f))
                drawCircle(Ink, eye, center + Offset(r * 0.3f, -r * 0.4f))
                drawCircle(ring, r - 1.dp.toPx(), style = Stroke(2.dp.toPx()))
            },
        contentAlignment = Alignment.Center,
    ) {
        Txt(
            name.trim().firstOrNull()?.uppercase() ?: "?",
            TextStyle(fontWeight = FontWeight.Black, fontSize = (size.value * 0.36f).sp),
            onColor(color),
            Modifier.padding(top = size * 0.2f),
        )
    }
}

// ---- Containers --------------------------------------------------------------------------------

/** Frosted glass card. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    color: Color = LocalPalette.current.surface,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    highlight: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = LocalPalette.current
    Column(
        modifier
            .drawBehind { drawGlass(palette, 24.dp.toPx(), fill = color, border = highlight ?: palette.outline) }
            .padding(contentPadding),
        content = content,
    )
}

/** Top bar: round back button, centred title, optional actions on the right. */
@Composable
fun TopBar(title: String, onBack: () -> Unit, modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit = {}) {
    Box(modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        IconBubble(Glyph.BACK, onBack, Modifier.align(Alignment.CenterStart), label = "Back")
        Txt(title, Type.title, modifier = Modifier.padding(horizontal = 56.dp), align = TextAlign.Center, maxLines = 1)
        Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

// ---- Inputs ------------------------------------------------------------------------------------

/** Segmented picker: glass track with a glowing thumb that stretches as it travels. */
@Composable
fun <T> Segmented(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = LocalPalette.current.accent,
) {
    val palette = LocalPalette.current
    val index = options.indexOf(selected).coerceAtLeast(0)
    val pos by animateFloatAsState(index.toFloat(), spring(dampingRatio = 0.55f, stiffness = 380f), label = "seg")
    Box(
        modifier
            .fillMaxWidth()
            .height(44.dp)
            .drawBehind {
                drawGlass(palette, size.height / 2)
                val inset = 4.dp.toPx()
                val w = (size.width - inset * 2) / options.size
                val stretch = kotlin.math.abs(pos - index) * w * 0.35f
                val h = size.height - inset * 2
                val tl = Offset(inset + pos * w - stretch / 2, inset)
                drawRoundRect(accent.copy(alpha = 0.35f), tl + Offset(0f, 3.dp.toPx()), Size(w + stretch, h), CornerRadius(h / 2))
                drawRoundRect(Brush.verticalGradient(listOf(lerp(accent, Color.White, 0.2f), accent), tl.y, tl.y + h), tl, Size(w + stretch, h), CornerRadius(h / 2))
            },
    ) {
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { i, option ->
                val isSel = i == index
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .semantics { this.selected = isSel; role = Role.Tab }
                        .pointerInput(option) { detectTapGestures { onSelect(option) } },
                    contentAlignment = Alignment.Center,
                ) {
                    Txt(label(option), Type.label, if (isSel) onColor(accent) else palette.textDim, maxLines = 1)
                }
            }
        }
    }
}

/** Canvas slider with a bouncy thumb. */
@Composable
fun CanvasSlider(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    color: Color = LocalPalette.current.accent,
    onFinished: () -> Unit = {},
) {
    val palette = LocalPalette.current
    var dragging by remember { mutableStateOf(false) }
    val thumb by animateFloatAsState(if (dragging) 1.3f else 1f, spring(dampingRatio = 0.35f, stiffness = 500f), label = "thumb")
    val change by rememberUpdatedState(onChange)
    val finished by rememberUpdatedState(onFinished)
    fun valueAt(x: Float, w: Float, pad: Float): Float {
        val t = ((x - pad) / (w - pad * 2)).coerceIn(0f, 1f)
        return range.start + t * (range.endInclusive - range.start)
    }
    Canvas(
        modifier
            .fillMaxWidth()
            .height(40.dp)
            .pointerInput(range) {
                val pad = 16.dp.toPx()
                detectTapGestures(onPress = { change(valueAt(it.x, size.width.toFloat(), pad)); finished() })
            }
            .pointerInput(range) {
                val pad = 16.dp.toPx()
                detectHorizontalDragGestures(
                    onDragStart = { dragging = true; change(valueAt(it.x, size.width.toFloat(), pad)) },
                    onDragEnd = { dragging = false; finished() },
                    onDragCancel = { dragging = false; finished() },
                ) { input, _ -> change(valueAt(input.position.x, size.width.toFloat(), pad)) }
            }
    ) {
        val pad = 16.dp.toPx()
        val t = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
        val y = size.height / 2
        val x = pad + t * (size.width - pad * 2)
        val track = 8.dp.toPx()
        drawLine(palette.surfaceHi, Offset(pad, y), Offset(size.width - pad, y), track, StrokeCap.Round)
        drawLine(Brush.horizontalGradient(listOf(lerp(color, Color.White, 0.3f), color), pad, x), Offset(pad, y), Offset(x, y), track, StrokeCap.Round)
        val r = 11.dp.toPx() * thumb
        drawCircle(color.copy(alpha = 0.4f), r * 1.5f, Offset(x, y))
        drawCircle(Color.White, r, Offset(x, y))
        drawCircle(color, r * 0.45f, Offset(x, y))
    }
}

/** Canvas toggle with a springy knob that squashes as it travels. */
@Composable
fun CanvasSwitch(checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    val t by animateFloatAsState(if (checked) 1f else 0f, spring(dampingRatio = 0.45f, stiffness = 420f), label = "sw")
    Canvas(
        modifier
            .size(54.dp, 32.dp)
            .semantics { role = Role.Switch; selected = checked }
            .bouncyClick { onChange(!checked) }
    ) {
        val r = CornerRadius(size.height / 2)
        drawRoundRect(lerp(palette.surfaceHi, palette.good, t.coerceIn(0f, 1f)), cornerRadius = r)
        drawRoundRect(palette.outline, cornerRadius = r, style = Stroke(1.2.dp.toPx()))
        val knobR = size.height / 2 - 4.dp.toPx()
        val x = size.height / 2 + t * (size.width - size.height)
        val squash = 1f + kotlin.math.abs(t - if (checked) 1f else 0f) * 0.6f
        drawOval(Color.White, Offset(x - knobR * squash, size.height / 2 - knobR), Size(knobR * 2 * squash, knobR * 2))
    }
}

@Composable
fun ToggleRow(glyph: Glyph, title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    val palette = LocalPalette.current
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).drawBehind { drawCircle(palette.surfaceHi) }, contentAlignment = Alignment.Center) {
            GlyphIcon(glyph, palette.text, size = 18.dp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Txt(title, Type.body.copy(fontWeight = FontWeight.Bold))
            if (subtitle != null) Txt(subtitle, Type.body.copy(fontSize = 12.sp), palette.textDim)
        }
        CanvasSwitch(checked, onChange)
    }
}

/** Row of colour dots; the selected one gets a springy ring. */
@Composable
fun ColorSwatches(colors: List<Long>, selected: Long, onSelect: (Long) -> Unit, modifier: Modifier = Modifier, dotSize: Dp = 34.dp) {
    val palette = LocalPalette.current
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        colors.forEach { c ->
            val sel = c == selected
            val ring by animateFloatAsState(if (sel) 1f else 0f, spring(dampingRatio = 0.35f, stiffness = 400f), label = "ring")
            Canvas(
                Modifier
                    .size(dotSize)
                    .semantics { this.selected = sel; role = Role.RadioButton }
                    .bouncyClick { onSelect(c) }
            ) {
                val r = size.minDimension / 2
                if (ring > 0.01f) drawCircle(Color(c).copy(alpha = 0.4f * ring), r)
                drawCircle(Color(c), r * (0.58f + 0.1f * ring))
                if (ring > 0.01f) drawCircle(palette.text, r * (0.8f + 0.12f * ring), style = Stroke(2.dp.toPx() * ring))
            }
        }
    }
}

/** A number that rolls up/down with a bounce whenever it changes. */
@Composable
fun RollingNumber(value: Int, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState = value,
        transitionSpec = {
            val up = targetState > initialState
            (slideInVertically(spring(dampingRatio = 0.45f, stiffness = 380f)) { h -> if (up) h else -h } + fadeIn() + scaleIn(spring(dampingRatio = 0.4f), initialScale = 1.6f))
                .togetherWith(slideOutVertically(tween(180)) { h -> if (up) -h else h } + fadeOut(tween(150)))
        },
        modifier = modifier,
        label = "rolling",
    ) { v -> Txt(v.toString(), style, color) }
}

@Composable
fun NameField(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier, onDone: () -> Unit = {}) {
    val palette = LocalPalette.current
    BasicTextField(
        value = value,
        onValueChange = { onChange(it.take(21)) },
        singleLine = true,
        textStyle = Type.body.copy(color = palette.text, fontWeight = FontWeight.Bold, fontSize = 16.sp),
        cursorBrush = SolidColor(palette.accent),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = modifier,
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .drawBehind { drawGlass(palette, size.height / 2, fill = palette.surfaceHi) }
                    .padding(horizontal = 18.dp, vertical = 13.dp)
            ) {
                if (value.isEmpty()) Txt(placeholder, Type.body.copy(fontSize = 16.sp), palette.textDim)
                inner()
            }
        },
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Txt(text.uppercase(), Type.label, LocalPalette.current.textDim, modifier.padding(top = 16.dp, bottom = 8.dp, start = 4.dp))
}

@Composable
fun Centered(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) =
    Box(modifier, contentAlignment = Alignment.Center, content = content)
