# 23. Performance report 🔴

This chapter records what was measured, what was optimised, and how to reproduce every number.

## App size (measured)

Built with `./gradlew :app:assembleDebug :app:assembleRelease` at commit time:

| | Debug | Release | Change |
|---|---|---|---|
| **APK** | 15.9 MB | **2.48 MB** | −84% |
| Dex | 13 files, 37.9 MB uncompressed | **1 file, 3.2 MB** | −92% |
| `resources.arsc` | 688 KB | **129 KB** | −81% |
| Fonts | 187 KB | 187 KB | (kept whole) |
| Baseline Profile | — | `baseline.prof` 7.7 KB | shipped |

What produced it:

- **R8 minification** (`isMinifyEnabled = true`) removes unused code from every library (Compose, CameraX, zxing,
  coroutines) and shortens names; R8 also **inlines and devirtualises**, so the release dex is faster, not just
  smaller.
- **Resource shrinking** (`isShrinkResources = true`) drops unused resources — and there are few: the launcher icon is
  a single vector, and no images or audio files ship at all.
- **No bitmap or audio assets.** Everything visual is drawn on Canvas and every sound is synthesised at runtime. The
  only binary assets are the two fonts.

## Rendering optimisations applied

These are design decisions throughout the codebase; each links to the chapter that explains it.

| Optimisation | Where | Effect |
|---|---|---|
| Animation values read **in the draw phase** | `BoardCanvas`, backdrop, medals ([ch. 2](../foundations/compose-model.md)) | Animations redraw only; no recomposition per frame |
| Movement and scale via **`graphicsLayer`** | `bouncyClick`, cards, Blocks thump | Transforms on the render layer; content isn't redrawn |
| **One Animatable → many staggered values** | grid draw-in ([ch. 5](../drawing/animation.md)) | Fewer running animations |
| **Stateless particles** from a seeded RNG | win burst ([ch. 7](../drawing/effects.md)) | Zero allocation or bookkeeping per particle |
| **One 60 s clock** for the whole backdrop | `AnimatedBackdrop` ([ch. 8](../drawing/backdrops.md)) | One infinite transition drives blobs, glows and 26 stars |
| **Layered strokes instead of blur** | neon marks, win line | No `RenderEffect` pass; works on API 26+ |
| **Pre-rendered sounds** | `Synth.buildAll()` ([ch. 17](../systems/audio.md)) | No synthesis work at the moment of a win |
| **AI off the main thread** | `Match.maybeRunAi` on `Dispatchers.Default` | Hard 4×4/5×5 search never blocks a frame |
| **Atomic, mutex-guarded writes on IO** | `StatsRepository` ([ch. 18](../systems/persistence.md)) | Saving never touches the main thread |
| **Remembered geometry** | `Rules.of(size)` caches win lines; `QrCode` remembers its bit matrix | Computed once, not per frame |

## Startup: Baseline Profiles

On first launch Android runs app code **interpreted and JIT-compiled**, which is slow until hot code gets compiled. A
**Baseline Profile** lists the classes and methods used at startup and in key journeys; the Play Store and
`androidx.profileinstaller` use it to compile that code **ahead of time** on install.

TTac's profile generator (`baselineprofile/BaselineProfileGenerator.kt`) drives the real app with UI Automator:
cold start to the home screen, a solo game with several moves, every scoreboard tab, then a Blocks run played with
real gestures. The generated profile holds **16,507 rules** and is merged into release builds; `profileinstaller`
compiles it on devices that don't install from Play.

```bash
./gradlew :app:generateReleaseBaselineProfile                   # regenerate on an API 33+ device
./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest  # measure
```

`StartupBenchmarks` measures three things with Macrobenchmark:

| Test | Metric | What it isolates |
|---|---|---|
| `startupNoCompilation` | time to initial & full display | the worst case: no AOT compilation |
| `startupBaselineProfile` | time to initial & full display | with the profile applied |
| `soloGameFrames` | frame duration percentiles | smoothness while playing Tic-Tac-Toe |
| `blocksFrames` | frame duration percentiles | smoothness during ~12 s of Blocks: rotates, drags, hard drops |

