package io.gh.kdbrian.ttac.game

import kotlinx.serialization.Serializable
import kotlin.random.Random

/**
 * Ludo on the classic cross, played for dice: on your turn you challenge someone at the table to Tic-Tac-Toe,
 * rounds repeat until someone wins, and the winner rolls the die and moves.
 *
 * Everything here is a pure reducer over [LudoState] so the same rules run on this phone, in tests and on a LAN
 * host that broadcasts state to other phones.
 */

/** Seat colours, in clockwise turn order, placed like the photo: blue TL, red TR, green BR, yellow BL. */
@Serializable
enum class LudoColor(val label: String, val argb: Long) {
    BLUE("Blue", 0xFF2F6FB5),
    RED("Red", 0xFF9E1B22),
    GREEN("Green", 0xFF2E7D32),
    YELLOW("Yellow", 0xFFE0A21B);

    /** Seat number in reading order, as players see it: 1 top-left, 2 top-right, 3 bottom-left, 4 bottom-right. */
    val number: Int get() = when (this) { BLUE -> 1; RED -> 2; YELLOW -> 3; GREEN -> 4 }

    /** First square after leaving base, as an index on the 52-square loop. */
    val start: Int get() = 1 + 13 * ordinal
}

@Serializable
enum class SeatKind { EMPTY, CPU, LOCAL, REMOTE }

@Serializable
data class LudoSeat(
    val color: LudoColor,
    val kind: SeatKind,
    val name: String,
    /** Which device controls this seat over LAN: 0 is the host; clients get 1, 2, 3. */
    val device: Int = 0,
    val avatarColor: Long = color.argb,
)

@Serializable
enum class LudoPhase { CHOOSE_OPPONENT, DUEL, ROLL, MOVE, OVER }

/** A Tic-Tac-Toe duel for the right to roll. [challenger] plays ✕ in odd rounds and ◯ in even rounds. */
@Serializable
data class LudoDuel(
    val challenger: LudoColor,
    val opponent: LudoColor,
    val board: String = ".........",
    val round: Int = 1,
    /** Whose mark moves next; ✕ always opens a round. */
    val toMove: Mark = Mark.X,
    val draws: Int = 0,
) {
    fun markOf(color: LudoColor): Mark = if ((color == challenger) == (round % 2 == 1)) Mark.X else Mark.O
    fun colorOf(mark: Mark): LudoColor = if (markOf(challenger) == mark) challenger else opponent
    val current: LudoColor get() = colorOf(toMove)
}

@Serializable
data class LudoState(
    val seats: List<LudoSeat>,
    /** Progress per pawn: −1 in base, 0..50 on the loop, 51..55 up the home lane, 56 home. */
    val pawns: Map<LudoColor, List<Int>>,
    val turn: LudoColor,
    val phase: LudoPhase = LudoPhase.CHOOSE_OPPONENT,
    val duel: LudoDuel? = null,
    /** Who won the duel (or rolled a six) and so rolls/moves now. */
    val roller: LudoColor? = null,
    val die: Int? = null,
    val movable: List<Int> = emptyList(),
    val winner: LudoColor? = null,
    val difficulty: Difficulty = Difficulty.MEDIUM,
    /** Bumped on every change; LAN clients use it to ignore stale snapshots. */
    val version: Int = 0,
    val lastEvent: String = "",
) {
    val active: List<LudoColor> get() = seats.filter { it.kind != SeatKind.EMPTY }.map { it.color }
    fun seat(c: LudoColor): LudoSeat = seats.first { it.color == c }

    /** The seat expected to act right now. */
    val actor: LudoColor?
        get() = when (phase) {
            LudoPhase.CHOOSE_OPPONENT -> turn
            LudoPhase.DUEL -> duel?.current
            LudoPhase.ROLL, LudoPhase.MOVE -> roller
            LudoPhase.OVER -> null
        }
}

/** Something a seat does. Every action names who is doing it, so the host can check they're allowed to. */
@Serializable
sealed interface LudoAction {
    val by: LudoColor

    @Serializable data class Challenge(override val by: LudoColor, val opponent: LudoColor) : LudoAction
    @Serializable data class DuelMove(override val by: LudoColor, val cell: Int) : LudoAction
    @Serializable data class Roll(override val by: LudoColor) : LudoAction
    @Serializable data class MovePawn(override val by: LudoColor, val pawn: Int) : LudoAction
}

object LudoBoard {
    const val LOOP = 52
    const val LAST_LOOP = 50
    const val HOME = 56
    const val PAWNS = 4
    const val GRID = 15

