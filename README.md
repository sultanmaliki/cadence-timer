# Fitness Timer

A minimalist, gesture-driven Android stopwatch/countdown for personal gym use.

**Core philosophy:** the timer is not the interface — the media is. Full-screen
video/audio/artwork fills the screen; the timer is a subtle effect layered
into it, not a UI built around a timer with media bolted on.

Native Android (Kotlin + Jetpack Compose), phone only, free/open-source, no
backend, no accounts, no cloud, no paid services.

## Docs

- [`PLAN.md`](PLAN.md) — the technical plan: feasibility, architecture, stack, risks. Source of truth for *why* things are built the way they are.
- [`STRUCTURE.md`](STRUCTURE.md) — planned module/package layout.
- [`DECISIONS.md`](DECISIONS.md) — open questions and unresolved risks to revisit as the app is built.

## Status

Most of the app is built and working on-device: timer (stopwatch +
countdown), gesture system, negative-text video rendering, native-aspect-
ratio letterboxing with ambient background, audio-mode squarcle artwork +
equalizer, playlists, app icon, and a `MediaSessionService` for a system
notification with transport controls.

**Currently broken: media playback itself doesn't work** (`DECISIONS.md` →
"Blocking" has the full writeup). Three real bugs were found and fixed
along the way (a crash on every launch, missing player-command grants, and
a Compose `DisposableEffect` closure bug that released the controller right
after connecting) but the core symptom — nothing plays — persists past all
three. Diagnostic logging is in place; next session starts by reproducing
and reading logcat for the `FitnessTimer` tag.

The gesture state machine also still needs real hands-on testing since this
test device blocks scripted touch input (see `DECISIONS.md`).

## Scope (v1)

Stopwatch + countdown, local media playback, read/control of other apps'
media sessions, negative-text-over-video effect with native-aspect-ratio +
ambient-color letterboxing, CD/artwork animation for audio, full gesture
control, manual source picker.

**Explicitly not in v1:** workout tracking, reps/sets, calories, social
features, AI features, accounts, cloud sync, stats/dashboards, interval
sequencing, tablets/foldables, any non-Android platform, **online/direct-URL
streaming** (dropped 2026-09-20, deferred to a future update), live-blurred
ambient background (deferred — see `PLAN.md` section H).
