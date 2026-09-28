package io.gh.kdbrian.ttac.game

enum class Mark {
    X, O;

    val other: Mark get() = if (this == X) O else X
}

/** A completed line: the cell indices in board order, and who owns them. */
data class WinLine(val cells: List<Int>, val mark: Mark)

/**
 * Line geometry for an N×N board. 3×3 needs three in a row; larger boards need four,
 * which keeps 4×4 and 5×5 winnable without being trivial.
 */
class Rules private constructor(val size: Int) {
    val winLength: Int = if (size <= 3) size else 4
    val cellCount: Int = size * size

    /** Every contiguous run of [winLength] cells horizontally, vertically and diagonally. */
    val lines: List<IntArray> = run {
        // Not inside buildList: its receiver has its own `size`, which would shadow the board's.
        val n = size
        val result = ArrayList<IntArray>()
        val directions = listOf(0 to 1, 1 to 0, 1 to 1, 1 to -1)
        for (r in 0 until n) for (c in 0 until n) {
            for ((dr, dc) in directions) {
                val endR = r + dr * (winLength - 1)
                val endC = c + dc * (winLength - 1)
                if (endR !in 0 until n || endC !in 0 until n) continue
                result += IntArray(winLength) { k -> (r + dr * k) * n + (c + dc * k) }
            }
        }
        result
    }

    companion object {
        val SUPPORTED_SIZES = listOf(3, 4, 5)
        private val cache = HashMap<Int, Rules>()

        fun of(size: Int): Rules {
            require(size in SUPPORTED_SIZES) { "Unsupported board size $size" }
            return synchronized(cache) { cache.getOrPut(size) { Rules(size) } }
        }
    }
}

/** Immutable board snapshot. */
class Board private constructor(val size: Int, private val cells: Array<Mark?>) {

    val rules: Rules get() = Rules.of(size)
    val cellCount: Int get() = cells.size

    operator fun get(index: Int): Mark? = cells[index]

    fun toList(): List<Mark?> = cells.toList()

    val emptyCells: List<Int> get() = cells.indices.filter { cells[it] == null }

    val isFull: Boolean get() = cells.none { it == null }

    val isEmpty: Boolean get() = cells.all { it == null }

    fun play(index: Int, mark: Mark): Board {
        require(cells[index] == null) { "Cell $index is taken" }
        return Board(size, cells.copyOf().also { it[index] = mark })
    }

    fun winner(): WinLine? {
        for (line in rules.lines) {
            val first = cells[line[0]] ?: continue
            if (line.all { cells[it] == first }) return WinLine(line.toList(), first)
        }
        return null
    }

    /** Whether the game has ended in a draw: full board, or no line can still be won by anyone. */
    fun isDraw(): Boolean {
        if (winner() != null) return false
        if (isFull) return true
        return rules.lines.none { line ->
            val marks = line.map { cells[it] }.filterNotNull().toSet()
            marks.size < 2
        }
    }

    /** Empty cells that would immediately complete a line for [mark]. */
    fun threats(mark: Mark): Set<Int> {
        val result = HashSet<Int>()
        for (line in rules.lines) {
            var mine = 0
            var empty = -1
            var emptyCount = 0
            for (i in line) {
                when (cells[i]) {
                    mark -> mine++
                    null -> { empty = i; emptyCount++ }
                    else -> {}
                }
            }
            if (mine == line.size - 1 && emptyCount == 1) result += empty
        }
        return result
    }

    /** Compact serialised form, e.g. "XO.X.O..." — used for history thumbnails. */
    fun encode(): String = cells.joinToString("") { it?.name ?: "." }

    override fun equals(other: Any?): Boolean =
        other is Board && other.size == size && other.cells.contentEquals(cells)

    override fun hashCode(): Int = 31 * size + cells.contentHashCode()

    override fun toString(): String = encode().chunked(size).joinToString("\n")

    companion object {
        fun empty(size: Int): Board = Board(size, arrayOfNulls(Rules.of(size).cellCount))

        fun decode(encoded: String): Board {
            val size = kotlin.math.sqrt(encoded.length.toDouble()).toInt()
            require(size * size == encoded.length) { "Not a square board: $encoded" }
            return Board(size, Array(encoded.length) { i ->
                when (encoded[i]) {
                    'X' -> Mark.X
                    'O' -> Mark.O
                    else -> null
                }
            })
        }
    }
}
