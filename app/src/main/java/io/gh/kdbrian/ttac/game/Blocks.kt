package io.gh.kdbrian.ttac.game

import kotlin.math.pow
import kotlin.random.Random

enum class Tetromino(private val rows: List<String>) {
    I(listOf("....", "XXXX", "....", "....")),
    O(listOf("XX", "XX")),
    T(listOf(".X.", "XXX", "...")),
    S(listOf(".XX", "XX.", "...")),
    Z(listOf("XX.", ".XX", "...")),
    J(listOf("X..", "XXX", "...")),
    L(listOf("..X", "XXX", "..."));

    val box: Int get() = rows.size

    private val rotations: List<List<Pair<Int, Int>>> by lazy {
        val base = rows.flatMapIndexed { y, row -> row.mapIndexedNotNull { x, c -> if (c == 'X') x to y else null } }
        generateSequence(base) { cells -> cells.map { (x, y) -> (box - 1 - y) to x } }.take(4).toList()
    }

    /** Cell offsets (x, y) inside the piece's box for rotation [rot] (0–3, clockwise). */
    fun cells(rot: Int): List<Pair<Int, Int>> = if (this == O) rotations[0] else rotations[((rot % 4) + 4) % 4]
}

/**
 * Board size, starting speed and a line milestone. Play is endless — a run ends only when the
 * stack tops out — and a run that passed its milestone counts as a win.
 */
enum class BlocksDifficulty(val label: String, val width: Int, val height: Int, val gravityMs: Long, val goalLines: Int) {
    EASY("Easy", 8, 16, 760, 8),
    MEDIUM("Medium", 10, 20, 520, 12),
    HARD("Hard", 12, 22, 340, 18),
}

/**
 * Falling-block puzzle state. Pure logic: the screen calls [tick] on a timer and the move
 * functions from gestures, then redraws. Cells hold 0 for empty or `Tetromino.ordinal + 1`.
 */
class BlocksGame(val difficulty: BlocksDifficulty, private val random: Random = Random.Default) {
    val width = difficulty.width
    val height = difficulty.height
    val grid = IntArray(width * height)

    var piece: Tetromino = Tetromino.T; private set
    var rot = 0; private set
    var px = 0; private set
    var py = 0; private set

    private val bag = ArrayDeque<Tetromino>()
    val next: Tetromino get() { refill(); return bag.first() }

    var score = 0; private set
    var lines = 0; private set
    /** The stack topped out: the run is finished. */
    var over = false; private set
    /** The line milestone was passed at some point during this run. */
    var won = false; private set

    /** Rows cleared by the most recent lock, for the flash animation; [clearCount] changes each time. */
    var lastCleared: List<Int> = emptyList(); private set
    var clearCount = 0; private set
    /** Bumped on every lock, so the UI can bounce the landing piece. */
    var lockCount = 0; private set

    val level: Int get() = 1 + lines / 4

    /** Speeds up about 12% per level. */
    val gravityMs: Long get() = (difficulty.gravityMs * 0.88.pow(level - 1)).toLong().coerceAtLeast(80)

    init { spawn() }

    operator fun get(x: Int, y: Int): Int = grid[y * width + x]

    private fun refill() {
        if (bag.isEmpty()) bag.addAll(Tetromino.entries.shuffled(random))
    }

    fun cellsOf(type: Tetromino = piece, r: Int = rot, x: Int = px, y: Int = py): List<Pair<Int, Int>> =
        type.cells(r).map { (cx, cy) -> (x + cx) to (y + cy) }

    fun fits(type: Tetromino, r: Int, x: Int, y: Int): Boolean = cellsOf(type, r, x, y).all { (cx, cy) ->
        cx in 0 until width && cy < height && (cy < 0 || grid[cy * width + cx] == 0)
    }

    private fun spawn() {
        refill()
        piece = bag.removeFirst()
        refill()
        rot = 0
        px = (width - piece.box) / 2
        py = -piece.cells(0).minOf { it.second }
        if (!fits(piece, rot, px, py)) over = true
    }

    fun move(dx: Int): Boolean {
        if (over || !fits(piece, rot, px + dx, py)) return false
        px += dx
        return true
    }

    /** Rotates clockwise, nudging sideways or up if the plain rotation collides. */
    fun rotate(): Boolean {
        if (over) return false
        val r = (rot + 1) % 4
        for ((kx, ky) in listOf(0 to 0, -1 to 0, 1 to 0, -2 to 0, 2 to 0, 0 to -1)) {
            if (fits(piece, r, px + kx, py + ky)) {
                rot = r; px += kx; py += ky
                return true
            }
        }
        return false
    }

    /** Gravity step: fall one row or lock in place. */
    fun tick() {
        if (over) return
        if (fits(piece, rot, px, py + 1)) py++ else lock()
    }

    fun softDrop(): Boolean {
        if (over) return false
        return if (fits(piece, rot, px, py + 1)) { py++; score += 1; true } else { lock(); false }
    }

    /** Drops straight down and locks. Returns rows fallen. */
    fun hardDrop(): Int {
        if (over) return 0
        val d = ghostY() - py
        py += d
        score += d * 2
        lock()
        return d
    }

    fun ghostY(): Int {
        var y = py
        while (fits(piece, rot, px, y + 1)) y++
        return y
    }

    private fun lock() {
        val cells = cellsOf()
        if (cells.any { it.second < 0 }) {
            over = true
            return
        }
        for ((x, y) in cells) grid[y * width + x] = piece.ordinal + 1
        lockCount++
        val full = (0 until height).filter { y -> (0 until width).all { x -> grid[y * width + x] != 0 } }
        if (full.isNotEmpty()) {
            val remaining = (0 until height).filter { it !in full }
            val copy = grid.copyOf()
            grid.fill(0)
            // Pack the surviving rows to the bottom.
            remaining.reversed().forEachIndexed { i, y ->
                val dest = height - 1 - i
                System.arraycopy(copy, y * width, grid, dest * width, width)
            }
            score += LINE_SCORES[full.size.coerceAtMost(4)] * level
            lines += full.size
            lastCleared = full
            clearCount++
            // Endless play: passing the goal is a milestone, not the end of the run.
            if (lines >= difficulty.goalLines) won = true
        }
        spawn()
    }

    companion object {
        private val LINE_SCORES = intArrayOf(0, 100, 300, 500, 800)
    }
}
