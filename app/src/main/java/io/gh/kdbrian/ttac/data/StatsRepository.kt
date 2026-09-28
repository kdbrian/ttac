package io.gh.kdbrian.ttac.data

import android.content.Context
import io.gh.kdbrian.ttac.game.Board
import io.gh.kdbrian.ttac.game.Difficulty
import io.gh.kdbrian.ttac.game.Mark
import io.gh.kdbrian.ttac.game.WinLine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

data class PlayerRef(val id: String, val name: String, val color: Long)

/** Everything needed to record one finished round. */
data class MatchResult(
    val mode: GameMode,
    val board: Board,
    val win: WinLine?,
    val x: PlayerRef,
    val o: PlayerRef,
    /** Solo only: which mark the human played. */
    val humanMark: Mark? = null,
    val difficulty: Difficulty? = null,
    val timestamp: Long = System.currentTimeMillis(),
)

/** Pure state transitions for [StatsData] — kept separate from I/O so they are unit-testable. */
object StatsReducer {
    const val HISTORY_LIMIT = 200

    fun record(data: StatsData, result: MatchResult): StatsData {
        val winner = result.win?.mark
        val size = result.board.size
        val cells = result.board.cellCount

        val match = MatchRecord(
            timestamp = result.timestamp,
            mode = result.mode,
            size = size,
            xName = result.x.name,
            oName = result.o.name,
            xColor = result.x.color,
            oColor = result.o.color,
            result = winner?.name ?: "DRAW",
            board = result.board.encode(),
            winCells = result.win?.cells.orEmpty(),
            difficulty = result.difficulty,
            xId = result.x.id,
            oId = result.o.id,
        )

        val oldHeat = data.heatFor(size)
        val winCounts = MutableList(cells) { oldHeat.winCounts.getOrElse(it) { 0 } }
        val playCounts = MutableList(cells) { oldHeat.playCounts.getOrElse(it) { 0 } }
        result.win?.cells?.forEach { winCounts[it]++ }
        for (i in 0 until cells) if (result.board[i] != null) playCounts[i]++
        val heat = data.heat + (size.toString() to HeatData(oldHeat.games + 1, winCounts, playCounts))

        var next = data.copy(
            history = (listOf(match) + data.history).take(HISTORY_LIMIT),
            heat = heat,
        )

        val awards = ArrayList<MedalAward>()
        fun award(ownerId: String, ownerName: String, before: Record, after: Record, outcome: Outcome) {
            for (m in Medals.earned(before, after, outcome)) {
                awards += MedalAward(m, ownerId, ownerName, if (m.kind == MedalKind.LOSS) after.lossStreak else if (m == Medal.COMEBACK) before.lossStreak else after.streak, result.timestamp)
            }
        }

        if (result.mode == GameMode.SOLO) {
            val human = requireNotNull(result.humanMark) { "Solo results need humanMark" }
            val difficulty = requireNotNull(result.difficulty) { "Solo results need difficulty" }
            val outcome = outcomeFor(human, winner)
            val key = difficulty.name
            val overall = next.soloOverall.add(outcome)
            award(StatsData.SOLO_OWNER, "You", next.soloOverall, overall, outcome)
            next = next.copy(solo = next.solo + (key to (next.solo[key] ?: Record()).add(outcome)), soloOverall = overall)
        } else {
            // Streak medals go to people on this device: profiles, not LAN guests.
            for ((ref, mark) in listOf(result.x to Mark.X, result.o to Mark.O)) {
                if (ref.id.startsWith("lan:") || ref.id.startsWith("cpu:")) continue
                val before = next.players[ref.id] ?: Record()
                val outcome = outcomeFor(mark, winner)
                award(ref.id, ref.name, before, before.add(outcome), outcome)
            }
            val x = result.x
            val o = result.o
            val players = next.players +
                (x.id to (next.players[x.id] ?: Record()).add(outcomeFor(Mark.X, winner))) +
                (o.id to (next.players[o.id] ?: Record()).add(outcomeFor(Mark.O, winner)))
            val key = HeadToHead.key(x.id, o.id)
            val (a, b) = if (x.id < o.id) x to o else o to x
            val aMark = if (a === x) Mark.X else Mark.O
            val h = (next.headToHead[key] ?: HeadToHead(a.id, b.id, a.name, b.name))
                .copy(aName = a.name, bName = b.name)
                .let {
                    when (winner) {
                        null -> it.copy(draws = it.draws + 1)
                        aMark -> it.copy(aWins = it.aWins + 1)
                        else -> it.copy(bWins = it.bWins + 1)
                    }
                }
            next = next.copy(
                players = players,
                playerNames = next.playerNames + (x.id to x.name) + (o.id to o.name),
                headToHead = next.headToHead + (key to h),
            )
        }
        next = checkBlocksUnlock(next)
        if (awards.isNotEmpty()) {
            var medals = next.medals
            for (a in awards) {
                val owner = medals[a.ownerId].orEmpty()
                medals = medals + (a.ownerId to (owner + (a.medal.name to (owner[a.medal.name] ?: 0) + 1)))
            }
            next = next.copy(medals = medals, awards = (awards + next.awards).take(AWARD_LIMIT))
        }
        return next
    }

