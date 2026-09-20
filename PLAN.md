# Technical Plan

Grounded in a research pass against primary Android/AOSP/Media3/Play sources
(Sept 2026), each finding adversarially re-checked. Claims below are the
conclusions; anything genuinely unresolved is called out as such rather than
asserted.

## A. Product definition

A gesture-only, full-screen Android stopwatch/countdown whose visual identity
*is* whatever media is playing — local file, another app's session, or a
direct stream — with the timer rendered as a minimal effect layered into that
media. Differentiator: media is the primary surface, the timer is the
accessory (most timer apps are the reverse).

## B. Feasibility

| Feature | Classification | Notes |
|---|---|---|
| Stopwatch/countdown, accurate across background/lock | Straightforward | `SystemClock.elapsedRealtime()` is monotonic across deep sleep |
| Local file playback (video/audio, artwork) | Straightforward | Media3/ExoPlayer + SAF |
| Online stream playback (direct URL, HLS/DASH) | Straightforward | Needs a direct media/manifest URL, not a webpage — arbitrary web video extraction is **not possible** |
| Read metadata/art/position of another app's session | Android-dependent | Only via `NotificationListenerService` + `MediaSessionManager.getActiveSessions()`; no lighter permission exists |
| Send play/pause/seek/skip to another app | Android-dependent | Framework forwards commands regardless of advertised capability; the *target app* may silently ignore them |
| **Negative/inverted timer text over full-screen video** | **Challenging — unproven, needs a prototype spike** | See below |
| Same effect over another app's video | **Not possible** | No API exposes another app's decoded frames without per-session, user-consented `MediaProjection`, which also blanks secure/DRM content. Third-party media is control+metadata only, never a drawable video surface. |
| CD/vinyl animation over audio artwork | Straightforward | Pure Compose animation |
| Custom gesture system (tap/double-tap/2-finger/hold) | Straightforward, hand-built | No built-in Compose detector for two-finger tap/swipe; requires a custom `pointerInput` state machine |
| Exact countdown-complete alarm while backgrounded | Straightforward, needs a policy choice | `SCHEDULE_EXACT_ALARM` (user-grant) vs `USE_EXACT_ALARM` (Play auto-grants to genuine alarm/timer apps) |
| Play Store distribution w/ NotificationListenerService | Unconfirmed | No primary Play policy page names `BIND_NOTIFICATION_LISTENER_SERVICE` — verify directly in Play Console before submitting |
| F-Droid / GitHub-APK distribution | Fully local, Android-dependent risk | Media3 core has no proprietary deps (Apache-2.0). Risk: Android 13+ "restricted settings" can block sideloaded installs' notification-listener grant until manually unblocked |

### The rendering question

Media3's recommended video surface is `SurfaceView`, composited by
SurfaceFlinger in its own layer — the app window punches a transparent hole
through it. A Compose `BlendMode.Difference` overlay **cannot** read those
pixels; nothing in the docs supports it, and the model (hole-punch +
alpha-composite) argues against it.

`TextureView` *is* part of the normal view hierarchy and can plausibly
participate in a Compose blend — but this exact case (Compose
`AndroidView(TextureView)` + sibling `Difference` draw) isn't documented
either way. Confirmed costs if this is the path: extra per-frame copy, higher
power draw, HDR tone-mapped down to SDR on API 33+, no DRM support (not
relevant for personal local files).

A heavier fallback exists: a custom Media3 GL shader (`BaseGlShaderProgram`)
sampling a text-mask texture, applied only to the app's own ExoPlayer
instance during decode. Real, Media3-native, but `@UnstableApi` and unproven
for this exact use — more machinery than justified until the simple path is
proven not to work.

**Decision: prototype the TextureView + Difference-blend approach first,
before any other app code** (see Roadmap step 1). Fallback if it doesn't
invert correctly: plain contrast-color/shadow text, same fallback used for
API < 29 where `BlendMode.Difference` silently no-ops to `SrcOver`.

## C. Stack

| Dependency | Purpose | License | External dependency |
|---|---|---|---|
| Kotlin + Jetpack Compose | UI, language | Apache-2.0 | None |
| Media3 (ExoPlayer, Session, UI-Compose) 1.11.1 | Local/online playback, MediaSession hosting, `PlayerSurface` | Apache-2.0 | None — no GMS/proprietary deps in core |
| Storage Access Framework | Local file/folder picking, persisted access | Platform API | None |
| `MediaSessionManager` + `NotificationListenerService` | Reading/controlling other apps' sessions | Platform API | None (user grant, not paid) |
| Coroutines/Flow | Async, state streams | Apache-2.0 | None |
| `AlarmManager` (exact alarms) | Countdown-complete while backgrounded | Platform API | None |
| Custom gesture layer (`pointerInput`/`awaitPointerEvent`) | Whole gesture system | Own code | None |

