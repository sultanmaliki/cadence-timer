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

**Released: v0.1.1** (debug-signed APK, GitHub Releases). Working on-device:
timer (stopwatch + countdown, persisted across process death), gesture
system, negative-text video rendering, native-aspect-ratio letterboxing with
ambient background, audio-mode artwork + equalizer, local playlists with a
real player-owned queue, app icon, and a media notification with
previous / play-pause / next and a timer pause/resume button. Automated:
41 JVM unit tests, lint clean; run logs in `test-logs/`.

**In progress: v0.2 — companion mode, One UI-style player, background countdown alarm** (decided 2026-09-21; core and screen
built and checked on-device with Mi Music, not yet released — see `PLAN.md`
section N): the app shows and controls whatever audio another app (YouTube Music,
Spotify, ...) is playing — title, artwork, equalizer animation, plus the
timer — while the music plays in the source app. Playing YouTube links
inside the app was researched and rejected: YouTube's policies forbid
overlays on the embedded player and background/audio-only playback, and
unofficial extraction is outside its rules. Whether local playback stays
afterwards is an open decision (`DECISIONS.md`).

Not verifiable by script on the test phone (MIUI blocks injected input):
the gesture state machine, the file-picker/settings screens, and whether
HyperOS shows the media notification in the Island. Those need hands-on
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
