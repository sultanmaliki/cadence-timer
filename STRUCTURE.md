# Project Structure

Planned layout — nothing exists yet (pre-code, see `PLAN.md` → Roadmap step 1
for the actual first task). Update this file as the real structure settles;
treat it as a living map, not a spec to satisfy exactly.

```
fitness-timer/
├── README.md
├── PLAN.md
├── STRUCTURE.md
├── DECISIONS.md
├── LICENSE
├── .github/
│   └── ISSUE_TEMPLATE/
└── app/
    ├── build.gradle.kts
    └── src/main/kotlin/.../
        ├── gesture/          # custom pointerInput state machine (section E)
        ├── timer/            # elapsedRealtime-based timer engine (section F)
        ├── media/
        │   ├── local/        # SAF + ExoPlayer local playback
        │   ├── session/      # NotificationListenerService + MediaController
        │   ├── online/       # direct-URL/HLS/DASH playback
        │   └── MediaState.kt # shared abstraction (section G)
        ├── render/           # video surface, negative-text overlay, CD/artwork, progress (section H)
        ├── state/            # single ApplicationState holder (section D)
        └── ui/                # top-level Compose screen(s)
```

Notes:
- No `data/`, `network/`, or `repository/` layers beyond what's above — v1
  has no backend and no persistence beyond simple local state/prefs.
- `render/` stays isolated so the rendering spike's outcome (TextureView
  blend vs. fallback vs. GL shader) can be swapped without touching
  `gesture/`, `timer/`, or `media/`.
- Package name/group ID not yet decided.
