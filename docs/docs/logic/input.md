# 13. Input & gestures 🟠

Games need more than `onClick`. TTac uses Compose's low-level pointer API for taps, drags, flicks and path tracing.

## Levels of the pointer API

| API | Use |
|---|---|
| `Modifier.clickable` | Simple buttons (TTac avoids it to control the bounce) |
| `detectTapGestures` / `detectDragGestures` | Common gestures — the board's taps, sliders |
| `awaitEachGesture { awaitFirstDown(); awaitPointerEvent() … }` | Full control — Blocks, Word Hive, bouncy press |

## Blocks: one gesture, four actions

```kotlin
awaitEachGesture {
    val down = awaitFirstDown()
    val tracker = VelocityTracker()
    while (true) {
        val change = awaitPointerEvent().changes.first { it.id == down.id }
        if (!change.pressed) break
        accX += change.positionChange().x
        while (abs(accX) >= cellPx * 0.9f) { move(sign(accX)); accX -= sign(accX) * cellPx * 0.9f }  // drag = move
        while (accY >= cellPx) { softDrop(); accY -= cellPx }                                        // drag down = soft drop
        tracker.addPosition(change.uptimeMillis, change.position)
    }
    when {
        tracker.calculateVelocity().y > 2600f -> hardDrop()   // flick down = hard drop
        !moved -> rotate()                                     // tap = rotate
    }
}
```

Accumulating movement and converting whole cells at a time gives precise, cell-snapped control however fast the
finger moves. `VelocityTracker` distinguishes a flick from a slow drag.

## Word Hive: tracing a path through hexagons

Each pointer move finds the nearest hex centre; a cell only counts once the finger is **well inside** it (72% of the
radius), so diagonal drags don't clip neighbours. Then:

- a new cell adjacent to the last one → **extend** the path;
- the second-to-last cell → **backtrack** (undo a step);
- anything else → ignore.

Release submits the path. See [Word Hive](../arcade/hive.md) for the hex maths.

## Going further

- `change.consume()` stops parent scroll containers from stealing the gesture.
- Semantics (`Role.Button`, `contentDescription`) keep Canvas UIs accessible and testable.
