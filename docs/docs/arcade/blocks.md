# 14. Blocks — a falling-block engine 🟠

Blocks (`game/Blocks.kt` + `ui/screens/BlocksScreen.kt`) is an endless falling-block game: pieces fall, you move and
rotate them, full rows clear, and it speeds up until the stack reaches the top.

![Blocks, captured from a live demo run](../images/screen-blocks.png){ width="40%" }

The capture above is a real run: the demo drops straight into play, and the first piece (a yellow O) is falling
toward its ghost outline — the hollow squares at the floor show exactly where a hard drop would land it. Note the layout — the well sits directly on the animated backdrop, edge to edge, with a single HUD row
(back · score · lines · level · next piece · pause) and a slim control strip.

## Representing pieces

Each tetromino is a tiny ASCII grid, and rotations are computed, not typed out:

```kotlin
T(listOf(".X.", "XXX", "..."))

private val rotations by lazy {
    val base = rows.flatMapIndexed { y, row -> row.mapIndexedNotNull { x, c -> if (c == 'X') x to y else null } }
    generateSequence(base) { cells -> cells.map { (x, y) -> (box - 1 - y) to x } }.take(4).toList()
}
```

`(x, y) → (box − 1 − y, x)` rotates a cell 90° clockwise inside its bounding box (remember y points *down*). Applying
it four times returns to the start — a property the unit tests check for every piece. `O` never rotates.

## The well

The well is a flat `IntArray(width × height)`: `0` is empty, otherwise `piece.ordinal + 1` (so the colour of settled
blocks is remembered). The active piece is *not* in the array; it's `(piece, rot, px, py)` and is drawn on top.

## Collision is one function

```kotlin
fun fits(type: Tetromino, r: Int, x: Int, y: Int) = cellsOf(type, r, x, y).all { (cx, cy) ->
    cx in 0 until width && cy < height && (cy < 0 || grid[cy * width + cx] == 0)
}
```

Moving, rotating, falling and the ghost piece all ask one question: *would the piece fit there?* Cells above the top
(`cy < 0`) are allowed so pieces can spawn partly hidden.

### Wall kicks

A rotation next to a wall or another block often collides. Instead of refusing, try nudging it:

```kotlin
for ((kx, ky) in listOf(0 to 0, -1 to 0, 1 to 0, -2 to 0, 2 to 0, 0 to -1)) {
    if (fits(piece, r, px + kx, py + ky)) { rot = r; px += kx; py += ky; return true }
}
```

## Fair randomness: the 7-bag

Pure random pieces can deal four S pieces in a row. A **bag** shuffles all seven pieces and deals them before
reshuffling, so droughts are impossible and the next piece is always known (`next` is the head of the bag).

## Locking and clearing

When a piece can't fall further it **locks**: its cells are written into the grid, full rows are found, and the
surviving rows are packed to the bottom with `System.arraycopy`. Scoring is `100 / 300 / 500 / 800 × level` for 1–4
rows, plus 1 per soft-dropped row and 2 per hard-dropped row.

## Endless play and speed

Runs never end on a goal — only when a new piece can't spawn. The difficulty sets the well and pace:

| | Well | Start gravity | Win milestone |
|---|---|---|---|
| Easy | 8 × 16 | 760 ms | 8 lines |
| Medium | 10 × 20 | 520 ms | 12 lines |
| Hard | 12 × 22 | 340 ms | 18 lines |

Level rises every 4 lines and gravity shrinks 12% per level: `gravityMs = base × 0.88^(level−1)`. Passing the
milestone flags the run as a win (it counts toward unlocking Word Hive) and shows a toast, but play continues.

## The game loop

```kotlin
LaunchedEffect(g, paused) {
    while (!g.over && !paused) {
        delay(g.gravityMs)
        g.tick()          // fall one row, or lock
        refresh()         // bump `frame` so the board redraws; detect clears for flash & sound
    }
}
```

The engine mutates plain fields, so the screen keeps a `frame` counter as the redraw signal.

## Smooth motion over a grid

The engine lives on a grid, but the screen doesn't have to jump: the active piece's *drawn* position is two
Animatables that chase `px`/`py` — a stiff spring horizontally and a short tween vertically — and snap whenever a new
piece spawns (`lockCount` changed). The piece glides between cells while the rules stay exact.

## Feel

- **Ghost piece** — outlined at `ghostY()`, where a hard drop would land.
- **Line-clear flash** — white bands where rows vanished, with sparks drifting outward.
- **Thump** — a hard drop springs the whole well down 6dp.
- **Danger glow** — red creeps down from the top when the stack is within five rows.
- **Heat** — the backdrop warms with level via `arcadeHeat`, and the floor line heats toward the milestone.

## Going further

- `BlocksTest` includes a greedy bot that evaluates every rotation and column (fewest holes, lowest stack) and plays
  until it passes the Easy milestone — an end-to-end test of the whole engine with no UI.
- Real Tetris guidelines add SRS kick tables, lock delay and hold; the engine's `fits()` design makes each a small
  addition.
