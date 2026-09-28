package io.gh.kdbrian.ttac.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingGfxInfoMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Measures what the baseline profile buys us. Compare `startupNoCompilation` with
 * `startupBaselineProfile` (time to initial and full display), and frame timing in a Tic-Tac-Toe game and a
 * Blocks run.
 *
 *   ./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest
 */
/*
 * Frame timing uses the gfxinfo-based metric: some devices (e.g. Samsung's) don't emit the frame-timeline
 * trace slices FrameTimingMetric needs.
 */
@OptIn(ExperimentalMetricApi::class)
@RunWith(AndroidJUnit4::class)
class StartupBenchmarks {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun startupNoCompilation() = startup(CompilationMode.None())

    @Test
    fun startupBaselineProfile() = startup(CompilationMode.Partial(BaselineProfileMode.Require))

    private fun startup(mode: CompilationMode) = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = mode,
        startupMode = StartupMode.COLD,
        iterations = 10,
        setupBlock = { pressHome() },
    ) {
        startAndWaitForHome()
    }

    @Test
    fun soloGameFrames() = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingGfxInfoMetric()),
        compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
        startupMode = StartupMode.WARM,
        iterations = 5,
        setupBlock = { startAndWaitForHome() },
    ) {
        playSoloRound()
    }

    @Test
    fun blocksFrames() = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingGfxInfoMetric()),
        compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
        startupMode = StartupMode.WARM,
        iterations = 5,
        setupBlock = { startAndWaitForHome() },
    ) {
        playBlocks()
    }
}
