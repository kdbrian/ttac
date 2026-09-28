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
