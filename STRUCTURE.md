# Project Structure

Current layout, `dev.fitnesstimer` package. Update this file as the
structure keeps changing; treat it as a living map, not a spec to satisfy
exactly.

```
fitness-timer/
├── README.md
├── PLAN.md
├── STRUCTURE.md
├── DECISIONS.md
└── app/
    ├── build.gradle.kts
    └── src/main/
        ├── AndroidManifest.xml
        ├── res/values/strings.xml
        └── kotlin/dev/fitnesstimer/
            ├── MainActivity.kt
            ├── gesture/
            │   └── TimerGestures.kt      # unified pointerInput state machine (section E)
            ├── timer/
            │   └── TimerEngine.kt        # elapsedRealtime-based, stopwatch only so far (section F)
            ├── render/
            │   ├── NegativeTimerText.kt  # Difference-blend overlay + hold-to-reset ring (section H)
            │   └── AmbientColor.kt       # one-time Palette dominant-color sample for the letterbox
            └── ui/
                └── MainScreen.kt         # wires timer + gestures + Media3 + render together
```

Notes:
- No `media/`, `data/`, `network/`, or `repository/` layers yet — local
  playback is currently inlined in `MainScreen.kt` (a single `ExoPlayer` +
  `ActivityResultContracts.OpenDocument()` picker, no persisted-URI
  handling across restarts, no `MediaState` abstraction). Splitting this out
  into `media/local/` + `media/session/` + a shared `MediaState` is still
  open — becomes necessary once MediaSession integration (roadmap step 6)
  starts, since that's when two source kinds need the same shape.
- No `state/` (single `ApplicationState` holder) yet either — `MainScreen`
  currently owns `TimerEngine`, the `ExoPlayer`, and UI state directly via
  `remember`. Fine at this size; revisit if it gets unwieldy.
- (`media/online/` — dropped from v1, 2026-09-20 — see `DECISIONS.md`.)
- Package name: `dev.fitnesstimer` (decided implicitly by starting the
  project; revisit before any real release if it matters).
