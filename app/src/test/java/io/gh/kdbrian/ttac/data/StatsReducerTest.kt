package io.gh.kdbrian.ttac.data

import io.gh.kdbrian.ttac.game.Board
import io.gh.kdbrian.ttac.game.Difficulty
import io.gh.kdbrian.ttac.game.Mark
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsReducerTest {

    private val you = PlayerRef("you", "You", 1)
    private val cpu = PlayerRef("cpu", "CPU", 2)
    private val ann = PlayerRef("a-id", "Ann", 3)
    private val bob = PlayerRef("b-id", "Bob", 4)

    private val xWinBoard = Board.decode("XXXOO....")
    private val drawBoard = Board.decode("XOXXOOOXX")

    private fun solo(board: Board, human: Mark = Mark.X) = MatchResult(
        GameMode.SOLO, board, board.winner(), you, cpu, humanMark = human, difficulty = Difficulty.HARD, timestamp = 1,
    )

    private fun local(board: Board, x: PlayerRef = ann, o: PlayerRef = bob) =
        MatchResult(GameMode.LOCAL, board, board.winner(), x, o, timestamp = 1)

    @Test fun `solo wins build a streak and draws reset it`() {
        var s = StatsData()
        s = StatsReducer.record(s, solo(xWinBoard))
        s = StatsReducer.record(s, solo(xWinBoard))
        assertEquals(Record(wins = 2, streak = 2, bestStreak = 2), s.soloRecord(Difficulty.HARD))
        s = StatsReducer.record(s, solo(drawBoard))
        assertEquals(Record(wins = 2, draws = 1, streak = 0, bestStreak = 2), s.soloRecord(Difficulty.HARD))
        s = StatsReducer.record(s, solo(xWinBoard, human = Mark.O))
        assertEquals(1, s.soloRecord(Difficulty.HARD).losses)
        assertEquals(Record(), s.soloRecord(Difficulty.EASY))
    }

    @Test fun `head to head is one entry regardless of seat order`() {
        var s = StatsData()
        s = StatsReducer.record(s, local(xWinBoard, ann, bob)) // Ann wins as X
        s = StatsReducer.record(s, local(xWinBoard, bob, ann)) // Bob wins as X
        s = StatsReducer.record(s, local(drawBoard, bob, ann))
        assertEquals(1, s.headToHead.size)
        val h = s.headToHead.getValue(HeadToHead.key(ann.id, bob.id))
        assertEquals(1, h.aWins)
        assertEquals(1, h.bWins)
        assertEquals(1, h.draws)
        assertEquals(Record(wins = 1, losses = 1, draws = 1, streak = 0, bestStreak = 1, lossStreak = 0, worstLossStreak = 1), s.players[ann.id])
        assertEquals("Ann", s.playerNames[ann.id])
    }

    @Test fun `heat counts winning cells and filled cells`() {
        var s = StatsReducer.record(StatsData(), local(xWinBoard))
        s = StatsReducer.record(s, local(Board.decode("X..X..X.O")))
        val heat = s.heatFor(3)
        assertEquals(2, heat.games)
        assertEquals(listOf(2, 1, 1, 1, 0, 0, 1, 0, 0), heat.winCounts)
        assertEquals(listOf(2, 1, 1, 2, 1, 0, 1, 0, 1), heat.playCounts)
        assertEquals(1f, heat.normalizedWins(9)[0])
        assertEquals(0.5f, heat.normalizedWins(9)[1])
    }

    @Test fun `history is newest first and capped`() {
        var s = StatsData()
        repeat(StatsReducer.HISTORY_LIMIT + 5) { s = StatsReducer.record(s, local(drawBoard).copy(timestamp = it.toLong())) }
        assertEquals(StatsReducer.HISTORY_LIMIT, s.history.size)
        assertEquals((StatsReducer.HISTORY_LIMIT + 4).toLong(), s.history.first().timestamp)
        assertEquals("DRAW", s.history.first().result)
    }

    @Test fun `stats survive a json round trip`() {
        val s = StatsReducer.record(StatsReducer.record(StatsData(), solo(xWinBoard)), local(xWinBoard))
        val json = Json { ignoreUnknownKeys = true }
        val text = json.encodeToString(StatsData.serializer(), s)
        assertEquals(s, json.decodeFromString(StatsData.serializer(), text))
    }

    @Test fun `losing streaks are tracked and reset by wins and draws`() {
        var r = Record()
        r = r.add(Outcome.LOSS).add(Outcome.LOSS)
        assertEquals(2, r.lossStreak)
        assertEquals(2, r.worstLossStreak)
        r = r.add(Outcome.WIN)
        assertEquals(0, r.lossStreak)
        r = r.add(Outcome.LOSS).add(Outcome.DRAW)
        assertEquals(0, r.lossStreak)
        assertEquals(2, r.worstLossStreak)
    }

    @Test fun `win streak medals land every gap and diamond repeats`() {
        var s = StatsData()
        repeat(21) { s = StatsReducer.record(s, solo(xWinBoard).copy(timestamp = it.toLong() + 10)) }
        val you = StatsData.SOLO_OWNER
        for (m in listOf(Medal.BRONZE, Medal.SILVER, Medal.GOLD, Medal.PLATINUM)) assertEquals(m.name, 1, s.medalCount(you, m))
        // 15, 18 and 21 all award Diamond.
        assertEquals(3, s.medalCount(you, Medal.DIAMOND))
        assertEquals(7, s.awards.size)
        assertEquals(21, s.awards.first().streak)
        assertEquals(listOf(Medal.DIAMOND), StatsReducer.awardsAt(s, 30).map { it.medal })
    }

    @Test fun `losing streak earns grit and breaking it earns a comeback`() {
        var s = StatsData()
        repeat(3) { s = StatsReducer.record(s, solo(xWinBoard, human = Mark.O).copy(timestamp = it.toLong())) }
        assertEquals(1, s.medalCount(StatsData.SOLO_OWNER, Medal.GRIT_I))
        s = StatsReducer.record(s, solo(xWinBoard).copy(timestamp = 99))
        assertEquals(listOf(Medal.COMEBACK), StatsReducer.awardsAt(s, 99).map { it.medal })
        assertEquals(3, s.awards.first().streak)
    }

    @Test fun `multiplayer medals go to profiles but not LAN guests`() {
        val guest = PlayerRef("lan:Zed", "Zed (LAN)", 9)
        var s = StatsData()
        repeat(3) { s = StatsReducer.record(s, local(xWinBoard, ann, guest).copy(timestamp = it.toLong())) }
        assertEquals(1, s.medalCount(ann.id, Medal.BRONZE))
        assertEquals(0, s.medalCount(guest.id, Medal.GRIT_I))
        assertTrue(s.awards.all { it.ownerId == ann.id })
    }

    @Test fun `next medal targets`() {
        assertEquals(Medal.BRONZE to 3, Medals.nextWin(0))
        assertEquals(Medal.SILVER to 6, Medals.nextWin(3))
        assertEquals(Medal.DIAMOND to 18, Medals.nextWin(16))
        assertEquals(Medal.GRIT_II to 6, Medals.nextGrit(4))
    }
}
