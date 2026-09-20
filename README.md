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

Pre-code. Next concrete step: the rendering spike (see `PLAN.md` → Roadmap,
step 1) — a throwaway screen proving whether the negative-text-over-video
effect actually works before any real app structure is written.

## Scope (v1)

Stopwatch + countdown, local media playback, read/control of other apps'
media sessions, direct-URL online streams, negative-text-over-video effect
(or a documented fallback), CD/artwork animation for audio, full gesture
control, manual source picker.

**Explicitly not in v1:** workout tracking, reps/sets, calories, social
features, AI features, accounts, cloud sync, stats/dashboards, interval
sequencing, tablets/foldables, any non-Android platform.
