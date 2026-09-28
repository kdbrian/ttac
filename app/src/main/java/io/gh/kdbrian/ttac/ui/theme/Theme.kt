package io.gh.kdbrian.ttac.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.gh.kdbrian.ttac.data.ThemeMode

@Immutable
data class Palette(
    val isDark: Boolean,
    /** Screen gradient, top → bottom. */
    val bgTop: Color,
    val bg: Color,
    val bgGlow: Color,
    /** Frosted-glass fill for cards and controls. */
    val surface: Color,
    /** Slightly brighter glass for tracks, tiles and pressed states. */
    val surfaceHi: Color,
    /** Hairline border on glass. */
    val outline: Color,
    val text: Color,
    val textDim: Color,
    val grid: Color,
    val accent: Color,
    val accent2: Color,
    val good: Color,
    val bad: Color,
    /** Organic background blobs. */
    val blobs: List<Color>,
    /** Cold → hot ramp used by every heat effect. */
    val heat: List<Color>,
)

// The original TTac identity — neon on midnight navy, and warm paper — with glass surfaces on top.
val DarkPalette = Palette(
    isDark = true,
    bgTop = Color(0xFF1C1446),
    bg = Color(0xFF0B0A1A),
    bgGlow = Color(0xFF2A1458),
    surface = Color(0x14FFFFFF),
    surfaceHi = Color(0x24FFFFFF),
    outline = Color(0x2EFFFFFF),
    text = Color(0xFFF4F1FF),
    textDim = Color(0xFFA29CC8),
    grid = Color(0xFF4B4490),
    accent = Color(0xFFFFB23D),
    accent2 = Color(0xFF9B6BFF),
    good = Color(0xFF3DFFA8),
    bad = Color(0xFFFF5470),
    blobs = listOf(Color(0xFFFF3D7F), Color(0xFF33E1FF), Color(0xFF9B6BFF), Color(0xFF2A1458)),
    heat = listOf(Color(0xFF2B1B6B), Color(0xFF8A1FA8), Color(0xFFFF2E63), Color(0xFFFF8A1F), Color(0xFFFFE45C)),
)

val LightPalette = Palette(
    isDark = false,
    bgTop = Color(0xFFFFEBD6),
    bg = Color(0xFFFBF6EC),
    bgGlow = Color(0xFFFFD9B8),
    surface = Color(0xB8FFFFFF),
    surfaceHi = Color(0xEBFFFFFF),
    outline = Color(0x291D1A2E),
    text = Color(0xFF1D1A2E),
    textDim = Color(0xFF6E6784),
    grid = Color(0xFF1D1A2E),
    accent = Color(0xFFFF7A1A),
    accent2 = Color(0xFF6B3DFF),
    good = Color(0xFF12A867),
    bad = Color(0xFFE0284F),
    blobs = listOf(Color(0xFFFF9AC0), Color(0xFF8FE9FF), Color(0xFFFFC27A), Color(0xFFD9C2FF)),
    heat = listOf(Color(0xFFB7C8FF), Color(0xFFB36BFF), Color(0xFFFF3D7F), Color(0xFFFF8A1F), Color(0xFFFFD21F)),
)

/** Colours offered for marks and profiles. */
val MarkColors = listOf(
    0xFFFF3D7F, 0xFF33E1FF, 0xFFFFB23D, 0xFF3DFFA8,
    0xFF9B6BFF, 0xFFFF5A36, 0xFFFFE45C, 0xFF4D7CFF,
)

val LocalPalette = staticCompositionLocalOf { DarkPalette }

/**
 * Game fonts from the google/fonts repository (SIL Open Font License):
 * Lilita One — chunky, rounded display face for titles and scores;
 * Fredoka — soft, bouncy rounded sans (variable weight) for everything else.
 */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private val Fredoka = FontFamily(
    listOf(400, 500, 600, 700).map { w ->
        Font(io.gh.kdbrian.ttac.R.font.fredoka, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
    }
)

private val LilitaOne = FontFamily(Font(io.gh.kdbrian.ttac.R.font.lilita_one, FontWeight.Normal))

object Type {
    val display = TextStyle(fontFamily = LilitaOne, fontSize = 54.sp, letterSpacing = (-0.5).sp)
    val title = TextStyle(fontFamily = LilitaOne, fontSize = 21.sp, letterSpacing = 0.3.sp)
    val heading = TextStyle(fontFamily = Fredoka, fontWeight = FontWeight.Bold, fontSize = 18.sp)
    val body = TextStyle(fontFamily = Fredoka, fontWeight = FontWeight.Medium, fontSize = 15.sp)
    val label = TextStyle(fontFamily = Fredoka, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 1.1.sp)
    val score = TextStyle(fontFamily = LilitaOne, fontSize = 36.sp)
}

@Composable
fun TTacTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val palette = if (dark) DarkPalette else LightPalette
    CompositionLocalProvider(
        LocalPalette provides palette,
        LocalTextSelectionColors provides TextSelectionColors(palette.accent, palette.accent.copy(alpha = 0.35f)),
        content = content,
    )
}

/** Samples [Palette.heat] at t ∈ [0, 1]. */
fun Palette.heatAt(t: Float): Color {
    val x = t.coerceIn(0f, 1f) * (heat.size - 1)
    val i = x.toInt().coerceAtMost(heat.size - 2)
    return androidx.compose.ui.graphics.lerp(heat[i], heat[i + 1], x - i)
}
