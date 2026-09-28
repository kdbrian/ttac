package io.gh.kdbrian.ttac.data

import android.content.Context
import io.gh.kdbrian.ttac.game.BlocksSnapshot
import io.gh.kdbrian.ttac.game.Difficulty
import io.gh.kdbrian.ttac.game.LudoState
import io.gh.kdbrian.ttac.game.Mark
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** A game in progress, saved when the app goes to the background so it can be resumed later. */
@Serializable
sealed interface SavedSession {
    val savedAt: Long

    /** A human-readable line for the "Continue" card. */
    val summary: String

    @Serializable @SerialName("ttt")
    data class TicTacToe(
        override val savedAt: Long,
        val mode: GameMode,
        val size: Int,
        val difficulty: Difficulty?,
        val xName: String, val oName: String,
        val xId: String, val oId: String,
        val xColor: Long, val oColor: Long,
        /** "HUMAN" or "AI" per seat (LAN games aren't saved). */
        val xKind: String, val oKind: String,
        val board: String,
        val turn: Mark,
        val round: Int,
        val xWins: Int, val oWins: Int, val draws: Int,
    ) : SavedSession {
        override val summary get() = "${mode.label} · ${size}×$size · round $round · $xWins–$oWins"
    }

    @Serializable @SerialName("blocks")
    data class Blocks(override val savedAt: Long, val snapshot: BlocksSnapshot) : SavedSession {
        override val summary get() = "Blocks · ${snapshot.difficulty.label} · ${snapshot.score} pts · ${snapshot.lines} lines"
    }

    @Serializable @SerialName("hive")
    data class Hive(
        override val savedAt: Long,
        val level: Int,
        val radius: Int,
        /** Hex letters as "q,r,L" triples. */
        val letters: List<String>,
        /** Hidden words with their paths as "q,r" lists. */
        val words: Map<String, List<String>>,
        val found: List<String>,
        val extras: List<String>,
        val hints: Map<String, Int>,
    ) : SavedSession {
        override val summary get() = "Word Hive · level $level · ${found.size}/${words.size} words"
    }

    @Serializable @SerialName("ludo")
    data class Ludo(override val savedAt: Long, val state: LudoState) : SavedSession {
        override val summary get() = "Ludo Palace · ${state.active.size} players · ${state.seat(state.turn).name}'s turn"
    }
}

/**
 * Persists at most one [SavedSession] — the last unfinished game — in app-private storage. Written
 * atomically, like the stats file, so a kill mid-save can't corrupt it.
 */
class SessionStore(context: Context, private val scope: CoroutineScope) {
    private val file = File(context.filesDir, "session.json")
    private val json = Json { ignoreUnknownKeys = true; classDiscriminator = "t" }
    private val mutex = Mutex()

    private val _saved = MutableStateFlow<SavedSession?>(null)
    val saved: StateFlow<SavedSession?> = _saved.asStateFlow()

    init {
        scope.launch(Dispatchers.IO) {
            _saved.value = runCatching { json.decodeFromString(SavedSession.serializer(), file.readText()) }.getOrNull()
        }
    }

    fun save(session: SavedSession) {
        _saved.value = session
        scope.launch(Dispatchers.IO) {
            mutex.withLock {
                runCatching {
                    val tmp = File(file.parentFile, "${file.name}.tmp")
                    tmp.writeText(json.encodeToString(SavedSession.serializer(), session))
                    if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
                }
            }
        }
    }

    fun clear() {
        _saved.value = null
        scope.launch(Dispatchers.IO) { mutex.withLock { file.delete() } }
    }
}
