package io.gh.kdbrian.ttac.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardTest {

    private fun board(vararg rows: String): Board = Board.decode(rows.joinToString(""))

    @Test fun `line counts per size`() {
        assertEquals(8, Rules.of(3).lines.size)
        // 4x4 with 4-in-a-row: 4 rows + 4 cols + 2 diagonals.
        assertEquals(10, Rules.of(4).lines.size)
        // 5x5 with 4-in-a-row: 10 rows + 10 cols + 4 + 4 diagonals.
        assertEquals(28, Rules.of(5).lines.size)
    }

    @Test fun `detects every 3x3 win direction`() {
        assertEquals(listOf(0, 1, 2), board("XXX", "OO.", "...").winner()?.cells)
        assertEquals(listOf(1, 4, 7), board("XO.", "XO.", ".O.").winner()?.cells)
        assertEquals(listOf(0, 4, 8), board("XO.", "OX.", "..X").winner()?.cells)
        val anti = board("X.O", "XO.", "O..").winner()
        assertEquals(Mark.O, anti?.mark)
        assertEquals(listOf(2, 4, 6), anti?.cells)
    }

    @Test fun `no winner on partial board`() {
        assertNull(board("XO.", ".X.", "..O").winner())
    }

    @Test fun `4x4 needs four in a row`() {
        assertNull(board("XXX.", "OOO.", "....", "....").winner())
        assertEquals(Mark.X, board("XXXX", "OOO.", "....", "....").winner()?.mark)
    }

    @Test fun `5x5 finds offset diagonal of four`() {
        val b = board(
            ".....",
            "X....",
            ".X...",
            "..X..",
            "...X.",
        )
        assertEquals(listOf(5, 11, 17, 23), b.winner()?.cells)
    }

    @Test fun `threats are cells that complete a line`() {
        val b = board("XX.", "O..", "O..")
        assertEquals(setOf(2), b.threats(Mark.X))
        assertEquals(emptySet<Int>(), board("XX.", "O..", "...").threats(Mark.O))
        assertEquals(setOf(6), board("OX.", "OX.", "...").threats(Mark.O))
    }

    @Test fun `full board without winner is a draw`() {
        val b = board("XOX", "XOO", "OXX")
        assertNull(b.winner())
        assertTrue(b.isDraw())
    }

    @Test fun `dead position is an early draw`() {
        // Every line already holds both marks: no one can win any more.
        val b = board("XOX", "XOO", "OX.")
        assertTrue(b.isDraw())
        assertFalse(board("XO.", "...", "...").isDraw())
    }

    @Test fun `encode decode round trip`() {
        val b = Board.empty(4).play(0, Mark.X).play(5, Mark.O).play(15, Mark.X)
        assertEquals(b, Board.decode(b.encode()))
        assertEquals("X....O.........X", b.encode())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `cannot play an occupied cell`() {
        Board.empty(3).play(4, Mark.X).play(4, Mark.O)
    }
}