    const val AWARD_LIMIT = 100

    private fun checkBlocksUnlock(data: StatsData): StatsData =
        if (!data.isUnlocked(ArcadeGame.BLOCKS) && data.bestLocalStreak >= Unlocks.BLOCKS_STREAK) data.copy(unlocked = data.unlocked + ArcadeGame.BLOCKS)
        else data

    /** A finished Blocks round. Three wins in a row unlocks Word Hive. */
    fun recordBlocks(data: StatsData, won: Boolean, score: Int, lines: Int): StatsData {
        val b = data.blocks
        val streak = if (won) b.streak + 1 else 0
        val blocks = b.copy(
            wins = b.wins + if (won) 1 else 0,
            losses = b.losses + if (won) 0 else 1,
            streak = streak,
            bestStreak = maxOf(b.bestStreak, streak),
            bestScore = maxOf(b.bestScore, score),
            totalLines = b.totalLines + lines,
        )
        var next = data.copy(blocks = blocks)
        if (!next.isUnlocked(ArcadeGame.HIVE) && streak >= Unlocks.HIVE_STREAK) next = next.copy(unlocked = next.unlocked + ArcadeGame.HIVE)
        return next
    }

    /** Words found in Word Hive; [levelCleared] moves you to the next level. */
    fun recordHive(data: StatsData, points: Int, words: Int, levelCleared: Boolean): StatsData {
        val h = data.hive
        val level = if (levelCleared) h.level + 1 else h.level
        return data.copy(
            hive = h.copy(
                level = level,
                points = (h.points + points).coerceAtLeast(0),
                bestLevel = maxOf(h.bestLevel, if (levelCleared) h.level else h.bestLevel),
                wordsFound = h.wordsFound + words,
            )
        )
    }

    /** A duel won by a player on this phone. */
    fun recordLudoDuel(data: StatsData): StatsData =
        data.copy(coins = data.coins + LudoRewards.DUEL_COINS, ludo = data.ludo.copy(duelsWon = data.ludo.duelsWon + 1))

    /** A finished Ludo game from this phone's point of view. */
    fun recordLudoGame(data: StatsData, won: Boolean): StatsData = data.copy(
        coins = data.coins + LudoRewards.PLAY_COINS + if (won) LudoRewards.WIN_COINS else 0,
        gems = data.gems + if (won) LudoRewards.WIN_GEMS else 0,
        ludo = data.ludo.copy(played = data.ludo.played + 1, wins = data.ludo.wins + if (won) 1 else 0),
    )

    fun newlyUnlocked(before: StatsData, after: StatsData): List<ArcadeGame> = after.unlocked - before.unlocked.toSet()

    /**
     * Data saved before streak medals existed has no overall solo record; rebuild it (streaks
     * included) by replaying solo history. Medals are not awarded retroactively.
     */
    fun backfill(data: StatsData): StatsData {
        if (data.soloOverall.played > 0 || data.solo.isEmpty()) return data
        val overall = data.history.filter { it.mode == GameMode.SOLO }.asReversed().fold(Record()) { rec, m ->
            // Solo history always has the human as X.
            rec.add(when (m.result) { "X" -> Outcome.WIN; "DRAW" -> Outcome.DRAW; else -> Outcome.LOSS })
        }
        return checkBlocksUnlock(data.copy(soloOverall = overall))
    }

    /** Awards produced by the round recorded at [timestamp]. */
    fun awardsAt(data: StatsData, timestamp: Long): List<MedalAward> = data.awards.filter { it.timestamp == timestamp }

    fun outcomeFor(mark: Mark, winner: Mark?): Outcome = when (winner) {
        null -> Outcome.DRAW
        mark -> Outcome.WIN
        else -> Outcome.LOSS
    }
}

/**
 * Scoreboard, profiles and match history, stored as one JSON file in app-private storage.
 * Writes go to a temp file first and are renamed into place so a crash can't corrupt it.
 */