**Explicitly not using:** cloud backend, paid media/AI services,
`MANAGE_EXTERNAL_STORAGE`, Media3's optional FFmpeg/AV1 decoder extensions in
v1 (separately-licensed native code, built-in `MediaCodec` formats already
cover MP4/H.264/AAC/MP3/etc.), Media3 custom-shader rendering unless the
TextureView prototype fails.

## D. System architecture

```
UI (Compose, full-screen)
  -> Gesture Engine (custom pointerInput state machine)
    -> Application State (single source of truth: mode, timer state, active media source)
      -> Timer Engine (elapsedRealtime-based, survives background/lock)
      -> Media Engine
          - Local Media (Media3 ExoPlayer + SAF)
          - Android MediaSession (NotificationListenerService + MediaController, read/control only)
          - Online Media (Media3 ExoPlayer, direct URL/HLS/DASH)
      -> Visual Renderer
          - Video (PlayerSurface, TextureView-backed)
          - Negative Timer Text (Compose Difference-blend overlay)
          - Artwork/CD (rotation animation over MediaMetadata art)
          - Progress (thin bottom bar, subtle)
```

Gesture Engine emits intents into a single `ApplicationState`. Timer Engine
and Media Engine are independent state producers feeding the same state
holder — they never call each other directly. Visual Renderer only reads
state.

## E. Gesture architecture

| Zone/input | Action |
|---|---|
| Tap center | start/pause (fires instantly — no competing double-tap in this zone) |
| Double-tap left third | seek -10s |
| Double-tap right third | seek +10s |
| Two-finger tap | play/pause media |
| Horizontal drag | scrub media |
| Two-finger horizontal swipe | prev/next track |
| Hold timer ~0.8s (paused only) | reset, growing ring, cancel on early release, haptic tick on completion |
| Top-left tap | timer mode picker |
| Top-right tap | media source picker |

Priority order (first match wins):
1. Second finger arrives while 1-finger hold-to-reset is charging → cancel, re-evaluate as 2-finger gesture.
2. Corner taps (mode/source pickers) — fixed hit-zones, checked before body gestures.
3. Two-finger tap vs. swipe — distinguished by movement distance/velocity.
4. One finger: hold-to-reset (paused only) → double-tap zones (left/right thirds) → drag (scrub) → plain tap (center = start/pause).

Left/right double-tap zones inherently add the system double-tap-window
delay to a plain tap there (no way around this while also wanting a
double-tap meaning in the same zone) — acceptable since those zones have no
single-tap meaning per the table above.

Finger-count gestures are distinguished by tracked pointer count (no
dedicated Compose API — custom code), not by timing. Tap vs. drag uses
`ViewConfiguration.touchSlop`. Hold-to-reset uses
`ViewConfiguration.longPressTimeout` (user-configurable, not a hardcoded
value).

System gesture conflicts: `Modifier.systemGestureExclusion()` for
back-swipe-adjacent edges (max 200dp/edge), edge-to-edge/immersive via
`WindowInsetsControllerCompat`. Home/quick-switch gestures cannot be
excluded — nothing essential at the very bottom edge.

**Accessibility gap (flagged, not yet decided):** TalkBack claims one-finger
touch gestures, so this screen is unusable with TalkBack on unless every
gesture is mirrored as a semantic action. Not a Play requirement, but a real
WCAG 2.5.1 gap worth deciding on explicitly rather than ignoring.

## F. Timer architecture

- Basis: `SystemClock.elapsedRealtime()` — monotonic, survives deep sleep, resets only on reboot. Store `startElapsedRealtime` + accumulated-paused-duration; compute displayed time as a pure function, never a mutated running counter.
- Reboot detection: `Settings.Global.BOOT_COUNT` — if changed since persisted, the `elapsedRealtime` anchor is invalid.
- No foreground service needed for stopwatch display while backgrounded.
- Countdown completion while backgrounded needs an exact alarm: `USE_EXACT_ALARM` (Play auto-grants to genuine timer apps) or `SCHEDULE_EXACT_ALARM` (user-grant flow).
- Completion alert: Android 17's background-audio hardening can silently swallow a raw sound-play call from the background. Use a high-importance notification with an alarm-channel sound, not a bare `MediaPlayer.play()`.

## G. Media architecture — one abstraction

