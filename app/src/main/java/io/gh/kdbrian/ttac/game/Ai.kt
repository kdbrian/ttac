package io.gh.kdbrian.ttac.game

import kotlin.math.abs
import kotlin.random.Random

enum class Difficulty(val label: String) {
    EASY("Easy"),
    MEDIUM("Medium"),
    HARD("Hard"),
}

/**
 * Computer opponent.
 * - Easy: mostly random, sometimes spots an immediate win.
 * - Medium: always takes wins and blocks, otherwise mixes search with random play.
 * - Hard: alpha-beta search — perfect on 3×3, depth-limited with a line heuristic on bigger boards.
 */
object Ai {

    fun chooseMove(board: Board, me: Mark, difficulty: Difficulty, random: Random = Random.Default): Int {
        val empty = board.emptyCells
        require(empty.isNotEmpty()) { "No moves left" }
        if (empty.size == 1) return empty.first()

        val winNow = board.threats(me)
        val blockNow = board.threats(me.other)

        return when (difficulty) {
            Difficulty.EASY -> when {
                winNow.isNotEmpty() && random.nextFloat() < 0.35f -> winNow.random(random)
                else -> empty.random(random)
            }

            Difficulty.MEDIUM -> when {
                winNow.isNotEmpty() -> winNow.random(random)
                blockNow.isNotEmpty() -> blockNow.random(random)
                random.nextFloat() < 0.45f -> search(board, me, depthFor(board.size) - 1, random)
                else -> preferCentre(board, empty, random)
            }

            Difficulty.HARD -> when {
                winNow.isNotEmpty() -> winNow.first()
                // Any move that doesn't block loses next turn, so only search the blocks — this also
                // makes the AI resist a double threat instead of shrugging at a lost position.
                blockNow.isNotEmpty() -> search(board, me, depthFor(board.size), random, blockNow.toList())
                else -> search(board, me, depthFor(board.size), random)
            }
        }
    }

    private fun depthFor(size: Int) = when (size) {
        3 -> 9
        4 -> 5
        else -> 3
    }

    private fun preferCentre(board: Board, empty: List<Int>, random: Random): Int {
        val mid = (board.size - 1) / 2f
        val weights = empty.map { i ->
            val r = i / board.size
            val c = i % board.size
            1f / (1f + abs(r - mid) + abs(c - mid))
        }
        var pick = random.nextFloat() * weights.sum()
        empty.forEachIndexed { k, cell ->
            pick -= weights[k]
            if (pick <= 0f) return cell
        }
        return empty.last()
    }

    private const val WIN = 1_000_000

    private fun search(board: Board, me: Mark, maxDepth: Int, random: Random, candidates: List<Int>? = null): Int {
        val moves = candidates ?: orderedMoves(board, random)
        var bestScore = Int.MIN_VALUE
        var best = moves.first()
        var alpha = -WIN * 2
        val beta = WIN * 2
        for (move in moves) {
            val score = -negamax(board.play(move, me), me.other, maxDepth - 1, 1, -beta, -alpha)
            if (score > bestScore) {
                bestScore = score
                best = move
            }
            alpha = maxOf(alpha, score)
        }
        return best
    }

    /** Score from the perspective of [toMove]. Faster wins and slower losses score better. */
    private fun negamax(board: Board, toMove: Mark, depth: Int, ply: Int, alphaIn: Int, beta: Int): Int {
        val win = board.winner()
        if (win != null) return if (win.mark == toMove) WIN - ply else -(WIN - ply)
        if (board.isFull) return 0
        if (depth <= 0) return evaluate(board, toMove)

        var alpha = alphaIn
        var best = -WIN * 2
        for (move in orderedMoves(board, null)) {
            val score = -negamax(board.play(move, toMove), toMove.other, depth - 1, ply + 1, -beta, -alpha)
            if (score > best) best = score
            if (best > alpha) alpha = best
            if (alpha >= beta) break
        }
        return best
    }

    /** Open-line heuristic: lines only one side occupies are worth exponentially more per mark. */
    internal fun evaluate(board: Board, perspective: Mark): Int {
        var score = 0
        for (line in board.rules.lines) {
            var mine = 0
            var theirs = 0
            for (i in line) when (board[i]) {
                perspective -> mine++
                null -> {}
                else -> theirs++
            }
            if (mine > 0 && theirs == 0) score += POW10[mine]
            else if (theirs > 0 && mine == 0) score -= POW10[theirs]
        }
        return score
    }

    private val POW10 = intArrayOf(0, 1, 10, 100, 1000, 10000)

    /** Centre-out ordering makes alpha-beta prune far more; a shuffle breaks ties for variety. */
    private fun orderedMoves(board: Board, random: Random?): List<Int> {
        val mid = (board.size - 1) / 2f
        val empty = board.emptyCells.let { if (random != null) it.shuffled(random) else it }
        return empty.sortedBy { i -> abs(i / board.size - mid) + abs(i % board.size - mid) }
    }
}
