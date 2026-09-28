# 10. Rules as pure Kotlin 🟠

`game/Board.kt` knows nothing about screens, colours or touch. That's deliberate: pure rules are easy to test,
reuse (the AI, the UI, the network layer and the stats all use the same `Board`), and reason about.

## An immutable board

```kotlin
class Board private constructor(val size: Int, private val cells: Array<Mark?>) {
    operator fun get(index: Int): Mark? = cells[index]
    fun play(index: Int, mark: Mark): Board {
        require(cells[index] == null) { "Cell $index is taken" }
        return Board(size, cells.copyOf().also { it[index] = mark })   // new board, old one untouched
    }
}
```

`play` returns a **new** board. Immutability means:

- The AI can explore millions of futures without undoing moves.
- Compose sees a new object → knows to redraw.
- "Was this move made on the board I think it was?" becomes `board == snapshot` (used to discard stale AI moves).

## Generating win lines once

A 3×3 board needs 3 in a row; 4×4 and 5×5 need 4. Rather than special-casing sizes, `Rules` generates every
**contiguous run of `winLength` cells** in four directions — right, down, down-right, down-left — once per size and
caches them:

```kotlin
val directions = listOf(0 to 1, 1 to 0, 1 to 1, 1 to -1)
for (r in 0 until n) for (c in 0 until n) for ((dr, dc) in directions) {
    val endR = r + dr * (winLength - 1); val endC = c + dc * (winLength - 1)
    if (endR !in 0 until n || endC !in 0 until n) continue
    result += IntArray(winLength) { k -> (r + dr * k) * n + (c + dc * k) }
}
```

| Board | Win length | Lines |
|---|---|---|
| 3×3 | 3 | 8 |
| 4×4 | 4 | 10 |
| 5×5 | 4 | 28 |

!!! bug "A real bug this caught"
    The first version built this list inside `buildList { }`. Inside that lambda, `size` refers to the *list being
    built* (0), not the board — so no lines were generated and nobody could ever win. The build compiled; the unit
    tests caught it immediately. Scoping receivers in Kotlin lambdas are powerful and occasionally treacherous.

## Winner, threats, draws

- **Winner**: the first line whose cells are all the same non-null mark.
- **Threats** for a mark: lines with `winLength − 1` of that mark and exactly one empty cell → that empty cell.
  Used by the AI (win/block) and the threat-heat glow.
- **Draw**: the board is full, *or* no line can ever be completed (every line already holds both marks). The early
  draw saves players from pointless moves on big boards.

## Going further

- `encode()` / `decode()` turn a board into `"XO.X.O..."` — handy for tests and for storing history thumbnails.
- The rules are symmetrical in X and O, so one `threats(mark)` serves attack and defence.
