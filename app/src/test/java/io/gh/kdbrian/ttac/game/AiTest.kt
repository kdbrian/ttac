package io.gh.kdbrian.ttac.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class AiTest {

    private fun board(vararg rows: String): Board = Board.decode(rows.joinToString(""))

    /** Plays a full game; returns the winner or null for a draw. */
    private fun playOut(size: Int, first: Mark, pick: (Board, Mark) -> Int): Mark? {
        var b = Board.empty(size)
        var turn = first
        while (true) {
            b = b.play(pick(b, turn), turn)
            b.winner()?.let { return it.mark }
            if (b.isDraw()) return null
            turn = turn.other
        }
    }

    @Test fun `hard never loses to random play on 3x3`() {
        val rnd = Random(1234)
        repeat(300) { game ->
            val aiMark = if (game % 2 == 0) Mark.X else Mark.O
            val result = playOut(3, Mark.X) { b, m ->
                if (m == aiMark) Ai.chooseMove(b, m, Difficulty.HARD, rnd) else b.emptyCells.random(rnd)
            }
            assertNotEquals("Hard AI lost game $game", aiMark.other, result)
        }
    }

    @Test fun `hard versus hard is always a draw on 3x3`() {
        val rnd = Random(7)
        repeat(10) {
            assertEquals(null, playOut(3, Mark.X) { b, m -> Ai.chooseMove(b, m, Difficulty.HARD, rnd) })
        }
    }

    @Test fun `hard and medium take an immediate win`() {
        val b = board("XX.", "OO.", "...")
        for (d in listOf(Difficulty.MEDIUM, Difficulty.HARD)) {
            assertEquals(2, Ai.chooseMove(b, Mark.X, d, Random(1)))
            assertEquals(5, Ai.chooseMove(b, Mark.O, d, Random(1)))
        }
    }

    @Test fun `hard and medium block an immediate loss`() {
        val b = board("OO.", "X..", "..X")
        for (d in listOf(Difficulty.MEDIUM, Difficulty.HARD)) {
            assertEquals(2, Ai.chooseMove(b, Mark.X, d, Random(3)))
        }
    }

    @Test fun `hard blocks on 4x4 and 5x5`() {
        val four = board("OOO.", "XX..", "X...", "....")
        assertEquals(3, Ai.chooseMove(four, Mark.X, Difficulty.HARD, Random(5)))
        val five = board(
            ".....",
            ".OOO.",
            "X....",
            "X....",
            ".....",
        )
        val move = Ai.chooseMove(five, Mark.X, Difficulty.HARD, Random(5))
        assertTrue("expected a block at 5 or 9, got $move", move == 5 || move == 9)
    }

    @Test fun `every difficulty returns legal moves on every size quickly`() {
        val rnd = Random(99)
        for (size in Rules.SUPPORTED_SIZES) for (d in Difficulty.entries) {
            val started = System.currentTimeMillis()
            playOut(size, Mark.X) { b, m ->
                val move = Ai.chooseMove(b, m, d, rnd)
                assertTrue(b[move] == null)
                move
            }
            val elapsed = System.currentTimeMillis() - started
            assertTrue("$size×$size $d took ${elapsed}ms", elapsed < 20_000)
        }
    }

    @Test fun `evaluation favours open lines`() {
        val b = board("X..", "...", "...")
        assertTrue(Ai.evaluate(b, Mark.X) > 0)
        assertTrue(Ai.evaluate(b, Mark.O) < 0)
    }
}
