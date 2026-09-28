package io.gh.kdbrian.ttac.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates the app's baseline profile. Run on an API 33+ device or emulator:
 *
 *   ./gradlew :app:generateReleaseBaselineProfile
 *
 * The result lands in app/src/release/generated/baselineProfiles and should be committed.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = PACKAGE, includeInStartupProfile = true) {
        startAndWaitForHome()
        playSoloRound()
        browseScores()
        playBlocks()
    }
}