class StatsRepository(context: Context, private val scope: CoroutineScope) {

    private val file = File(context.filesDir, "stats.json")
    private val mutex = Mutex()
    private val loaded = CompletableDeferred<Unit>()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    private val _stats = MutableStateFlow(StatsData())
    val stats: StateFlow<StatsData> = _stats.asStateFlow()

    init {
        scope.launch(Dispatchers.IO) {
            mutex.withLock {
                val loaded = runCatching { json.decodeFromString<StatsData>(file.readText()) }.getOrNull()
                _stats.value = withDefaultProfiles(StatsReducer.backfill(loaded ?: StatsData()))
            }
            this@StatsRepository.loaded.complete(Unit)
        }
    }

    private val _newAwards = MutableSharedFlow<List<MedalAward>>(extraBufferCapacity = 8)
    /** Medals earned by the round just recorded — used for the in-game celebration. */
    val newAwards: SharedFlow<List<MedalAward>> = _newAwards.asSharedFlow()

    private val _newUnlocks = MutableSharedFlow<List<ArcadeGame>>(extraBufferCapacity = 4)
    /** Arcade games unlocked by the latest result — celebrated like medals. */
    val newUnlocks: SharedFlow<List<ArcadeGame>> = _newUnlocks.asSharedFlow()

    fun record(result: MatchResult) = update { old ->
        StatsReducer.record(old, result).also { next ->
            val fresh = StatsReducer.awardsAt(next, result.timestamp)
            if (fresh.isNotEmpty()) _newAwards.tryEmit(fresh)
            emitUnlocks(old, next)
        }
    }

    fun recordBlocks(won: Boolean, score: Int, lines: Int) = update { old ->
        StatsReducer.recordBlocks(old, won, score, lines).also { emitUnlocks(old, it) }
    }

    fun recordLudoDuel() = update { StatsReducer.recordLudoDuel(it) }
    fun recordLudoGame(won: Boolean) = update { StatsReducer.recordLudoGame(it, won) }

    fun useDemo(game: ArcadeGame) = update { if (game in it.demosUsed) it else it.copy(demosUsed = it.demosUsed + game) }

    fun recordHive(points: Int, words: Int, levelCleared: Boolean) = update { StatsReducer.recordHive(it, points, words, levelCleared) }

    private fun emitUnlocks(old: StatsData, next: StatsData) {
        val unlocked = StatsReducer.newlyUnlocked(old, next)
        if (unlocked.isNotEmpty()) _newUnlocks.tryEmit(unlocked)
    }

    fun addProfile(name: String, color: Long): Profile {
        val profile = Profile(UUID.randomUUID().toString(), name.trim().take(16), color)
        update { it.copy(profiles = it.profiles + profile, playerNames = it.playerNames + (profile.id to profile.name)) }
        return profile
    }

    fun updateProfile(profile: Profile) = update { data ->
        data.copy(
            profiles = data.profiles.map { if (it.id == profile.id) profile else it },
            playerNames = data.playerNames + (profile.id to profile.name),
        )
    }

    fun deleteProfile(id: String) = update { data -> data.copy(profiles = data.profiles.filterNot { it.id == id }) }

    /** Clears scores, history and medals — but games you've unlocked stay unlocked. */
    fun resetScores() = update { StatsData(profiles = it.profiles, playerNames = it.playerNames, unlocked = it.unlocked, hive = it.hive, demosUsed = it.demosUsed, coins = it.coins, gems = it.gems) }

    private fun update(transform: (StatsData) -> StatsData) {
        scope.launch(Dispatchers.IO) {
            loaded.await()
            mutex.withLock {
                val next = transform(_stats.value)
                _stats.value = next
                runCatching {
                    val tmp = File(file.parentFile, "${file.name}.tmp")
                    tmp.writeText(json.encodeToString(StatsData.serializer(), next))
                    if (!tmp.renameTo(file)) {
                        file.delete()
                        tmp.renameTo(file)
                    }
                }
            }
        }
    }

    private fun withDefaultProfiles(data: StatsData): StatsData =
        if (data.profiles.isNotEmpty()) data
        else {
            val p1 = Profile(UUID.randomUUID().toString(), "Player 1", 0xFFFF3D7F)
            val p2 = Profile(UUID.randomUUID().toString(), "Player 2", 0xFF33E1FF)
            data.copy(profiles = listOf(p1, p2), playerNames = data.playerNames + (p1.id to p1.name) + (p2.id to p2.name))
        }
}
