package io.gh.kdbrian.ttac.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class LudoTest {

    private fun seats(vararg kinds: SeatKind) = LudoColor.entries.mapIndexed { i, c ->
        LudoSeat(c, kinds.getOrElse(i) { SeatKind.EMPTY }, c.label)
    }

    @Test fun `loop is 52 distinct squares around the cross`() {
        assertEquals(52, LudoBoard.loop.size)
        assertEquals(52, LudoBoard.loop.toSet().size)
        // Consecutive squares touch — straight along the arms, diagonally at the four inner corners — wrap included.
        val steps = (LudoBoard.loop + LudoBoard.loop.first()).zipWithNext().map { (a, b) ->
            maxOf(kotlin.math.abs(a.first - b.first), kotlin.math.abs(a.second - b.second))
        }
        assertTrue(steps.all { it == 1 })
        // No loop square is inside the 3×3 centre or on a home lane.
        val lanes = LudoColor.entries.flatMap { LudoBoard.lane(it) }.toSet()
        assertTrue(LudoBoard.loop.none { it in lanes || (it.first in 6..8 && it.second in 6..8) })
    }

    @Test fun `each colour enters its lane from the square just before its start`() {
        for (c in LudoColor.entries) {
            val entry = LudoBoard.cellOf(c, 50)!!
            val firstLane = LudoBoard.cellOf(c, 51)!!
            assertEquals(1, kotlin.math.abs(entry.first - firstLane.first) + kotlin.math.abs(entry.second - firstLane.second))
        }
    }

    @Test fun `only a six leaves base and home needs an exact roll`() {
        assertNull(Ludo.target(-1, 5))
        assertEquals(0, Ludo.target(-1, 6))
        assertEquals(56, Ludo.target(53, 3))
        assertNull(Ludo.target(53, 4))
        assertNull(Ludo.target(56, 1))
    }

    @Test fun `only the expected seat may act`() {
        val s = Ludo.newGame(seats(SeatKind.LOCAL, SeatKind.CPU), Difficulty.EASY, Random(1))
        val other = (s.active - s.turn).first()
        assertNull(Ludo.apply(s, LudoAction.Challenge(other, s.turn)))
        assertNull(Ludo.apply(s, LudoAction.Challenge(s.turn, s.turn)))
        assertNull(Ludo.apply(s, LudoAction.Roll(s.turn)))
        assertNotNull(Ludo.apply(s, LudoAction.Challenge(s.turn, other)))
    }

    @Test fun `duel winner rolls and draws replay`() {
        var s = Ludo.newGame(seats(SeatKind.LOCAL, SeatKind.LOCAL), Difficulty.EASY, Random(2))
        val a = s.turn
        val b = (s.active - a).first()
        s = Ludo.apply(s, LudoAction.Challenge(a, b))!!
        // A (✕) takes the top row while B (◯) plays the middle row.
        for ((who, cell) in listOf(a to 0, b to 3, a to 1, b to 4, a to 2)) s = Ludo.apply(s, LudoAction.DuelMove(who, cell))!!
        assertEquals(LudoPhase.ROLL, s.phase)
        assertEquals(a, s.roller)

        // A drawn duel resets the board and swaps marks.
        var d = Ludo.newGame(seats(SeatKind.LOCAL, SeatKind.LOCAL), Difficulty.EASY, Random(3))
        val c = d.turn
        val e = (d.active - c).first()
        d = Ludo.apply(d, LudoAction.Challenge(c, e))!!
        for (cell in listOf(0, 1, 2, 4, 3, 5, 7, 6, 8)) {
            d = Ludo.apply(d, LudoAction.DuelMove(d.duel!!.current, cell))!!
            if (d.duel!!.round > 1) break          // dead positions are called a draw early
        }
        assertEquals(LudoPhase.DUEL, d.phase)
        assertEquals(2, d.duel!!.round)
        assertEquals(e, d.duel!!.current)   // the opponent opens round two as ✕
    }

    @Test fun `landing on a rival sends it home but safe squares protect`() {
        val base = Ludo.newGame(seats(SeatKind.LOCAL, SeatKind.LOCAL), Difficulty.EASY, Random(4))
        // Blue pawn 4 squares behind a red pawn on an unsafe square.
        val redProgress = 5                                         // red start + 5
        val square = LudoBoard.loopIndex(LudoColor.RED, redProgress)
        val blueProgress = (square - LudoColor.BLUE.start + 52) % 52 - 4
        val s = base.copy(
            pawns = base.pawns + (LudoColor.BLUE to listOf(blueProgress, -1, -1, -1)) + (LudoColor.RED to listOf(redProgress, -1, -1, -1)),
            turn = LudoColor.BLUE, phase = LudoPhase.MOVE, roller = LudoColor.BLUE, die = 4, movable = listOf(0),
        )
        val after = Ludo.apply(s, LudoAction.MovePawn(LudoColor.BLUE, 0))!!
        assertEquals(-1, after.pawns.getValue(LudoColor.RED)[0])
        assertTrue(after.lastEvent.contains("captures"))

        val onStart = s.copy(pawns = s.pawns + (LudoColor.RED to listOf(0, -1, -1, -1)))
        val startSquare = LudoColor.RED.start
        val blueToStart = (startSquare - LudoColor.BLUE.start + 52) % 52 - 4
        val safe = Ludo.apply(onStart.copy(pawns = onStart.pawns + (LudoColor.BLUE to listOf(blueToStart, -1, -1, -1))), LudoAction.MovePawn(LudoColor.BLUE, 0))!!
        assertEquals(0, safe.pawns.getValue(LudoColor.RED)[0])
    }

    @Test fun `a six grants another roll`() {
        val base = Ludo.newGame(seats(SeatKind.LOCAL, SeatKind.LOCAL), Difficulty.EASY, Random(5))
        val s = base.copy(turn = LudoColor.BLUE, phase = LudoPhase.MOVE, roller = LudoColor.BLUE, die = 6, movable = listOf(0))
        val after = Ludo.apply(s, LudoAction.MovePawn(LudoColor.BLUE, 0))!!
        assertEquals(LudoPhase.ROLL, after.phase)
        assertEquals(LudoColor.BLUE, after.roller)
        assertEquals(0, after.pawns.getValue(LudoColor.BLUE)[0])
    }

    @Test fun `four CPUs always finish a game`() {
        for (seed in 1..6) {
            val rnd = Random(seed)
            var s = Ludo.newGame(List(4) { LudoSeat(LudoColor.entries[it], SeatKind.CPU, "CPU$it") }, Difficulty.EASY, rnd)
            var steps = 0
            while (s.phase != LudoPhase.OVER && steps++ < 50_000) {
                s = Ludo.apply(s, LudoBot.act(s, rnd)!!, rnd) ?: error("bot made an illegal move in $s")
            }
            assertEquals("seed $seed", LudoPhase.OVER, s.phase)
            assertTrue(s.pawns.getValue(s.winner!!).all { it == LudoBoard.HOME })
        }
    }

    @Test fun `state survives json for the LAN`() {
        val s = Ludo.newGame(seats(SeatKind.LOCAL, SeatKind.REMOTE, SeatKind.CPU), Difficulty.HARD, Random(9))
        val json = kotlinx.serialization.json.Json
        assertEquals(s, json.decodeFromString(LudoState.serializer(), json.encodeToString(LudoState.serializer(), s)))
    }
}
