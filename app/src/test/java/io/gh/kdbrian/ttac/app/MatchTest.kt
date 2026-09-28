package io.gh.kdbrian.ttac.app

import io.gh.kdbrian.ttac.data.GameMode
import io.gh.kdbrian.ttac.data.MatchResult
import io.gh.kdbrian.ttac.data.PlayerRef
import io.gh.kdbrian.ttac.fx.Sfx
import io.gh.kdbrian.ttac.game.Difficulty
import io.gh.kdbrian.ttac.game.Mark
import io.gh.kdbrian.ttac.net.LanMessage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
class MatchTest {

    private class FakeHost : MatchHost {
        val sounds = mutableListOf<Sfx>()
        val results = mutableListOf<MatchResult>()
        val sent = mutableListOf<LanMessage>()
        override fun play(sfx: Sfx) { sounds += sfx }
        override fun record(result: MatchResult) { results += result }
        override fun send(message: LanMessage) { sent += message }
    }

    private val a = PlayerRef("a", "Ann", 1)
    private val b = PlayerRef("b", "Bob", 2)

    private fun TestScope.match(config: MatchConfig, host: FakeHost) =
        Match(config, this, host, aiDispatcher = StandardTestDispatcher(testScheduler), random = Random(1))

    private fun localConfig() = MatchConfig(GameMode.LOCAL, 3, Seat(a, SeatKind.HUMAN), Seat(b, SeatKind.HUMAN))

    @Test fun `local players alternate and a win is scored and recorded`() = runTest {
        val host = FakeHost()
        val m = match(localConfig(), host)
        listOf(0, 3, 1, 4, 2).forEach { m.tap(it) }
        assertEquals(Mark.X, m.win?.mark)
        assertEquals(1, m.xWins)
        assertEquals(1, host.results.size)
        assertEquals(Sfx.WIN, host.sounds.last())
        assertFalse(m.canTap)
    }

    @Test fun `taps on occupied cells or after the end are ignored`() = runTest {
        val m = match(localConfig(), FakeHost())
        m.tap(0)
        m.tap(0)
        assertEquals(Mark.O, m.turn)
        listOf(3, 1, 4, 2).forEach { m.tap(it) }
        val before = m.board
        m.tap(8)
        assertEquals(before, m.board)
    }

    @Test fun `rounds alternate the starting player and reset the board`() = runTest {
        val m = match(localConfig(), FakeHost())
        listOf(0, 3, 1, 4, 2).forEach { m.tap(it) }
        m.requestNextRound()
        assertEquals(2, m.round)
        assertEquals(Mark.O, m.turn)
        assertTrue(m.board.isEmpty)
        assertNull(m.win)
    }

    @Test fun `solo AI replies after the human and records personal stats`() = runTest {
        val host = FakeHost()
        val cfg = MatchConfig(GameMode.SOLO, 3, Seat(a, SeatKind.HUMAN), Seat(b, SeatKind.AI), Difficulty.HARD)
        val m = match(cfg, host)
        m.tap(0)
        assertTrue(m.aiThinking)
        assertFalse(m.canTap)
        advanceUntilIdle()
        assertFalse(m.aiThinking)
        assertEquals(2, m.board.toList().count { it != null })
        assertEquals(Mark.X, m.turn)

        // Play the rest out: hard AI can't be beaten, so the human either draws or loses.
        while (!m.isOver) {
            m.tap(m.board.emptyCells.first())
            advanceUntilIdle()
        }
        assertTrue(m.win?.mark != Mark.X)
        val r = host.results.single()
        assertEquals(Mark.X, r.humanMark)
        assertEquals(Difficulty.HARD, r.difficulty)
    }

    @Test fun `AI opens when it starts the round`() = runTest {
        val cfg = MatchConfig(GameMode.SOLO, 3, Seat(a, SeatKind.AI), Seat(b, SeatKind.HUMAN), Difficulty.EASY)
        val m = match(cfg, FakeHost())
        advanceUntilIdle()
        assertEquals(1, m.board.toList().count { it != null })
        assertEquals(Mark.O, m.turn)
    }

    private fun lanConfig() = MatchConfig(GameMode.LAN, 3, Seat(a, SeatKind.HUMAN), Seat(b, SeatKind.REMOTE))

    @Test fun `LAN sends local moves and validates remote ones`() = runTest {
        val host = FakeHost()
        val m = match(lanConfig(), host)
        // Remote can't move out of turn.
        m.onRemote(LanMessage.Move(1, 4))
        assertTrue(m.board.isEmpty)

        m.tap(0)
        assertEquals(LanMessage.Move(1, 0), host.sent.single())
        assertFalse("local player must wait for the remote", m.canTap)

        m.onRemote(LanMessage.Move(2, 4)) // wrong round
        m.onRemote(LanMessage.Move(1, 0)) // occupied
        m.onRemote(LanMessage.Move(1, 99)) // out of range
        assertEquals(Mark.O, m.turn)
        assertEquals(1, m.board.toList().count { it != null })

        m.onRemote(LanMessage.Move(1, 4))
        assertEquals(Mark.O, m.board[4])
        assertTrue(m.canTap)
    }

    @Test fun `LAN rematch needs both sides`() = runTest {
        val host = FakeHost()
        val m = match(lanConfig(), host)
        m.tap(0); m.onRemote(LanMessage.Move(1, 3))
        m.tap(1); m.onRemote(LanMessage.Move(1, 4))
        m.tap(2)
        assertNotNull(m.win)

        m.requestNextRound()
        assertEquals(1, m.round)
        assertTrue(m.localRematch)
        assertEquals(LanMessage.Rematch(1), host.sent.last())

        m.onRemote(LanMessage.Rematch(0)) // stale
        assertEquals(1, m.round)
        m.onRemote(LanMessage.Rematch(1))
        assertEquals(2, m.round)
        assertEquals(Mark.O, m.turn)
        assertFalse(m.canTap)
    }

    @Test fun `LAN rematch works when the remote asks first`() = runTest {
        val m = match(lanConfig(), FakeHost())
        m.tap(0); m.onRemote(LanMessage.Move(1, 3))
        m.tap(1); m.onRemote(LanMessage.Move(1, 4))
        m.tap(2)
        m.onRemote(LanMessage.Rematch(1))
        assertTrue(m.remoteRematch)
        m.requestNextRound()
        assertEquals(2, m.round)
    }

    @Test fun `no taps once the opponent has left`() = runTest {
        val m = match(lanConfig(), FakeHost())
        m.remoteGone = "Opponent left"
        m.tap(0)
        assertTrue(m.board.isEmpty)
    }
}
