# 19. Pause, save & resume 🔴

Players get interrupted — a call, a notification, the home button. TTac notices, pauses, saves the game in progress,
and offers to pick it up exactly where it was.

## Detecting the pause

The app observes its own lifecycle once, at the root:

```kotlin
val observer = LifecycleEventObserver { _, event ->
    if (event == Lifecycle.Event.ON_PAUSE) vm.onAppPaused()
}
```

`ON_PAUSE` fires for everything that takes focus away: switching apps, the notification shade, turning the screen
off, an incoming call. `onAppPaused()` does two things:

1. Bumps `pauseSignal`. Blocks watches it and pauses its gravity loop, so pieces don't keep falling while you're
   away.
2. Saves a snapshot of whatever game is showing.

## What a snapshot is

A `SavedSession` is a sealed, serialisable type — one case per game:

| Game | Saved |
|---|---|
| Tic-Tac-Toe | seats (human/AI), board, whose turn, round, session score |
| Blocks | the well, active piece & rotation, the upcoming bag, score, lines |
| Word Hive | every letter's hex, each hidden word's path, found words, extras, hints |
| Ludo | the whole `LudoState` (it's already serialisable — the LAN sends it) |

Game engines expose their state for this: `BlocksGame.snapshot()` / `BlocksGame.restore()`, `Match.restore(...)`.
A unit test restores a Blocks run and then plays both copies forward with the same inputs to prove they stay
identical.

Screens that own their game state (Blocks, Word Hive) **register a saver** — a lambda the ViewModel calls when it's
time to save — and unregister it when they leave. The ViewModel snapshots Tic-Tac-Toe and Ludo itself.

The session is written like the stats file: temp file, then an atomic rename, so a kill mid-save can't corrupt it.
It survives the process being killed in the background.

## Resuming

The home screen shows a **Continue** card whenever a session is saved. Tapping it restores the game and opens its
screen; Blocks comes back *paused* so you're never dropped into a falling piece. Leaving a game with back also saves
it, so "quit to menu" never loses progress.

Two cases aren't saved: **LAN games** (the other phones won't be there when you come back) and **finished games**
(nothing to resume).

## Going further

- `ON_PAUSE` vs `ON_STOP`: pausing on `ON_PAUSE` catches the notification shade and split-screen focus changes too,
  which matters for a real-time game like Blocks.
- Anything you'd send over the network or save to disk wants to be a plain serialisable value. Designing `LudoState`
  that way for the LAN made saving it free.
