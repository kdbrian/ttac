package io.gh.kdbrian.ttac.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.gh.kdbrian.ttac.app.AppViewModel
import io.gh.kdbrian.ttac.data.MarkStyle
import io.gh.kdbrian.ttac.data.ThemeMode
import io.gh.kdbrian.ttac.game.Mark
import io.gh.kdbrian.ttac.ui.components.CanvasSlider
import io.gh.kdbrian.ttac.ui.components.ColorSwatches
import io.gh.kdbrian.ttac.ui.components.Panel
import io.gh.kdbrian.ttac.ui.components.SectionLabel
import io.gh.kdbrian.ttac.ui.components.Segmented
import io.gh.kdbrian.ttac.ui.components.ToggleRow
import io.gh.kdbrian.ttac.ui.components.TopBar
import io.gh.kdbrian.ttac.ui.components.Txt
import io.gh.kdbrian.ttac.ui.components.popIn
import io.gh.kdbrian.ttac.ui.draw.Glyph
import io.gh.kdbrian.ttac.ui.draw.drawMark
import io.gh.kdbrian.ttac.ui.theme.LocalPalette
import io.gh.kdbrian.ttac.ui.theme.MarkColors
import io.gh.kdbrian.ttac.ui.theme.Type

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val palette = LocalPalette.current
    val settings by vm.settings.collectAsStateWithLifecycle()

    // Sliders keep a local value while dragging and only persist when released.
    var stroke by remember(settings.strokeWidth) { mutableFloatStateOf(settings.strokeWidth) }
    var speed by remember(settings.animSpeed) { mutableFloatStateOf(settings.animSpeed) }

    ScreenColumn {
        TopBar("Style & Settings", { vm.back() })

        Panel(Modifier.fillMaxWidth().popIn()) {
            // Re-key on every look change so the preview redraws from scratch.
            key(settings.markStyle, settings.xColor, settings.oColor) {
                StylePreview(settings.markStyle, Color(settings.xColor), Color(settings.oColor), stroke, speed)
            }
        }

        SectionLabel("Mark style")
        Segmented(MarkStyle.entries, settings.markStyle, { it.label }, { s -> vm.updateSettings { it.copy(markStyle = s) }; vm.tapSound() })

        SectionLabel("✕ colour")
        ColorSwatches(MarkColors, settings.xColor, { c -> vm.updateSettings { it.copy(xColor = c) }; vm.tapSound() })
        SectionLabel("◯ colour")
        ColorSwatches(MarkColors, settings.oColor, { c -> vm.updateSettings { it.copy(oColor = c) }; vm.tapSound() })
        Txt("Pass & Play and LAN use each player's profile colour.", Type.label, palette.textDim)

        SectionLabel("Stroke weight")
        CanvasSlider(stroke, 0.05f..0.18f, { stroke = it }, onFinished = { val v = stroke; vm.updateSettings { it.copy(strokeWidth = v) } })

        SectionLabel("Animation speed · ${"%.1f".format(speed)}×")
        CanvasSlider(speed, 0.5f..2.5f, { speed = it }, color = palette.accent2, onFinished = { val v = speed; vm.updateSettings { it.copy(animSpeed = v) } })

        SectionLabel("Theme")
        Segmented(ThemeMode.entries, settings.theme, { it.label }, { t -> vm.updateSettings { it.copy(theme = t) }; vm.tapSound() }, accent = palette.accent2)

        SectionLabel("Game")
        Panel(Modifier.fillMaxWidth()) {
            ToggleRow(Glyph.FLAME, "Threat heat", "Cells that finish a line glow while you play", settings.threatHeat) { v -> vm.updateSettings { it.copy(threatHeat = v) } }
            ToggleRow(Glyph.TROPHY, "Heatmap overlay", "Show where past games were won", settings.heatmapOverlay) { v -> vm.updateSettings { it.copy(heatmapOverlay = v) } }
            ToggleRow(Glyph.SOUND, "Sound", "Clicks, chimes and fanfares", settings.sound) { v -> vm.updateSettings { it.copy(sound = v) } }
            ToggleRow(Glyph.VIBRATE, "Haptics", null, settings.haptics) { v -> vm.updateSettings { it.copy(haptics = v) } }
        }
        Gap(24)
    }
}

/** An ✕ and a ◯ drawing themselves on loop, followed by a heat stroke underneath. */
@Composable
private fun StylePreview(style: MarkStyle, x: Color, o: Color, stroke: Float, speed: Float) {
    val palette = LocalPalette.current
    val period = (2400 / speed).toInt().coerceAtLeast(600)
    val t by rememberInfiniteTransition(label = "preview").animateFloat(
        0f, 1f, infiniteRepeatable(tween(period, easing = LinearEasing)), label = "t",
    )
    Canvas(Modifier.fillMaxWidth().height(130.dp)) {
        val cell = size.height * 0.9f
        val cx = size.width / 2
        val px = (t / 0.35f).coerceIn(0f, 1f)
        val po = ((t - 0.3f) / 0.35f).coerceIn(0f, 1f)
        val fade = 1f - ((t - 0.9f) / 0.1f).coerceIn(0f, 1f)
        drawMark(Mark.X, style, 1, Offset(cx - cell * 0.6f, size.height / 2), cell, x, stroke, px, 0.8f + 0.2f * px, fade, palette.isDark)
        drawMark(Mark.O, style, 2, Offset(cx + cell * 0.6f, size.height / 2), cell, o, stroke, po, 0.8f + 0.2f * po, fade, palette.isDark)
        val lw = ((t - 0.62f) / 0.2f).coerceIn(0f, 1f)
        if (lw > 0) {
            val a = Offset(cx - cell * 1.1f, size.height * 0.92f)
            val b = Offset(cx - cell * 1.1f + cell * 2.2f * lw, size.height * 0.92f)
            drawLine(palette.heat[2].copy(alpha = 0.35f * fade), a, b, 14f, StrokeCap.Round)
            drawLine(palette.heat[4].copy(alpha = fade), a, b, 5f, StrokeCap.Round)
        }
    }
}