    /** The 52-square loop as (column, row) on a 15×15 grid, clockwise from the left arm. */
    val loop: List<Pair<Int, Int>> = buildList {
        for (c in 0..5) add(c to 6)          // left arm, top row →
        for (r in 5 downTo 0) add(6 to r)    // top arm, left column ↑
        add(7 to 0)                          // top middle
        for (r in 0..5) add(8 to r)          // top arm, right column ↓
        for (c in 9..14) add(c to 6)         // right arm, top row →
        add(14 to 7)                         // right middle
        for (c in 14 downTo 9) add(c to 8)   // right arm, bottom row ←
        for (r in 9..14) add(8 to r)         // bottom arm, right column ↓
        add(7 to 14)                         // bottom middle
        for (r in 14 downTo 9) add(6 to r)   // bottom arm, left column ↑
        for (c in 5 downTo 0) add(c to 8)    // left arm, bottom row ←
        add(0 to 7)                          // left middle
    }

    /** Each colour's five home-lane squares, outside in. */
    fun lane(color: LudoColor): List<Pair<Int, Int>> = when (color) {
        LudoColor.BLUE -> (1..5).map { it to 7 }
        LudoColor.RED -> (1..5).map { 7 to it }
        LudoColor.GREEN -> (13 downTo 9).map { it to 7 }
        LudoColor.YELLOW -> (13 downTo 9).map { 7 to it }
    }

    /** Squares where nobody can be captured: every start, and the square eight on from each start. */
    val safe: Set<Int> = LudoColor.entries.flatMap { listOf(it.start, (it.start + 8) % LOOP) }.toSet()

    fun loopIndex(color: LudoColor, progress: Int): Int = (color.start + progress) % LOOP

    /** Grid cell for a pawn, or null while it's in base or home. */
    fun cellOf(color: LudoColor, progress: Int): Pair<Int, Int>? = when (progress) {
        in 0..LAST_LOOP -> loop[loopIndex(color, progress)]
        in 51..55 -> lane(color)[progress - 51]
        else -> null
    }
}

object Ludo {

    fun newGame(seats: List<LudoSeat>, difficulty: Difficulty, random: Random = Random.Default): LudoState {
        require(seats.count { it.kind != SeatKind.EMPTY } >= 2) { "Ludo needs at least two players" }
        val first = seats.filter { it.kind != SeatKind.EMPTY }.random(random).color
        return LudoState(
            seats = seats,
            pawns = LudoColor.entries.associateWith { List(LudoBoard.PAWNS) { -1 } },
            turn = first,
            difficulty = difficulty,
            lastEvent = "${seats.first { it.color == first }.name} goes first",
        )
    }

    /** Applies [action] if it's legal right now; returns null (and changes nothing) otherwise. */
    fun apply(state: LudoState, action: LudoAction, random: Random = Random.Default): LudoState? {
        if (action.by != state.actor) return null
        val next = when (action) {
            is LudoAction.Challenge -> challenge(state, action)
            is LudoAction.DuelMove -> duelMove(state, action)
            is LudoAction.Roll -> roll(state, random)
            is LudoAction.MovePawn -> movePawn(state, action)
        } ?: return null
        return next.copy(version = state.version + 1)
    }

    private fun challenge(s: LudoState, a: LudoAction.Challenge): LudoState? {
        if (s.phase != LudoPhase.CHOOSE_OPPONENT || a.opponent == a.by || a.opponent !in s.active) return null
        return s.copy(
            phase = LudoPhase.DUEL,
            duel = LudoDuel(challenger = a.by, opponent = a.opponent),
            lastEvent = "${s.seat(a.by).name} challenges ${s.seat(a.opponent).name}",
        )
    }

    private fun duelMove(s: LudoState, a: LudoAction.DuelMove): LudoState? {
        val d = s.duel ?: return null
        if (s.phase != LudoPhase.DUEL || a.cell !in 0..8 || d.board[a.cell] != '.') return null
        val board = Board.decode(d.board).play(a.cell, d.toMove)
        val win = board.winner()
        return when {
            win != null -> {
                val victor = d.colorOf(win.mark)
                s.copy(
                    phase = LudoPhase.ROLL, roller = victor, die = null,
                    duel = d.copy(board = board.encode()),
                    lastEvent = "${s.seat(victor).name} wins the duel and rolls",
                )
            }
            board.isDraw() -> s.copy(
                // Draws replay with the starting mark swapped.
                duel = d.copy(board = ".........", round = d.round + 1, toMove = Mark.X, draws = d.draws + 1),
                lastEvent = "Draw — play again",
            )
            else -> s.copy(duel = d.copy(board = board.encode(), toMove = d.toMove.other))
        }
    }

    private fun roll(s: LudoState, random: Random): LudoState? {
        if (s.phase != LudoPhase.ROLL) return null
        val roller = s.roller ?: return null
        val die = random.nextInt(1, 7)
        val movable = legalPawns(s, roller, die)
        val name = s.seat(roller).name
        return if (movable.isEmpty()) {
            endTurn(s.copy(die = die, movable = emptyList(), lastEvent = "$name rolled $die — no move"))
        } else {
            s.copy(phase = LudoPhase.MOVE, die = die, movable = movable, lastEvent = "$name rolled $die")
        }
    }

