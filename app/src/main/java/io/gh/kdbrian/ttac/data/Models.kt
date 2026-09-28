package io.gh.kdbrian.ttac.data

import io.gh.kdbrian.ttac.game.Difficulty
import kotlinx.serialization.Serializable

@Serializable
data class Profile(
    val id: String,
    val name: String,
    /** ARGB colour used for this player's marks. */
    val color: Long,
)

@Serializable
data class Record(
    val wins: Int = 0,
    val losses: Int = 0,
    val draws: Int = 0,
    /** Current run of consecutive wins. */
    val streak: Int = 0,
    val bestStreak: Int = 0,
    /** Current run of consecutive losses. */
    val lossStreak: Int = 0,
    val worstLossStreak: Int = 0,
) {
    val played: Int get() = wins + losses + draws
    val winRate: Float get() = if (played == 0) 0f else wins.toFloat() / played

    /** A draw ends both kinds of streak. */
    fun add(outcome: Outcome): Record = when (outcome) {
        Outcome.WIN -> copy(wins = wins + 1, streak = streak + 1, bestStreak = maxOf(bestStreak, streak + 1), lossStreak = 0)
        Outcome.LOSS -> copy(losses = losses + 1, streak = 0, lossStreak = lossStreak + 1, worstLossStreak = maxOf(worstLossStreak, lossStreak + 1))
        Outcome.DRAW -> copy(draws = draws + 1, streak = 0, lossStreak = 0)
    }
}

enum class Outcome { WIN, LOSS, DRAW }

enum class GameMode(val label: String) {
    SOLO("Solo"),
    LOCAL("Pass & Play"),
    LAN("LAN"),
}

/** Head-to-head tally between two players; [aId] < [bId] so each pair has one entry. */
@Serializable
data class HeadToHead(
    val aId: String,
    val bId: String,
    val aName: String,
    val bName: String,
    val aWins: Int = 0,
    val bWins: Int = 0,
    val draws: Int = 0,
) {
    val played: Int get() = aWins + bWins + draws

    companion object {
        fun key(id1: String, id2: String): String = if (id1 < id2) "$id1|$id2" else "$id2|$id1"
    }
}

@Serializable
data class MatchRecord(
    val timestamp: Long,
    val mode: GameMode,
    val size: Int,
    val xName: String,
    val oName: String,
    val xColor: Long,
    val oColor: Long,
    /** "X", "O" or "DRAW". */
    val result: String,
    /** Final position, e.g. "XO.X.O..." */
    val board: String,
    val winCells: List<Int> = emptyList(),
    val difficulty: Difficulty? = null,
    val xId: String = "",
    val oId: String = "",
)

/** Per-board-size tallies used to render the heatmap of where games are won and played. */
@Serializable
data class HeatData(
    val games: Int = 0,
    val winCounts: List<Int> = emptyList(),
    val playCounts: List<Int> = emptyList(),
) {
    fun normalizedWins(cells: Int): FloatArray = normalize(winCounts, cells)
    fun normalizedPlays(cells: Int): FloatArray = normalize(playCounts, cells)

    private fun normalize(counts: List<Int>, cells: Int): FloatArray {
        val max = counts.maxOrNull()?.takeIf { it > 0 } ?: return FloatArray(cells)
        return FloatArray(cells) { i -> (counts.getOrNull(i) ?: 0).toFloat() / max }
    }
}

/**
 * Medals are handed out every [Medals.GAP] games of a streak. Win streaks climb five tiers
 * (Diamond repeats past the top), losing streaks earn Grit badges, and snapping a losing
 * streak of at least one gap earns a Comeback.
 */
enum class Medal(val title: String, val kind: MedalKind, val threshold: Int) {
    BRONZE("Bronze Streak", MedalKind.WIN, Medals.GAP * 1),
    SILVER("Silver Streak", MedalKind.WIN, Medals.GAP * 2),
    GOLD("Gold Streak", MedalKind.WIN, Medals.GAP * 3),
    PLATINUM("Platinum Streak", MedalKind.WIN, Medals.GAP * 4),
    DIAMOND("Diamond Streak", MedalKind.WIN, Medals.GAP * 5),
    GRIT_I("Grit I", MedalKind.LOSS, Medals.GAP * 1),
    GRIT_II("Grit II", MedalKind.LOSS, Medals.GAP * 2),
    GRIT_III("Grit III", MedalKind.LOSS, Medals.GAP * 3),
    COMEBACK("Comeback", MedalKind.COMEBACK, Medals.GAP),
    ;

    val description: String
        get() = when (kind) {
            MedalKind.WIN -> if (this == DIAMOND) "Win $threshold in a row — and every ${Medals.GAP} after" else "Win $threshold in a row"
            MedalKind.LOSS -> "Lose $threshold in a row and keep playing"
            MedalKind.COMEBACK -> "Win right after losing $threshold or more in a row"
        }
}

enum class MedalKind { WIN, LOSS, COMEBACK }

