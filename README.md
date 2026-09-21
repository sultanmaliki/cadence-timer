# Fitness Timer

A minimalist, gesture-driven Android stopwatch/countdown for personal gym use.

**Core philosophy:** the timer is not the interface — the media is. Full-screen
video/audio/artwork fills the screen; the timer is a subtle effect layered
into it, not a UI built around a timer with media bolted on.

Native Android (Kotlin + Jetpack Compose), phone only, free/open-source, no
backend, no accounts, no cloud, no paid services.

## Docs

- [`PLAN.md`](PLAN.md) — the technical plan: feasibility, architecture, stack, risks. Source of truth for *why* things are built the way they are.
- [`STRUCTURE.md`](STRUCTURE.md) — current package layout plus the planned v0.2 additions.
- [`DECISIONS.md`](DECISIONS.md) — resolved decisions, open questions and unresolved risks.
- [`test-logs/`](test-logs) — saved unit-test / lint / on-device run logs.

## Status

**Released: v0.2** (GitHub Releases; signed with the debug key, not debuggable).

- **Companion mode:** shows and controls whatever another app (Mi Music, and
  in principle any app with a media session) is playing — title, artist,
  artwork, progress — in a One UI-style player, with the timer underneath.
  Gestures drive the source app (confirmed working by hand). Needs
  notification access; reads media sessions only.
- **Beat-reactive wave:** the progress wave follows the music's low/mid/high in
  real time (Android's audio visualizer; needs the Record audio permission,
  never uses the microphone, stores or sends nothing).
- **Background countdown alarm:** exact alarm + notification, works with the
  screen off (the notification/sound itself still needs a hand test).
- Still there: stopwatch + countdown (persisted across process death),
  gesture system, local files and playlists with a real player queue,
  negative-text video rendering, audio mode, media notification.

Playing YouTube links inside the app was researched and rejected (YouTube's
policies forbid overlays and background/audio-only playback on embeds;
unofficial extraction is outside its rules) — see `PLAN.md` section N. The
fate of local playback is an open decision (`DECISIONS.md`).

Automated: 93 JVM unit tests, lint clean; run logs in `test-logs/`. Not
verifiable by script on the test phone (MIUI blocks injected input and
permission grants): the countdown notification/sound, the real Settings
toggle for notification access, and HyperOS Island behaviour need hands-on
testing.

## Scope (v1)

Stopwatch + countdown, local media playback, read/control of other apps'
media sessions (companion mode, v0.2), negative-text-over-video effect with native-aspect-ratio +
ambient-color letterboxing, CD/artwork animation for audio, full gesture
control, manual source picker.

**Explicitly not in v1:** workout tracking, reps/sets, calories, social
features, AI features, accounts, cloud sync, stats/dashboards, interval
sequencing, tablets/foldables, any non-Android platform, **online/direct-URL
streaming** (dropped 2026-09-20, deferred to a future update), live-blurred
ambient background (deferred — see `PLAN.md` section H).
