package io.gh.kdbrian.ttac.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.gh.kdbrian.ttac.data.GameMode
import io.gh.kdbrian.ttac.data.MatchResult
import io.gh.kdbrian.ttac.data.MedalAward
import io.gh.kdbrian.ttac.data.PlayerRef
import io.gh.kdbrian.ttac.data.Profile
import io.gh.kdbrian.ttac.data.Settings
import io.gh.kdbrian.ttac.data.SettingsRepository
import io.gh.kdbrian.ttac.data.StatsRepository
import io.gh.kdbrian.ttac.fx.Fx
import io.gh.kdbrian.ttac.fx.Sfx
import io.gh.kdbrian.ttac.game.Difficulty
import io.gh.kdbrian.ttac.net.LanMessage
import io.gh.kdbrian.ttac.net.LanSession
import io.gh.kdbrian.ttac.net.LanStatus
import io.gh.kdbrian.ttac.ui.theme.MarkColors
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class Screen { HOME, SOLO_SETUP, LOCAL_SETUP, LAN, GAME, SCOREBOARD, AWARDS, SETTINGS, BLOCKS, HIVE }

/** Something worth a full-screen moment. */
sealed interface Celebration {
    data class MedalWon(val award: MedalAward) : Celebration
    data class Unlocked(val game: io.gh.kdbrian.ttac.data.ArcadeGame) : Celebration
}

class AppViewModel(app: Application) : AndroidViewModel(app), MatchHost {

    private val settingsRepo = SettingsRepository(app)
    val statsRepo = StatsRepository(app, viewModelScope)
    private val fx = Fx(app)
    val lan = LanSession(app)

