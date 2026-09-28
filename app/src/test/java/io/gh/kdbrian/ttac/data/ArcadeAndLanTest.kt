package io.gh.kdbrian.ttac.data

import io.gh.kdbrian.ttac.game.Board
import io.gh.kdbrian.ttac.game.Difficulty
import io.gh.kdbrian.ttac.game.Mark
import io.gh.kdbrian.ttac.net.JoinLink
import io.gh.kdbrian.ttac.net.SessionCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArcadeAndLanTest {
    private val you = PlayerRef("you", "You", 1)
    private val cpu = PlayerRef("cpu", "CPU", 2)
    private val xWin = Board.decode("XXXOO....")

    @Test fun `ten in a row unlocks blocks`() {
        var s = StatsData()
        repeat(9) { s = StatsReducer.record(s, MatchResult(GameMode.SOLO, xWin, xWin.winner(), you, cpu, Mark.X, Difficulty.EASY, it.toLong())) }
        assertFalse(s.isUnlocked(ArcadeGame.BLOCKS))
        val before = s
        s = StatsReducer.record(s, MatchResult(GameMode.SOLO, xWin, xWin.winner(), you, cpu, Mark.X, Difficulty.EASY, 99))
        assertTrue(s.isUnlocked(ArcadeGame.BLOCKS))
        assertEquals(listOf(ArcadeGame.BLOCKS), StatsReducer.newlyUnlocked(before, s))
    }

    @Test fun `three blocks wins in a row unlocks the hive, a loss resets`() {
        var s = StatsData(unlocked = listOf(ArcadeGame.BLOCKS))
        s = StatsReducer.recordBlocks(s, true, 100, 8)
        s = StatsReducer.recordBlocks(s, false, 50, 2)
        s = StatsReducer.recordBlocks(s, true, 100, 8)
        s = StatsReducer.recordBlocks(s, true, 300, 8)
        assertFalse(s.isUnlocked(ArcadeGame.HIVE))
        s = StatsReducer.recordBlocks(s, true, 250, 8)
        assertTrue(s.isUnlocked(ArcadeGame.HIVE))
        assertEquals(300, s.blocks.bestScore)
        assertEquals(34, s.blocks.totalLines)
    }

    @Test fun `hive progress and demos`() {
        var s = StatsReducer.recordHive(StatsData(), 120, 2, levelCleared = false)
        s = StatsReducer.recordHive(s, -500, 0, levelCleared = false)
        assertEquals(0, s.hive.points)
        s = StatsReducer.recordHive(s, 25, 0, levelCleared = true)
        assertEquals(2, s.hive.level)
        assertEquals(1, s.hive.bestLevel)
        assertTrue(s.canDemo(ArcadeGame.HIVE))
        s = s.copy(demosUsed = listOf(ArcadeGame.HIVE))
        assertFalse(s.canDemo(ArcadeGame.HIVE))
    }

    @Test fun `join links carry code and alias only`() {
        val code = SessionCode.encode("192.168.0.7", 5555)!!
        val link = JoinLink.build(code, "Neon Otter 12")
        assertFalse("192.168" in link)
        assertEquals(code to "Neon Otter 12", JoinLink.parse(link))
        assertNull(JoinLink.parse("https://example.com"))
        assertNull(JoinLink.parse("ttac://join?c=NOPE&a=x"))
    }

    @Test fun `lan board tracks each alias with streaks`() {
        val me = PlayerRef("p1", "Ann", 1)
        val rival = PlayerRef("lan:Cosmic Otter 42", "Bo", 2)
        var s = StatsData()
        val oWin = Board.decode("OOOXX.X..")
        listOf(xWin, xWin, oWin, xWin).forEachIndexed { i, b ->
            s = StatsReducer.record(s, MatchResult(GameMode.LAN, b, b.winner(), me, rival, timestamp = i.toLong()))
        }
        val board = LanBoard.members(s, "Neon Fox 10")
        val r = board.first { it.isRemote }
        assertEquals("Cosmic Otter 42", r.alias)
        assertEquals(1, r.record.wins)
        assertEquals(1, r.record.lossStreak)
        val mine = board.first { !it.isRemote }
        assertEquals("Neon Fox 10", mine.alias)
        assertEquals(2, mine.record.bestStreak)
        assertEquals(1, mine.record.streak)
        assertEquals(4, LanBoard.historyOf(s, rival.id).size)
        assertEquals(board.first(), mine)
    }
}