Frame timing uses `FrameTimingGfxInfoMetric` (Android's own `dumpsys gfxinfo` frame stats). The trace-based
`FrameTimingMetric` found no frame-timeline slices on the test phone — Samsung builds don't always emit them.

**Time to full display** is reported by the app itself: `ReportDrawnWhen { stats.profiles.isNotEmpty() }` on the
home screen marks the moment saved data has loaded and the real UI (not an empty shell) is on screen.

## Startup results (measured)

Samsung Galaxy A32 (SM-A325F), Android 13, 10 cold starts each:

| Compilation | min | median | max |
|---|---|---|---|
| None (worst case) | 997 ms | 1,053 ms | 2,421 ms |
| Baseline Profile | 958 ms | 1,138 ms | 1,277 ms |

**Reading these honestly:**

- The profile **halves the worst case** (2.4 s → 1.3 s) and tightens the spread — the unlucky slow cold starts
  disappear.
- The **median didn't improve** in this run (1.05 s → 1.14 s). The phone was at 15–17% battery and couldn't charge
  past it, so the run was made with Macrobenchmark's low-battery guard suppressed; low battery lets Android throttle
  the CPU, which adds noise of this size. Treat the median comparison as inconclusive until it's re-run on a charged
  device.
- Time to full display equals time to initial display: saved data loads before the first frame finishes, so users
  never see an empty home screen.

## Frame results (measured)

Same phone, warm start with the Baseline Profile, 5 iterations each, `FrameTimingGfxInfoMetric`:

| Journey | Frames | P50 | P90 | P95 | P99 | Janky frames |
|---|---|---|---|---|---|---|
| Solo Tic-Tac-Toe (several moves) | ~198 | 93 ms | 117 ms | 133 ms | 150 ms | 100% |
| Blocks (~12 s of rotates, drags, hard drops) | ~212 | 77 ms | 101 ms | 121 ms | 150 ms | 100% |

**This is the report's most important finding: gameplay does not hit 60 fps on this mid-range phone.** A frame
needs to finish in ~16.7 ms; the median here is 5–6× that. (The same low-battery caveat applies — throttling
makes it worse — but it can't explain a gap this size.)

Where the time goes, from reading the rendering code against these numbers:

1. **The backdrop never stops.** `AnimatedBackdrop` redraws every frame: a full-screen gradient, three blob paths,
   two screen-sized radial glows, a player-tint wash and up to 26 neon stars. On a mobile GPU that's several
   full-screen layers of translucent overdraw per frame, behind everything else.
2. **Marks are rebuilt on every draw.** `drawMark` recomputes each mark's point geometry and rebuilds its `Path`s
   on every call — for the board *and* for each backdrop star — and neon marks stroke that path four times.
3. **Idle animations keep the board redrawing.** The board's pulse and heat-flow infinite transitions invalidate it
   every frame even when nothing is happening.

The fixes follow directly — cache mark geometry and paths per (mark, style, seed); draw the backdrop's slow layers
(gradient, blobs, glows) through `drawWithCache` or at a reduced rate and keep only the stars live; stop
infinite transitions when there's nothing to pulse (no threats, no win) — and re-running `soloGameFrames` and
`blocksFrames` will show whether they land.

## Reproducing

1. Connect an API 33+ device (Baseline Profiles can't be generated on older, non-rooted devices).
2. Run the two Gradle commands above. To run one benchmark, pass
   `-Pandroid.testInstrumentationRunnerArguments.class=io.gh.kdbrian.ttac.baselineprofile.StartupBenchmarks#blocksFrames`
   (the runner honours only one `#method` per filter).
3. **Back up app data first**: installing the benchmark build replaces the app, which clears its saved games and
   scores on the device.
4. Results appear in `baselineprofile/build/outputs/connected_android_test_additional_output/` as JSON, and in
   Android Studio's test results.