    fun legalPawns(s: LudoState, color: LudoColor, die: Int): List<Int> =
        s.pawns.getValue(color).withIndex().filter { (_, p) -> target(p, die) != null }.map { it.index }

    /** Where a pawn at [progress] lands with [die], or null if it can't move. */
    fun target(progress: Int, die: Int): Int? = when {
        progress == LudoBoard.HOME -> null
        progress == -1 -> if (die == 6) 0 else null
        progress + die > LudoBoard.HOME -> null           // exact roll needed to get home
        else -> progress + die
    }

    private fun movePawn(s: LudoState, a: LudoAction.MovePawn): LudoState? {
        if (s.phase != LudoPhase.MOVE || a.pawn !in s.movable) return null
        val color = a.by
        val die = s.die ?: return null
        val from = s.pawns.getValue(color)[a.pawn]
        val to = target(from, die) ?: return null
        var pawns = s.pawns + (color to s.pawns.getValue(color).toMutableList().also { it[a.pawn] = to })
        val name = s.seat(color).name
        var event = if (to == LudoBoard.HOME) "$name brings a pawn home!" else "$name moves"

        // Capture: landing on a rival on an unsafe loop square sends them back to base.
        if (to in 0..LudoBoard.LAST_LOOP) {
            val square = LudoBoard.loopIndex(color, to)
            if (square !in LudoBoard.safe) {
                for (rival in s.active - color) {
                    val theirs = pawns.getValue(rival)
                    if (theirs.any { it in 0..LudoBoard.LAST_LOOP && LudoBoard.loopIndex(rival, it) == square }) {
                        pawns = pawns + (rival to theirs.map { if (it in 0..LudoBoard.LAST_LOOP && LudoBoard.loopIndex(rival, it) == square) -1 else it })
                        event = "$name captures ${s.seat(rival).name}!"
                    }
                }
            }
        }

        val moved = s.copy(pawns = pawns, movable = emptyList(), lastEvent = event)
        if (pawns.getValue(color).all { it == LudoBoard.HOME }) {
            return moved.copy(phase = LudoPhase.OVER, winner = color, roller = null, lastEvent = "$name wins the game!")
        }
        // A six earns another roll straight away — no duel needed.
        return if (die == 6) moved.copy(phase = LudoPhase.ROLL, die = null, lastEvent = "$event · six! roll again")
        else endTurn(moved)
    }

    private fun endTurn(s: LudoState): LudoState {
        val order = s.active
        val next = order[(order.indexOf(s.turn) + 1) % order.size]
        return s.copy(phase = LudoPhase.CHOOSE_OPPONENT, turn = next, duel = null, roller = null, movable = emptyList())
    }

    /** "Match me" — pick the rival furthest ahead, so duels keep the leader honest. */
    fun autoOpponent(s: LudoState, by: LudoColor): LudoColor =
        (s.active - by).maxByOrNull { c -> s.pawns.getValue(c).sumOf { if (it < 0) 0 else it } }!!
}

/** A CPU seat's choices. */
object LudoBot {

    fun act(s: LudoState, random: Random = Random.Default): LudoAction? {
        val me = s.actor ?: return null
        return when (s.phase) {
            LudoPhase.CHOOSE_OPPONENT -> LudoAction.Challenge(me, Ludo.autoOpponent(s, me))
            LudoPhase.DUEL -> {
                val d = s.duel ?: return null
                LudoAction.DuelMove(me, Ai.chooseMove(Board.decode(d.board), d.toMove, s.difficulty, random))
            }
            LudoPhase.ROLL -> LudoAction.Roll(me)
            LudoPhase.MOVE -> LudoAction.MovePawn(me, bestPawn(s, me))
            LudoPhase.OVER -> null
        }
    }

    /** Capture > bring home > leave base > land safe > advance the furthest pawn. */
    fun bestPawn(s: LudoState, me: LudoColor): Int {
        val die = s.die ?: return s.movable.first()
        return s.movable.maxBy { i ->
            val from = s.pawns.getValue(me)[i]
            val to = Ludo.target(from, die) ?: return@maxBy Int.MIN_VALUE
            var score = to
            if (to == LudoBoard.HOME) score += 500
            if (from == -1) score += 200
            if (to in 0..LudoBoard.LAST_LOOP) {
                val sq = LudoBoard.loopIndex(me, to)
                if (sq in LudoBoard.safe) score += 60
                val captures = (s.active - me).any { r -> s.pawns.getValue(r).any { it in 0..LudoBoard.LAST_LOOP && LudoBoard.loopIndex(r, it) == sq } }
                if (captures && sq !in LudoBoard.safe) score += 800
            }
            score
        }
    }
}
