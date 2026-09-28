package io.gh.kdbrian.ttac.ui

import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.activity.compose.LocalActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.gh.kdbrian.ttac.app.AppViewModel
import io.gh.kdbrian.ttac.app.Screen
import io.gh.kdbrian.ttac.game.Mark
import io.gh.kdbrian.ttac.ui.draw.AnimatedBackdrop
import io.gh.kdbrian.ttac.ui.components.CelebrationOverlay
import io.gh.kdbrian.ttac.ui.screens.BlocksScreen
import io.gh.kdbrian.ttac.ui.screens.HiveScreen
import io.gh.kdbrian.ttac.ui.screens.AwardsScreen
import io.gh.kdbrian.ttac.ui.screens.GameScreen
import io.gh.kdbrian.ttac.ui.screens.HomeScreen
import io.gh.kdbrian.ttac.ui.screens.LanScreen
import io.gh.kdbrian.ttac.ui.screens.LocalSetupScreen
import io.gh.kdbrian.ttac.ui.screens.ScoreboardScreen
import io.gh.kdbrian.ttac.ui.screens.SettingsScreen
import io.gh.kdbrian.ttac.ui.screens.SoloSetupScreen
import io.gh.kdbrian.ttac.ui.theme.LocalPalette
import io.gh.kdbrian.ttac.ui.theme.TTacTheme
import io.gh.kdbrian.ttac.ui.theme.heatAt

@Composable
fun TTacApp(vm: AppViewModel = viewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    TTacTheme(settings.theme) {
        val palette = LocalPalette.current
        val activity = LocalActivity.current as? androidx.activity.ComponentActivity
        LaunchedEffect(palette.isDark) {
            // Transparent bars in both themes; only the icon contrast changes.
            val style = if (palette.isDark) SystemBarStyle.dark(Color.Transparent.toArgb())
            else SystemBarStyle.light(Color.Transparent.toArgb(), Color.Transparent.toArgb())
            activity?.enableEdgeToEdge(style, style)
        }

        BackHandler(enabled = vm.celebrations.isNotEmpty()) { vm.dismissCelebration() }
        BackHandler(enabled = vm.screen != Screen.HOME && vm.celebrations.isEmpty()) { vm.back() }

        Box(Modifier.fillMaxSize()) {
            // During a game the whole screen washes toward whoever is up (or whoever won).
            val m = vm.match
            val focus = if (vm.screen == Screen.GAME && m != null) {
                m.win?.mark ?: if (m.isOver) null else m.turn
            } else null
            // Arcade games warm the whole screen as they speed up.
            val arcadeTint = when (vm.screen) {
                Screen.BLOCKS, Screen.HIVE -> if (vm.arcadeHeat > 0f) palette.heatAt(vm.arcadeHeat) else null
                else -> null
            }
            AnimatedBackdrop(
                Color(settings.xColor), Color(settings.oColor), Modifier.fillMaxSize(),
                tint = focus?.let { Color(m!!.config.seat(it).player.color) } ?: arcadeTint,
                tintSide = when (focus) { Mark.X -> -1f; Mark.O -> 1f; null -> 0f },
            )
            AnimatedContent(
                targetState = vm.screen,
                transitionSpec = {
                    val dir = if (vm.forward) 1 else -1
                    (slideInHorizontally(spring(dampingRatio = 0.75f, stiffness = 300f)) { it * dir / 3 } +
                        fadeIn(tween(220)) + scaleIn(spring(dampingRatio = 0.6f, stiffness = 300f), initialScale = 0.92f))
                        .togetherWith(
                            slideOutHorizontally(tween(220)) { -it * dir / 4 } + fadeOut(tween(160)) + scaleOut(tween(220), targetScale = 0.96f)
                        )
                },
                label = "screens",
            ) { screen ->
                when (screen) {
                    Screen.HOME -> HomeScreen(vm)
                    Screen.SOLO_SETUP -> SoloSetupScreen(vm)
                    Screen.LOCAL_SETUP -> LocalSetupScreen(vm)
                    Screen.LAN -> LanScreen(vm)
                    Screen.GAME -> GameScreen(vm)
                    Screen.SCOREBOARD -> ScoreboardScreen(vm)
                    Screen.AWARDS -> AwardsScreen(vm)
                    Screen.BLOCKS -> BlocksScreen(vm)
                    Screen.HIVE -> HiveScreen(vm)
                    Screen.SETTINGS -> SettingsScreen(vm)
                }
            }
            CelebrationOverlay(vm.celebrations.firstOrNull(), { vm.tapSound(); vm.dismissCelebration() }, vm::openArcade)
        }
    }
}