```
data class MediaState(
  title: String?, artist: String?, artwork: ImageBitmap?,
  durationMs: Long?,        // unknown allowed on both local and third-party sources
  positionMs: Long, positionAnchorElapsedRealtime: Long, speed: Float,
  isPlaying: Boolean,
  capabilities: Set<Capability>,  // canSeek, canSkipNext, canSkipPrev — advisory only
  source: MediaSourceKind         // Local, ThirdParty, Online
)
```

Live position while playing = `positionMs + (elapsedRealtime() -
positionAnchorElapsedRealtime) * speed`. This extrapolation is our own
convention, not a documented Android formula.

## H. Visual rendering

1. **Prototype spike (first task):** `PlayerSurface(surfaceType =
   SURFACE_TYPE_TEXTURE_VIEW)` + sibling `Text` via
   `drawWithContent`/`drawText(blendMode = BlendMode.Difference)`, tested
   with/without `Modifier.graphicsLayer(compositingStrategy = Offscreen)`
   (want it *without*). Test on a real device, API 29+.
2. If it inverts correctly → that's the renderer, with a plain
   contrast/shadow fallback for API < 29 or non-blending devices.
3. If it doesn't → fall back to the same fallback style as the primary look
   rather than reaching for the GL shader route.
4. Artwork/CD mode: static bitmap + `rememberInfiniteTransition`-driven
   `rotationZ` on a `graphicsLayer` (draw-phase only, cheap).
5. Progress bar: thin `Canvas` draw, low opacity, skipped when duration is
   unknown.
6. No-media state: timer text only, subtle idle motion via the same
   `graphicsLayer` pattern.

## I. Roadmap

1. **Rendering spike** — de-risk the biggest unknown before anything else.
2. Timer engine + persistence (no UI polish) — correctness across
   background/lock/reboot first.
3. Gesture engine in isolation against a dummy timer.
4. Local media playback (Media3 + SAF + validated rendering approach).
5. Full-screen integration — timer + local media + gestures = first usable build.
6. Third-party MediaSession integration, including the restricted-settings
   onboarding flow.
7. Online stream source.
8. Source-picker UI (manual, persists until changed), polish, accessibility
   semantics layer.
9. Packaging — F-Droid metadata, Play submission groundwork if desired
   (including the Play Console policy check from section B).

## J. V1 scope

**In:** stopwatch + countdown, all three media source types, negative-text
video rendering (or fallback), CD/artwork audio rendering, full gesture
system, manual persistent source picker, local notification-based countdown
alert.

**Out:** workout tracking, reps/sets, calories, social features, AI
features, accounts, cloud sync, stats/dashboards, interval sequencing beyond
stopwatch/countdown, tablet/foldable layouts, any non-Android platform.

## K. Getting started

1. Install Android Studio (bundles JDK + SDK + emulator).
2. New empty Compose project, minSdk TBD (see `DECISIONS.md`).
3. Add Media3 (`exoplayer`, `session`, `ui-compose`) via version catalog.
4. Do the rendering spike (H/I step 1) as a scratch screen before real app
   structure.

## L. Risks

- Rendering feasibility is unproven — top risk, build the spike first.
- Third-party video is architecturally impossible to visually integrate;
  keep UI copy honest about this.
- Restricted-settings onboarding friction for sideloaded installs — design
  the onboarding screen assuming it might happen.
- Android Developer Verification's 2027 global rollout is a standing,
  unresolved risk to sideload/F-Droid distribution.
- Play's policy stance on `BIND_NOTIFICATION_LISTENER_SERVICE` is
  unconfirmed from docs — check Play Console directly before submitting.
- Android 17 background-audio hardening can silently swallow the
  countdown-complete sound if the alert path isn't built around a valid
  foreground state/notification channel.
- HDR/DRM loss on TextureView if that ends up being the rendering path
  (acceptable for personal local files).
- No official OLED burn-in guidance for a mostly-static full-screen timer —
  a self-chosen design call.
- FFmpeg/AV1 optional codec extensions bring separate copyleft-flavored
  licensing — skip for v1.

## M. Open-source setup

- License: Apache-2.0 (matches Media3/Kotlin/coroutines; avoid GPLv2
  specifically due to Apache-2.0 compatibility issues).
- Repo: standard single-module Compose app (`app/`), `LICENSE`,
  `CONTRIBUTING.md`, `.github/ISSUE_TEMPLATE/`.
- Dependency licensing: stick to Apache-2.0/MIT; document why the optional
  Media3 codec extensions are excluded.
- No telemetry — contradicts the no-cloud/no-accounts requirement, and
  there's no need for it on a personal-use project.
- Release: tagged GitHub Releases (APK) → F-Droid once stable → Play as an
  optional secondary channel pending the policy check above.
