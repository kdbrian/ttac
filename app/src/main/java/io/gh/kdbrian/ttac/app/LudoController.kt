package io.gh.kdbrian.ttac.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.gh.kdbrian.ttac.fx.Sfx
import io.gh.kdbrian.ttac.game.Difficulty
import io.gh.kdbrian.ttac.game.Ludo
import io.gh.kdbrian.ttac.game.LudoAction
import io.gh.kdbrian.ttac.game.LudoBot
import io.gh.kdbrian.ttac.game.LudoColor
import io.gh.kdbrian.ttac.game.LudoPhase
import io.gh.kdbrian.ttac.game.LudoSeat
import io.gh.kdbrian.ttac.game.LudoState
import io.gh.kdbrian.ttac.game.SeatKind
import io.gh.kdbrian.ttac.net.LudoLink
import io.gh.kdbrian.ttac.net.LudoLinkStatus
import io.gh.kdbrian.ttac.net.LudoWire
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Runs a Ludo table on this phone.
 *
 * - **Host** (local play or LAN host): owns the [LudoState], applies every action through [Ludo.apply], drives CPU
 *   seats, and broadcasts each new state to guests.
 * - **Guest** (LAN): mirrors the host's state and sends intents; the host decides what's legal.
 */
class LudoController(
    private val scope: CoroutineScope,
    private val link: LudoLink,
    private val fx: (Sfx) -> Unit,
    private val onDuelWon: () -> Unit,
    private val onGameOver: (won: Boolean) -> Unit,
    private val random: Random = Random.Default,
) {
    var isGuest by mutableStateOf(false); private set
    /** This phone's device number: 0 for the host, 1..3 for guests. */
    var device by mutableStateOf(0); private set

    /** Seating plan while in the lobby. */
    var seats by mutableStateOf(defaultSeats("You")); private set
    var difficulty by mutableStateOf(Difficulty.MEDIUM)
    var hostAlias by mutableStateOf("")
        private set

    var state by mutableStateOf<LudoState?>(null); private set

    private var cpuJob: Job? = null
    private var rewarded = false

    // ---- Lobby (host side) ------------------------------------------------------------------

    fun setSeat(color: LudoColor, kind: SeatKind, name: String? = null) {
        if (isGuest || state != null) return
        seats = seats.map {
            if (it.color != color) it else it.copy(
                kind = kind,
                name = name ?: when (kind) {
                    SeatKind.CPU -> "CPU ${color.label}"
                    SeatKind.REMOTE -> "Waiting…"
                    SeatKind.EMPTY -> "Empty"
                    SeatKind.LOCAL -> it.name.takeUnless { n -> n.startsWith("CPU") || n == "Empty" || n == "Waiting…" } ?: "Player ${color.number}"
                },
                device = if (kind == SeatKind.REMOTE) it.device.takeIf { d -> d > 0 } ?: 0 else 0,
            )
        }
        publishLobby()
    }

    fun rename(color: LudoColor, name: String) {
        seats = seats.map { if (it.color == color) it.copy(name = name.take(16)) else it }
        publishLobby()
    }

    /** Guests who connect fill open LAN seats in clockwise order; leavers free their seat again. */
    fun onGuestsChanged(guests: List<io.gh.kdbrian.ttac.net.LudoGuest>) {
        if (isGuest || state != null) return
        var s = seats.map { seat -> if (seat.kind == SeatKind.REMOTE && guests.none { it.device == seat.device }) seat.copy(device = 0, name = "Waiting…") else seat }
        for (g in guests) {
            if (s.any { it.kind == SeatKind.REMOTE && it.device == g.device }) continue
            val open = s.indexOfFirst { it.kind == SeatKind.REMOTE && it.device == 0 }
            if (open < 0) continue
            s = s.toMutableList().also { it[open] = it[open].copy(device = g.device, name = g.name, avatarColor = g.color) }
        }
        seats = s
        publishLobby()
    }

    val canStart: Boolean
        get() = seats.count { it.kind != SeatKind.EMPTY } >= 2 && seats.none { it.kind == SeatKind.REMOTE && it.device == 0 }

    fun start() {
        if (isGuest || !canStart) return
        rewarded = false
        link.stopAdvertising()
        publish(Ludo.newGame(seats, difficulty, random))
    }

    /** Resumes a saved local game (no LAN seats) exactly where it was. */
    fun restore(saved: LudoState) {
        reset(saved.seats.firstOrNull { it.kind == SeatKind.LOCAL }?.name ?: "You")
        seats = saved.seats
        difficulty = saved.difficulty
        rewarded = false
        publish(saved)
    }

    /** Only local tables can be resumed — a LAN table's other phones are gone. */
    val resumable: Boolean
        get() = !isGuest && state?.let { s -> s.phase != LudoPhase.OVER && s.seats.none { it.kind == SeatKind.REMOTE } } == true

    /** Same table, same seats, fresh game. */
    fun rematch() {
        if (isGuest) return
        val same = state?.seats ?: return
        rewarded = false
        seats = same
        publish(Ludo.newGame(same, difficulty, random))
    }

    private fun publishLobby() {
        if (!isGuest) link.broadcast(LudoWire.Lobby(seats, hostAlias))
    }

    fun beginHosting(alias: String, myName: String) {
        hostAlias = alias
        val open = seats.count { it.kind == SeatKind.REMOTE }
        link.host(alias, myName, maxOf(1, open))
    }

    fun reset(myName: String) {
        cpuJob?.cancel()
        state = null
        isGuest = false
        device = 0
        seats = defaultSeats(myName)
        rewarded = false
    }

    // ---- Guest side -------------------------------------------------------------------------

    fun becomeGuest() {
        isGuest = true
        state = null
    }

    /** Handles a message from the network ([from] = sending device; 0 = host). */
    fun onWire(from: Int, m: LudoWire) {
        when (m) {
            is LudoWire.Welcome -> device = m.device
            is LudoWire.Lobby -> if (isGuest) { seats = m.seats; hostAlias = m.hostAlias }
            is LudoWire.State -> if (isGuest && (state?.version ?: -1) < m.state.version) { announce(state, m.state); state = m.state; maybeReward(m.state) }
            is LudoWire.Act -> if (!isGuest) {
                val s = state ?: return
                // A guest may only act for a LAN seat it owns.
                val seat = s.seat(m.action.by)
                if (seat.kind != SeatKind.REMOTE || seat.device != from) return
                Ludo.apply(s, m.action, random)?.let(::publish)
            }
            else -> {}
        }
    }

    // ---- Playing ----------------------------------------------------------------------------

    /** Whether this phone decides for [color] (its own human seats). */
    fun controls(color: LudoColor): Boolean {
        val seat = (state?.seats ?: seats).firstOrNull { it.color == color } ?: return false
        return if (isGuest) seat.kind == SeatKind.REMOTE && seat.device == device
        else seat.kind == SeatKind.LOCAL
    }

    /** The action a human on this phone takes. */
    fun act(action: LudoAction) {
        if (!controls(action.by)) return
        if (isGuest) {
            link.sendTo(0, LudoWire.Act(action))
            return
        }
        val s = state ?: return
        Ludo.apply(s, action, random)?.let(::publish)
    }

    private fun publish(next: LudoState) {
        val before = state
        state = next
        announce(before, next)
        link.broadcast(LudoWire.State(next))
        maybeReward(next)
        scheduleCpu(next)
    }

    /** Sounds for what just happened, plus duel-win rewards for this phone's players. */
    private fun announce(before: LudoState?, next: LudoState) {
        if (before == null) return
        when {
            next.phase == LudoPhase.OVER && before.phase != LudoPhase.OVER -> fx(Sfx.WIN)
            next.lastEvent.contains("captures") && next.lastEvent != before.lastEvent -> fx(Sfx.QUAD)
            next.phase == LudoPhase.ROLL && before.phase == LudoPhase.DUEL -> {
                fx(Sfx.MEDAL)
                if (next.roller != null && controls(next.roller)) onDuelWon()
            }
            next.die != null && before.die == null -> fx(Sfx.DROP)
            next.duel?.board != before.duel?.board -> fx(Sfx.PLACE_X)
            next.pawns != before.pawns -> fx(Sfx.MOVE)
        }
    }

    private fun maybeReward(s: LudoState) {
        if (s.phase != LudoPhase.OVER || rewarded) return
        rewarded = true
        val mine = s.seats.filter { controls(it.color) }
        if (mine.isNotEmpty()) onGameOver(s.winner != null && controls(s.winner))
    }

    /** CPU seats think for a beat, then act — duels move faster than rolls so they stay watchable. */
    private fun scheduleCpu(s: LudoState) {
        cpuJob?.cancel()
        val actor = s.actor ?: return
        if (s.seat(actor).kind != SeatKind.CPU) return
        cpuJob = scope.launch {
            delay(
                when (s.phase) {
                    LudoPhase.DUEL -> 450
                    LudoPhase.ROLL -> 700
                    LudoPhase.MOVE -> 900
                    else -> 800
                }
            )
            val now = state ?: return@launch
            if (now.version != s.version) return@launch
            LudoBot.act(now, random)?.let { a -> Ludo.apply(now, a, random)?.let(::publish) }
        }
    }

    fun dispose() {
        cpuJob?.cancel()
    }

    val linkStatus get() = link.status

    companion object {
        fun defaultSeats(myName: String) = listOf(
            LudoSeat(LudoColor.BLUE, SeatKind.LOCAL, myName),
            LudoSeat(LudoColor.RED, SeatKind.CPU, "CPU Red"),
            LudoSeat(LudoColor.GREEN, SeatKind.CPU, "CPU Green"),
            LudoSeat(LudoColor.YELLOW, SeatKind.CPU, "CPU Yellow"),
        )
    }
}

/** Convenience: is the link currently hosting a table? */
val LudoLinkStatus.isHosting: Boolean get() = this is LudoLinkStatus.Hosting