    val settings: StateFlow<Settings> = settingsRepo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())
    val stats = statsRepo.stats

    private val backStack = mutableStateListOf(Screen.HOME)
    val screen: Screen get() = backStack.last()
    /** True when the latest navigation went deeper, used to pick the transition direction. */
    var forward by mutableStateOf(true); private set

    var match by mutableStateOf<Match?>(null); private set

    /** Medals and unlocks waiting to be celebrated, oldest first. */
    val celebrations = mutableStateListOf<Celebration>()

    /** True while playing a locked game's single demo attempt — nothing is recorded. */
    var demoMode by mutableStateOf(false); private set

    /** Opens a locked arcade game for its one free try. The attempt is spent immediately. */
    fun startDemo(game: io.gh.kdbrian.ttac.data.ArcadeGame) {
        if (!stats.value.canDemo(game)) return
        statsRepo.useDemo(game)
        demoMode = true
        go(if (game == io.gh.kdbrian.ttac.data.ArcadeGame.BLOCKS) Screen.BLOCKS else Screen.HIVE)
    }

    /** 0..1 — how hot the arcade game is running; warms the backdrop. */
    var arcadeHeat by mutableStateOf(0f)

    fun dismissCelebration() {
        if (celebrations.isNotEmpty()) celebrations.removeAt(0)
    }

    init {
        viewModelScope.launch {
            settings.collect {
                fx.soundEnabled = it.sound
                fx.hapticsEnabled = it.haptics
            }
        }
        // Every device gets one lasting LAN alias the first time the app runs.
        viewModelScope.launch {
            val first = settingsRepo.settings.first()
            if (first.lanAlias == null) settingsRepo.update { it.copy(lanAlias = io.gh.kdbrian.ttac.net.Aliases.random()) }
        }
        viewModelScope.launch {
            lan.status.collect { status ->
                when (status) {
                    is LanStatus.Connected -> if (match?.config?.mode != GameMode.LAN) startLanMatch(status)
                    is LanStatus.Disconnected -> match?.takeIf { it.config.mode == GameMode.LAN }?.remoteGone = status.reason
                    else -> {}
                }
            }
        }
        viewModelScope.launch { lan.messages.collect { msg -> match?.onRemote(msg) } }
        viewModelScope.launch {
            statsRepo.newAwards.collect { awards ->
                // Let the win line finish burning before the medal drops in.
                kotlinx.coroutines.delay(1400)
                celebrations.addAll(awards.map { Celebration.MedalWon(it) })
                fx.play(Sfx.MEDAL)
            }
        }
        viewModelScope.launch {
            statsRepo.newUnlocks.collect { games ->
                kotlinx.coroutines.delay(1600)
                celebrations.addAll(games.map { Celebration.Unlocked(it) })
                fx.play(Sfx.UNLOCK)
            }
        }
    }

    // ---- Navigation -------------------------------------------------------------------------

    fun go(screen: Screen) {
        fx.play(Sfx.TAP)
        forward = true
        backStack.add(screen)
    }

    fun back(): Boolean {
        if (backStack.size <= 1) return false
        forward = false
        val leaving = backStack.removeAt(backStack.lastIndex)
        if (leaving == Screen.GAME) endMatch()
        if (leaving == Screen.BLOCKS || leaving == Screen.HIVE) {
            arcadeHeat = 0f
            demoMode = false
        }
        if (leaving == Screen.LAN) lan.close()
        return true
    }

    private fun replaceTop(screen: Screen) {
        forward = true
        backStack[backStack.lastIndex] = screen
    }

    // ---- Settings & profiles ----------------------------------------------------------------

    fun updateSettings(transform: (Settings) -> Settings) {
        viewModelScope.launch { settingsRepo.update(transform) }
    }

    fun addProfile(name: String, color: Long): Profile = statsRepo.addProfile(name.ifBlank { "Player" }, color)

    fun deleteProfile(id: String) = statsRepo.deleteProfile(id)

    fun resetScores() = statsRepo.resetScores()

    fun tapSound() = fx.play(Sfx.TAP)

    /** Opens an unlocked arcade game straight from its celebration. */
    fun openArcade(game: io.gh.kdbrian.ttac.data.ArcadeGame) {
        dismissCelebration()
        demoMode = false
        if (match != null) back()
        go(if (game == io.gh.kdbrian.ttac.data.ArcadeGame.BLOCKS) Screen.BLOCKS else Screen.HIVE)
    }

    // ---- Starting matches -------------------------------------------------------------------

    fun startSolo(difficulty: Difficulty, size: Int) {
        val s = settings.value
        updateSettings { it.copy(difficulty = difficulty, boardSize = size) }
        val you = PlayerRef("you", "You", s.xColor)
        val cpu = PlayerRef("cpu:${difficulty.name}", "CPU · ${difficulty.label}", s.oColor)
        begin(MatchConfig(GameMode.SOLO, size, Seat(you, SeatKind.HUMAN), Seat(cpu, SeatKind.AI), difficulty))
        replaceTop(Screen.GAME)
    }

    fun startLocal(x: Profile, o: Profile, size: Int) {
        updateSettings { it.copy(lastXProfileId = x.id, lastOProfileId = o.id, boardSize = size) }
        val (xc, oc) = distinctColors(x.color, o.color)
        begin(
            MatchConfig(
                GameMode.LOCAL, size,
                Seat(PlayerRef(x.id, x.name, xc), SeatKind.HUMAN),
                Seat(PlayerRef(o.id, o.name, oc), SeatKind.HUMAN),
            )
        )
        replaceTop(Screen.GAME)
    }

    fun hostLan(me: Profile, size: Int) {
        updateSettings { it.copy(lanProfileId = me.id, boardSize = size) }
        lanMe = me
        lan.host(myAlias(), me.name, me.color, size)
    }

    fun joinLan(me: Profile, host: io.gh.kdbrian.ttac.net.LanHost) = join(me, host.address, host.port, host.alias)

    /** Join by a typed session code; returns false if the code doesn't decode. */
    fun joinLanByCode(me: Profile, code: String, alias: String? = null): Boolean {
        val (address, port) = io.gh.kdbrian.ttac.net.SessionCode.decode(code) ?: return false
        join(me, address, port, alias ?: "session ${code.uppercase()}")
        return true
    }

    fun myAlias(): String = settings.value.lanAlias ?: "Mystery Player"

    private fun join(me: Profile, address: String, port: Int, alias: String) {
        updateSettings { it.copy(lanProfileId = me.id) }
        lanMe = me
        lan.join(address, port, alias, myAlias(), me.name, me.color)
    }

    private var lanMe: Profile? = null

    private fun startLanMatch(status: LanStatus.Connected) {
        val me = lanMe ?: return
        if (screen != Screen.LAN) return
        fx.play(Sfx.CONNECT)
        // LAN members are remembered by their alias, so their history and streaks carry over.
        val remoteKey = status.remoteAlias.ifBlank { status.remoteName }
        val remote = PlayerRef("lan:$remoteKey", status.remoteName, status.remoteColor)
        val local = PlayerRef(me.id, me.name, me.color)
        val (x, o) = if (status.isHost) local to remote else remote to local
        val (xc, oc) = distinctColors(x.color, o.color)
        val xSeat = Seat(x.copy(color = xc), if (status.isHost) SeatKind.HUMAN else SeatKind.REMOTE)
        val oSeat = Seat(o.copy(color = oc), if (status.isHost) SeatKind.REMOTE else SeatKind.HUMAN)
        begin(MatchConfig(GameMode.LAN, status.boardSize, xSeat, oSeat))
        replaceTop(Screen.GAME)
    }

    /** Keeps the two players' marks distinguishable even if their profiles share a colour. */
    private fun distinctColors(x: Long, o: Long): Pair<Long, Long> =
        if (x != o) x to o else x to (MarkColors.firstOrNull { it != x } ?: o)

    private fun begin(config: MatchConfig) {
        match?.dispose()
        match = Match(config, viewModelScope, this)
    }

    private fun endMatch() {
        val m = match ?: return
        m.dispose()
        match = null
        if (m.config.mode == GameMode.LAN) lan.close()
    }

    // ---- MatchHost ------------------------------------------------------------------------

    override fun play(sfx: Sfx) = fx.play(sfx)
    override fun record(result: MatchResult) = statsRepo.record(result)
    override fun send(message: LanMessage) = lan.send(message)

    override fun onCleared() {
        lan.close()
        fx.release()
    }
}
