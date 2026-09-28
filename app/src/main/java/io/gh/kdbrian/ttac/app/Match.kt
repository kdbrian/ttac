package io.gh.kdbrian.ttac.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.gh.kdbrian.ttac.data.GameMode
import io.gh.kdbrian.ttac.data.MatchResult
import io.gh.kdbrian.ttac.data.PlayerRef
import io.gh.kdbrian.ttac.fx.Sfx
import io.gh.kdbrian.ttac.game.Ai
import io.gh.kdbrian.ttac.game.Board
import io.gh.kdbrian.ttac.game.Difficulty
import io.gh.kdbrian.ttac.game.Mark
import io.gh.kdbrian.ttac.game.WinLine
import io.gh.kdbrian.ttac.net.LanMessage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

enum class SeatKind { HUMAN, AI, REMOTE }

data class Seat(val player: PlayerRef, val kind: SeatKind)

data class MatchConfig(
    val mode: GameMode,
    val size: Int,
    val x: Seat,
    val o: Seat,
    val difficulty: Difficulty? = null,
) {
    fun seat(mark: Mark) = if (mark == Mark.X) x else o

    /** The mark this device's user plays, when there is exactly one local human. */
    val localMark: Mark? = when {
        x.kind == SeatKind.HUMAN && o.kind != SeatKind.HUMAN -> Mark.X
        o.kind == SeatKind.HUMAN && x.kind != SeatKind.HUMAN -> Mark.O
        else -> null
    }
}

/** Side effects the match needs, injected so it can be tested without Android. */
interface MatchHost {
    fun play(sfx: Sfx)
    fun record(result: MatchResult)
    fun send(message: LanMessage)
}

/**
 * State for one sitting: a series of rounds between the same two seats, with a running session
 * score. Rounds alternate who starts. Compose reads the fields directly.
 */
class Match(
    val config: MatchConfig,
    private val scope: CoroutineScope,
    private val host: MatchHost,
    private val aiDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val aiDelayMs: Long = 420,
    private val random: Random = Random.Default,
) {
    var board by mutableStateOf(Board.empty(config.size)); private set
    var turn by mutableStateOf(Mark.X); private set
    var win by mutableStateOf<WinLine?>(null); private set
    var isDraw by mutableStateOf(false); private set
    var round by mutableIntStateOf(1); private set
    var xWins by mutableIntStateOf(0); private set
    var oWins by mutableIntStateOf(0); private set
    var draws by mutableIntStateOf(0); private set
    var aiThinking by mutableStateOf(false); private set
    var localRematch by mutableStateOf(false); private set
    var remoteRematch by mutableStateOf(false); private set
    var remoteGone by mutableStateOf<String?>(null)

    private var aiJob: Job? = null

    val isOver: Boolean get() = win != null || isDraw

    val canTap: Boolean
        get() = !isOver && !aiThinking && remoteGone == null && config.seat(turn).kind == SeatKind.HUMAN

    fun starterOf(round: Int): Mark = if (round % 2 == 1) Mark.X else Mark.O

    init {
        turn = starterOf(1)
        maybeRunAi()
    }

    fun tap(index: Int) {
        if (!canTap || board[index] != null) return
        val mark = turn
        apply(index, mark)
        if (config.mode == GameMode.LAN) host.send(LanMessage.Move(round, index))
    }

    /** "Play again": immediate locally, but both sides must agree over LAN. */
    fun requestNextRound() {
        if (!isOver) return
        if (config.mode != GameMode.LAN) return startNextRound()
        if (localRematch) return
        localRematch = true
        host.send(LanMessage.Rematch(round))
        if (remoteRematch) startNextRound()
    }

    fun onRemote(message: LanMessage) {
        when (message) {
            is LanMessage.Move -> {
                // Peers are untrusted: only accept a legal move for the remote seat in this round.
                if (message.round != round || isOver) return
                if (config.seat(turn).kind != SeatKind.REMOTE) return
                if (message.index !in 0 until board.cellCount || board[message.index] != null) return
                apply(message.index, turn)
            }
            is LanMessage.Rematch -> {
                if (message.round != round || !isOver) return
                remoteRematch = true
                if (localRematch) startNextRound()
            }
            else -> {}
        }
    }

    private fun apply(index: Int, mark: Mark) {
        board = board.play(index, mark)
        host.play(if (mark == Mark.X) Sfx.PLACE_X else Sfx.PLACE_O)
        val w = board.winner()
        when {
            w != null -> finish(w)
            board.isDraw() -> finish(null)
            else -> {
                turn = mark.other
                maybeRunAi()
            }
        }
    }

    private fun finish(w: WinLine?) {
        win = w
        isDraw = w == null
        when (w?.mark) {
            Mark.X -> xWins++
            Mark.O -> oWins++
            null -> draws++
        }
        val local = config.localMark
        host.play(
            when {
                w == null -> Sfx.DRAW
                local == null || w.mark == local -> Sfx.WIN
                else -> Sfx.LOSE
            }
        )
        host.record(
            MatchResult(
                mode = config.mode,
                board = board,
                win = w,
                x = config.x.player,
                o = config.o.player,
                humanMark = if (config.mode == GameMode.SOLO) local else null,
                difficulty = config.difficulty,
            )
        )
    }

    private fun startNextRound() {
        aiJob?.cancel()
        round++
        board = Board.empty(config.size)
        win = null
        isDraw = false
        localRematch = false
        remoteRematch = false
        aiThinking = false
        turn = starterOf(round)
        maybeRunAi()
    }

    private fun maybeRunAi() {
        if (isOver || config.seat(turn).kind != SeatKind.AI) return
        val difficulty = config.difficulty ?: Difficulty.MEDIUM
        val me = turn
        val snapshot = board
        val thisRound = round
        aiThinking = true
        aiJob = scope.launch {
            val started = System.nanoTime()
            val move = withContext(aiDispatcher) { Ai.chooseMove(snapshot, me, difficulty, random) }
            val elapsedMs = (System.nanoTime() - started) / 1_000_000
            delay((aiDelayMs + random.nextLong(0, aiDelayMs / 2 + 1) - elapsedMs).coerceAtLeast(0))
            if (round == thisRound && board == snapshot) {
                aiThinking = false
                apply(move, me)
            }
        }
    }

    /** Puts a saved sitting back exactly as it was, then lets the AI continue if it was its move. */
    fun restore(board: Board, turn: Mark, round: Int, xWins: Int, oWins: Int, draws: Int) {
        aiJob?.cancel()
        this.board = board
        this.turn = turn
        this.round = round
        this.xWins = xWins
        this.oWins = oWins
        this.draws = draws
        win = board.winner()
        isDraw = win == null && board.isDraw()
        aiThinking = false
        maybeRunAi()
    }

    fun dispose() {
        aiJob?.cancel()
    }
}
