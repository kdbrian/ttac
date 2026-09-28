package io.gh.kdbrian.ttac.data

import io.gh.kdbrian.ttac.game.BlocksDifficulty
import io.gh.kdbrian.ttac.game.BlocksGame
import io.gh.kdbrian.ttac.game.Difficulty
import io.gh.kdbrian.ttac.game.Ludo
import io.gh.kdbrian.ttac.game.LudoAction
import io.gh.kdbrian.ttac.game.LudoColor
import io.gh.kdbrian.ttac.game.LudoSeat
import io.gh.kdbrian.ttac.game.Mark
import io.gh.kdbrian.ttac.game.SeatKind
import io.gh.kdbrian.ttac.net.LudoCodec
import io.gh.kdbrian.ttac.net.LudoWire
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.random.Random

class SessionAndWireTest {

    private val json = Json { ignoreUnknownKeys = true; classDiscriminator = "t" }

    @Test fun `a blocks run restores exactly and plays on identically`() {
        val g = BlocksGame(BlocksDifficulty.MEDIUM, Random(4))
        repeat(6) { g.move(if (it % 2 == 0) 1 else -1); g.hardDrop() }
        g.tick(); g.tick()
        val copy = BlocksGame.restore(g.snapshot())
        assertEquals(g.snapshot(), copy.snapshot())
        // Same inputs from here produce the same board.
        repeat(5) { g.hardDrop(); copy.hardDrop() }
        assertEquals(g.snapshot(), copy.snapshot())
    }

    @Test fun `every saved session survives json`() {
        val seats = LudoColor.entries.map { LudoSeat(it, if (it == LudoColor.BLUE) SeatKind.LOCAL else SeatKind.CPU, it.label) }
        val sessions: List<SavedSession> = listOf(
            SavedSession.TicTacToe(1, GameMode.SOLO, 3, Difficulty.HARD, "You", "CPU", "you", "cpu", 1, 2, "HUMAN", "AI", "X...O....", Mark.X, 2, 1, 0, 1),
            SavedSession.Blocks(2, BlocksGame(BlocksDifficulty.EASY, Random(1)).snapshot()),
            SavedSession.Hive(3, 2, 2, listOf("0,0,A", "1,0,B"), mapOf("AB" to listOf("0,0", "1,0")), listOf("AB"), listOf(), mapOf("AB" to 1)),
            SavedSession.Ludo(4, Ludo.newGame(seats, Difficulty.EASY, Random(2))),
        )
        for (s in sessions) {
            val text = json.encodeToString(SavedSession.serializer(), s)
            assertEquals(s, json.decodeFromString(SavedSession.serializer(), text))
        }
    }

    @Test fun `ludo wire messages round trip and garbage is ignored`() {
        val seats = LudoColor.entries.map { LudoSeat(it, SeatKind.REMOTE, it.label, device = it.ordinal) }
        val state = Ludo.newGame(seats, Difficulty.MEDIUM, Random(3))
        val msgs = listOf(
            LudoWire.Join("Ann", "Neon Fox 10", 0xFF33E1FF),
            LudoWire.Welcome(2),
            LudoWire.Lobby(seats, "Velvet Badger 91"),
            LudoWire.State(state),
            LudoWire.Act(LudoAction.Challenge(LudoColor.RED, LudoColor.BLUE)),
            LudoWire.Bye,
        )
        for (m in msgs) assertEquals(m, LudoCodec.decode(LudoCodec.encode(m)))
        assertNull(LudoCodec.decode("{\"t\":\"act\"}"))
        assertNull(LudoCodec.decode("not json"))
    }

    @Test fun `ludo rewards pay the winner more`() {
        val base = StatsData()
        val win = StatsReducer.recordLudoGame(base, won = true)
        val loss = StatsReducer.recordLudoGame(base, won = false)
        assertEquals(base.coins + LudoRewards.PLAY_COINS + LudoRewards.WIN_COINS, win.coins)
        assertEquals(base.gems + LudoRewards.WIN_GEMS, win.gems)
        assertEquals(base.coins + LudoRewards.PLAY_COINS, loss.coins)
        assertEquals(1, win.ludo.wins)
        assertEquals(base.coins + LudoRewards.DUEL_COINS, StatsReducer.recordLudoDuel(base).coins)
    }
}
