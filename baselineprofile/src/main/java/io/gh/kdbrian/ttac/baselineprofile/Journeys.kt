package io.gh.kdbrian.ttac.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until

const val PACKAGE = "io.gh.kdbrian.ttac"
private const val TIMEOUT = 5_000L

/**
 * The critical user journeys both the profile generator and the benchmarks exercise:
 * cold start to the home screen, then into a solo game with a few moves.
 */
fun MacrobenchmarkScope.startAndWaitForHome() {
    pressHome()
    startActivityAndWait()
    device.wait(Until.hasObject(By.desc("Solo vs AI")), TIMEOUT)
}

fun MacrobenchmarkScope.playSoloRound() {
    device.findObject(By.desc("Solo vs AI"))?.click() ?: return
    device.wait(Until.hasObject(By.text("Start")), TIMEOUT)
    device.findObject(By.text("Start"))?.click() ?: return
    device.waitForIdle()
    // Tap across the middle of the board: whichever cells are free take the move.
    val w = device.displayWidth
    val h = device.displayHeight
    for ((fx, fy) in listOf(0.5f to 0.5f, 0.2f to 0.38f, 0.8f to 0.62f, 0.2f to 0.62f, 0.8f to 0.38f)) {
        device.click((w * fx).toInt(), (h * fy).toInt())
        Thread.sleep(700)
    }
    device.pressBack()
    device.wait(Until.hasObject(By.desc("Solo vs AI")), TIMEOUT)
}

fun MacrobenchmarkScope.browseScores() {
    device.findObject(By.desc("Scores"))?.click() ?: return
    device.waitForIdle()
    for (tab in listOf("Versus", "LAN", "History", "Heat")) {
        device.findObject(By.text(tab))?.click()
        device.waitForIdle()
    }
    device.pressBack()
}

/**
 * Opens Blocks from the home screen's Arcade section, starts a run and plays it with real gestures for
 * [seconds]: rotate taps, left/right drags and hard-drop flicks. Needs Blocks unlocked on the device.
 */
fun MacrobenchmarkScope.playBlocks(seconds: Int = 12) {
    val w = device.displayWidth
    val h = device.displayHeight
    // The arcade cards sit below the fold.
    device.swipe(w / 2, (h * 0.8f).toInt(), w / 2, (h * 0.25f).toInt(), 20)
    device.wait(Until.hasObject(By.text("Blocks")), TIMEOUT)
    device.findObject(By.text("Blocks"))?.click() ?: return
    device.wait(Until.hasObject(By.text("Start")), TIMEOUT)
    device.findObject(By.text("Start"))?.click() ?: return
    Thread.sleep(600)
    val end = System.currentTimeMillis() + seconds * 1000L
    var i = 0
    while (System.currentTimeMillis() < end) {
        when (i % 4) {
            0 -> device.click(w / 2, h / 2)                                                    // rotate
            1 -> device.swipe(w / 2, h / 2, w / 2 - w / 5, h / 2, 6)                            // drag left
            2 -> device.swipe(w / 2, h / 2, w / 2 + w / 5, h / 2, 6)                            // drag right
            else -> device.swipe(w / 2, (h * 0.35f).toInt(), w / 2, (h * 0.75f).toInt(), 3)   // flick = hard drop
        }
        Thread.sleep(250)
        i++
    }
    device.pressBack()
    device.wait(Until.hasObject(By.desc("Solo vs AI")), TIMEOUT)
}
