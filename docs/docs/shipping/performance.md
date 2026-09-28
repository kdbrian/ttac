# 21. Performance report 🔴

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
| **Pre-rendered sounds** | `Synth.buildAll()` ([ch. 16](../systems/audio.md)) | No synthesis work at the moment of a win |
| **AI off the main thread** | `Match.maybeRunAi` on `Dispatchers.Default` | Hard 4×4/5×5 search never blocks a frame |
| **Atomic, mutex-guarded writes on IO** | `StatsRepository` ([ch. 17](../systems/persistence.md)) | Saving never touches the main thread |
| **Remembered geometry** | `Rules.of(size)` caches win lines; `QrCode` remembers its bit matrix | Computed once, not per frame |

## Startup: Baseline Profiles

On first launch Android runs app code **interpreted and JIT-compiled**, which is slow until hot code gets compiled. A
**Baseline Profile** lists the classes and methods used at startup and in key journeys; the Play Store and
`androidx.profileinstaller` use it to compile that code **ahead of time** on install.

TTac's profile generator (`baselineprofile/BaselineProfileGenerator.kt`) drives the real app with UI Automator:
cold start to the home screen, a solo game with several moves, then every scoreboard tab. The profile is merged into
release builds, and `profileinstaller` compiles it on devices that don't install from Play.

```bash
./gradlew :app:generateReleaseBaselineProfile                   # regenerate on an API 33+ device
./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest  # measure
```

`StartupBenchmarks` measures three things with Macrobenchmark:

| Test | Metric | What it isolates |
|---|---|---|
| `startupNoCompilation` | time to initial & full display | the worst case: no AOT compilation |
| `startupBaselineProfile` | time to initial & full display | with the profile applied |
| `soloGameFrames` | frame duration percentiles | smoothness while playing |

**Time to full display** is reported by the app itself: `ReportDrawnWhen { stats.profiles.isNotEmpty() }` on the
home screen marks the moment saved data has loaded and the real UI (not an empty shell) is on screen.

## Reproducing

1. Connect an API 33+ device (Baseline Profiles can't be generated on older, non-rooted devices).
2. Run the two Gradle commands above.
3. Results appear in `baselineprofile/build/outputs/connected_android_test_additional_output/` as JSON, and in
   Android Studio's test results.
