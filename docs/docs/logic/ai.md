# 11. Game AI — minimax to alpha-beta 🟠

TTac's CPU (`game/Ai.kt`) ranges from clumsy to unbeatable. This chapter builds it up the way you'd write it.

## Step 1 — minimax

Game trees alternate between you (maximising your score) and the opponent (minimising it). Minimax scores a position
by assuming both sides play perfectly:

```text
score(position, toMove):
    if someone won: return ±WIN
    if board full: return 0
    return max over moves of −score(position after move, other player)
```

That last line is the **negamax** form: one function for both players, because the opponent's best is our worst.

## Step 2 — prefer fast wins

A win in 1 move and a win in 5 moves both score `WIN` — so the AI might dawdle. Subtracting depth (`ply`) fixes it:

```kotlin
if (win != null) return if (win.mark == toMove) WIN - ply else -(WIN - ply)
```

Now it wins as fast as possible and, when losing, **delays defeat** as long as possible.

## Step 3 — alpha-beta pruning

Most branches can be skipped. Alpha is the best score we're already guaranteed; beta is the best the opponent will
allow. If a move is already worse for the opponent than what they can get elsewhere, stop looking at its siblings:

```kotlin
for (move in orderedMoves(board)) {
    val score = -negamax(board.play(move, toMove), toMove.other, depth - 1, ply + 1, -beta, -alpha)
    if (score > best) best = score
    if (best > alpha) alpha = best
    if (alpha >= beta) break          // ✂ prune
}
```

Pruning works best when good moves are tried first, so moves are **ordered centre-out** (centre squares are part of
more lines). On 3×3, the full game tree becomes trivial; the unit test `hard never loses to random play` plays 300
games in under a second.

## Step 4 — bigger boards need a heuristic

A 5×5 tree is far too big to search to the end. The search stops at a **depth limit** (5 on 4×4, 3 on 5×5) and scores
the position with an evaluation function:

```kotlin
for (line in lines) {
    if (mine > 0 && theirs == 0) score += 10^mine     // an open line I'm building
    else if (theirs > 0 && mine == 0) score -= 10^theirs
}
```

Lines only one player occupies are still winnable; the exponent makes three-in-an-open-line worth far more than
three separate singles.

## Step 5 — difficulty is about mistakes

| Level | Behaviour |
|---|---|
| Easy | Random moves; spots an immediate win only 35% of the time |
| Medium | Always takes wins and blocks; otherwise 45% search, else a centre-weighted random move |
| Hard | Full search; when the opponent threatens, only blocking moves are searched |

!!! info "Why Hard restricts itself to blocks"
    Facing a double threat, every move loses at the same depth, so a plain search picks any move — including ones that
    look like giving up. Searching only the blocking moves is strictly correct (a non-block loses next turn anyway) and
    makes the CPU resist like a person would.

## Going further

- **Transposition tables** (memoising positions by hash) would speed up 4×4/5×5 considerably.
- **Iterative deepening** with a time budget gives the best move found so far when time runs out.
- The AI runs on `Dispatchers.Default` and its result is discarded if the round or board changed meanwhile.
