package io.gh.kdbrian.ttac.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class BlocksTest {

    @Test fun `every tetromino has four cells in every rotation`() {
        for (t in Tetromino.entries) for (r in 0..3) assertEquals("$t r$r", 4, t.cells(r).toSet().size)
    }

    @Test fun `four rotations return to the start`() {
        val g = BlocksGame(BlocksDifficulty.MEDIUM, Random(1))
        val before = g.cellsOf()
        repeat(4) { g.rotate() }
        assertEquals(before.toSet(), g.cellsOf().toSet())
    }

    @Test fun `pieces stop at the walls`() {
        val g = BlocksGame(BlocksDifficulty.EASY, Random(2))
        repeat(20) { g.move(-1) }
        assertEquals(0, g.cellsOf().minOf { it.first })
        repeat(20) { g.move(1) }
        assertEquals(g.width - 1, g.cellsOf().maxOf { it.first })
    }

    @Test fun `hard drop lands on the floor and scores`() {
        val g = BlocksGame(BlocksDifficulty.MEDIUM, Random(3))
        val dist = g.hardDrop()
        assertTrue(dist > 0)
        assertEquals(dist * 2, g.score)
        assertEquals(1, g.lockCount)
        assertTrue((0 until g.width).any { g[it, g.height - 1] != 0 })
    }

    @Test fun `difficulty changes size and speed`() {
        val easy = BlocksGame(BlocksDifficulty.EASY)
        val hard = BlocksGame(BlocksDifficulty.HARD)
        assertTrue(hard.width > easy.width && hard.height > easy.height)
        assertTrue(hard.gravityMs < easy.gravityMs)
    }

    @Test fun `stacking without clearing eventually tops out`() {
        val g = BlocksGame(BlocksDifficulty.EASY, Random(4))
        var guard = 0
        while (!g.over && guard++ < 500) g.hardDrop()
        assertTrue(g.over)
        assertFalse(g.won)
    }

    @Test fun `clearing lines by playing reaches the goal`() {
        // Greedy bot: try every rotation/column, keep the placement with the fewest holes & lowest stack.
        val g = BlocksGame(BlocksDifficulty.EASY, Random(5))
        var guard = 0
        while (!g.over && guard++ < 2000) {
            var best: Triple<Int, Int, Int>? = null
            var bestCost = Int.MAX_VALUE
            for (r in 0..3) for (x in -2 until g.width + 2) {
                if (!g.fits(g.piece, r, x, g.py)) continue
                var y = g.py
                while (g.fits(g.piece, r, x, y + 1)) y++
                val cells = g.cellsOf(r = r, x = x, y = y).toSet()
                fun filled(cx: Int, cy: Int) = (cx to cy) in cells || (cy >= 0 && g[cx, cy] != 0)
                val rows = (0 until g.height).count { cy -> (0 until g.width).all { cx -> filled(cx, cy) } }
                var holes = 0
                var top = g.height
                for (cx in 0 until g.width) {
                    var seen = false
                    for (cy in 0 until g.height) {
                        if (filled(cx, cy)) { if (!seen) top = minOf(top, cy); seen = true } else if (seen) holes++
                    }
                }
                val cost = holes * 50 - rows * 100 + (g.height - top) * 3
                if (cost < bestCost) { bestCost = cost; best = Triple(r, x, y) }
            }
            val (r, x, _) = best ?: break
            while (g.rot != r) g.rotate()
            while (g.px < x && g.move(1)) {}
            while (g.px > x && g.move(-1)) {}
            g.hardDrop()
        }
        assertTrue("lines=${g.lines}", g.won)
        assertTrue(g.lines >= BlocksDifficulty.EASY.goalLines)
    }
}