object Medals {
    /** The predefined gap between streak medals. */
    const val GAP = 3

    /** Medals earned by moving from [before] to [after] with [outcome]. */
    fun earned(before: Record, after: Record, outcome: Outcome): List<Medal> = buildList {
        when (outcome) {
            Outcome.WIN -> {
                val s = after.streak
                Medal.entries.firstOrNull { it.kind == MedalKind.WIN && it.threshold == s }?.let(::add)
                if (s > Medal.DIAMOND.threshold && s % GAP == 0) add(Medal.DIAMOND)
                if (before.lossStreak >= Medal.COMEBACK.threshold) add(Medal.COMEBACK)
            }
            Outcome.LOSS -> {
                val s = after.lossStreak
                Medal.entries.firstOrNull { it.kind == MedalKind.LOSS && it.threshold == s }?.let(::add)
                if (s > Medal.GRIT_III.threshold && s % GAP == 0) add(Medal.GRIT_III)
            }
            Outcome.DRAW -> {}
        }
    }

    /** The next win-streak medal to chase from a current streak. */
    fun nextWin(streak: Int): Pair<Medal, Int> {
        val next = Medal.entries.firstOrNull { it.kind == MedalKind.WIN && it.threshold > streak }
        return if (next != null) next to next.threshold else Medal.DIAMOND to ((streak / GAP) + 1) * GAP
    }

    fun nextGrit(lossStreak: Int): Pair<Medal, Int> {
        val next = Medal.entries.firstOrNull { it.kind == MedalKind.LOSS && it.threshold > lossStreak }
        return if (next != null) next to next.threshold else Medal.GRIT_III to ((lossStreak / GAP) + 1) * GAP
    }
}

/** Bonus games unlocked by playing well. */
enum class ArcadeGame(val title: String) {
    BLOCKS("Blocks"),
    HIVE("Word Hive"),
}

object Unlocks {
    /** A win streak this long (solo or any profile) unlocks Blocks. */
    const val BLOCKS_STREAK = 10
    /** This many Blocks wins in a row unlocks Word Hive. */
    const val HIVE_STREAK = 3
}

@Serializable
data class BlocksStats(
    val wins: Int = 0,
    val losses: Int = 0,
    val streak: Int = 0,
    val bestStreak: Int = 0,
    val bestScore: Int = 0,
    val totalLines: Int = 0,
)

@Serializable
data class HiveStats(
    /** The level you'll play next. */
    val level: Int = 1,
    val points: Int = 0,
    val bestLevel: Int = 0,
    val wordsFound: Int = 0,
)

@Serializable
data class MedalAward(
    val medal: Medal,
    val ownerId: String,
    val ownerName: String,
    val streak: Int,
    val timestamp: Long,
)

@Serializable
data class StatsData(
    val profiles: List<Profile> = emptyList(),
    /** Personal record against the AI, keyed by [Difficulty.name]. */
    val solo: Map<String, Record> = emptyMap(),
    /** Overall multiplayer record for each player id (profiles and LAN opponents). */
    val players: Map<String, Record> = emptyMap(),
    val playerNames: Map<String, String> = emptyMap(),
    val headToHead: Map<String, HeadToHead> = emptyMap(),
    val history: List<MatchRecord> = emptyList(),
    /** Keyed by board size ("3", "4", "5"). */
    val heat: Map<String, HeatData> = emptyMap(),
    /** Your solo record across every difficulty — drives your personal streak medals. */
    val soloOverall: Record = Record(),
    /** Medal counts per owner ("you" or a profile id), keyed by [Medal.name]. */
    val medals: Map<String, Map<String, Int>> = emptyMap(),
    /** Newest first. */
    val awards: List<MedalAward> = emptyList(),
    val unlocked: List<ArcadeGame> = emptyList(),
    /** Locked games whose one free demo attempt has been used. */
    val demosUsed: List<ArcadeGame> = emptyList(),
    val blocks: BlocksStats = BlocksStats(),
    val hive: HiveStats = HiveStats(),
) {
    fun isUnlocked(game: ArcadeGame) = game in unlocked
    fun canDemo(game: ArcadeGame) = !isUnlocked(game) && game !in demosUsed

    /** Longest win streak by anyone playing on this device (you solo, or a profile). */
    val bestLocalStreak: Int
        get() = maxOf(soloOverall.bestStreak, players.filterKeys { !it.startsWith("lan:") && !it.startsWith("cpu:") }.values.maxOfOrNull { it.bestStreak } ?: 0)

    fun medalCount(ownerId: String, medal: Medal): Int = medals[ownerId]?.get(medal.name) ?: 0
    fun streakRecord(ownerId: String): Record = if (ownerId == SOLO_OWNER) soloOverall else players[ownerId] ?: Record()

    companion object {
        const val SOLO_OWNER = "you"
    }

    fun soloRecord(difficulty: Difficulty): Record = solo[difficulty.name] ?: Record()
    fun heatFor(size: Int): HeatData = heat[size.toString()] ?: HeatData()
}
