# Open Questions & Decisions

Things that research couldn't settle and need a real answer (from the user,
or from an on-device test) before or during implementation. Update this file
as each is resolved — move resolved items into `PLAN.md` instead of leaving
them here.

## Blocking — pick this up first

- **Media still won't play, even after two confirmed-and-fixed bugs.**
  Session: 2026-09-20/21. Symptom: pick a file in the source menu, picker
  returns fine, but nothing plays — screen goes back to the no-media timer
  state. Two real bugs were found and fixed along the way (both confirmed
  via on-device logcat, not guessed):
  1. The custom "timer toggle" notification button (`media/PlaybackService.kt`)
     was missing a required icon resource id, which threw inside Media3's
     legacy-compat layer during `onConnect` and made the whole session
     get rejected — this was the "app force-closes on every launch" bug.
     Fixed with `setIconResId(android.R.drawable.ic_lock_idle_alarm)`.
  2. `onConnect` never explicitly granted player commands (play/seek/
     setMediaItem/etc.), only session commands — fixed by building the
     `ConnectionResult` from `super.onConnect(...)`'s defaults instead of
     from scratch.
  3. A genuine Compose bug in `MainScreen.kt`'s controller-lifecycle
     `DisposableEffect(controller) { onDispose { controller?.release() } }`:
     `controller` was read *inside* `onDispose`, which re-reads the latest
     state at dispose time rather than the value this effect instance was
     keyed on — so the very first recomposition after connecting (key
     changing from `null` to the real controller) disposed the OLD
     `null`-keyed effect instance, whose `onDispose` then read the
     already-updated (non-null) `controller` and released it — confirmed
     via logcat: `isConnected=true` right after connecting, `isConnected=
     false` moments later when `setMediaItem`/`prepare`/`play` were called
     (and `mediaItemCount=0` afterward, i.e. the commands were silently
     dropped on a dead controller). Fixed by capturing `val toRelease =
     controller` before `onDispose`.

  **After all three fixes, the symptom is unchanged** — media still
  doesn't play. This means there's at least one more distinct bug not yet
  found. Diagnostic logging is already in place and shipped in this
  commit (`Log.d/Log.e` tagged `"FitnessTimer"` in `MainScreen.kt`'s
  controller-connect, tracks/playback-state/error listener, and the load
  effect; plus a player-level error listener directly in
  `PlaybackService.kt`) — next session, reproduce the issue and read
  logcat for `FitnessTimer` tags first, specifically:
  - Does `isConnected` stay `true` this time through the load effect?
  - Does `onPlayerError` fire (service-side or controller-side listener)
    — if so, that's almost certainly SAF read-permission failing for the
    picked `content://` URI, or a codec/format issue MediaMetadataRetriever
    handles fine but ExoPlayer doesn't.
  - Does `onPlaybackStateChanged`/`onIsPlayingChanged` fire at all after
    `play()`, or does it just sit at IDLE?
  - `mediaItemCount` after `setMediaItem` — 0 means the command didn't
    land on a real connected controller; 1 means it landed and the
    problem is downstream (in ExoPlayer's actual loading/decoding).

## Resolved

- **Rendering approach — RESOLVED 2026-09-20, confirmed on-device.**
  `TextureView` (`PlayerSurface(surfaceType = SURFACE_TYPE_TEXTURE_VIEW)`)
  + a sibling Compose `drawText(blendMode = BlendMode.Difference)`, with
  `CompositingStrategy.Auto` (not `Offscreen`), genuinely inverts pixels
  under the timer text against live video. Tested on a real phone (Xiaomi/
  POCO, Android 16 / API 36): the same string rendered purple/blue over
  yellow-green grass and near-white over a dark shadow in the same frame —
  conclusive evidence the blend reads actual video content, not a static
  color. This is the renderer for local/online video. Third-party video
  remains out of scope regardless (see PLAN.md section B) since this
  technique only ever applies to the app's own `ExoPlayer`/`PlayerSurface`.
  Source: `app/src/main/kotlin/dev/fitnesstimer/render/RenderSpikeScreen.kt`.
  Not yet re-verified: the `Offscreen`-breaks-it and API<29-fallback control
  cases (MIUI blocked ADB's synthetic taps on the test device); low priority
  since the positive result already answers the load-bearing question.

- **Online media source — DROPPED from v1, 2026-09-20.** User decided
  direct-URL/HLS/DASH streaming isn't worth the added complexity for a
  local-gym-use app right now. Media3 supports it technically (unchanged
  finding), so re-adding it later is a scoped addition, not a redesign —
  deferred rather than ruled out. `PLAN.md` sections B/D/G/I/J updated.
- **Aspect-ratio / letterbox ambient background — decided 2026-09-20.**
  Native aspect ratio via Media3-Compose's `ContentFrame(contentScale =
  ContentScale.Fit)`, letterbox filled with a one-time dominant color
  (`MediaMetadataRetriever` + `androidx.palette` 1.0.0) rather than a plain
  black bar or a live blurred replay of the video. True YouTube-style live
  blur is deferred — it needs a second simultaneous decode or periodic frame
  capture, real added cost for a cosmetic effect, unlike the TextureView
  blend which turned out to be cheap. Revisit if the static-color version
  looks flat in practice.

- **On-device automated gesture testing isn't available on the current test
  phone.** MIUI/HyperOS blocks ADB's synthetic `input tap`/`swipe`
  (`SecurityException: Injecting input events requires INJECT_EVENTS
  permission`) — confirmed on this device, not a project bug. The gesture
  state machine (`gesture/TimerGestures.kt`) has been compiled and smoke-run
  (a hold-to-reset ring was observed rendering during a real manual touch)
  but not systematically exercised. Real verification of the priority rules
  in PLAN.md section E — double-tap timing, drag-vs-tap thresholds,
  two-finger detection, corner zones — has to happen by hand on-device, or
  via Android Studio's instrumented UI tests (`MotionEvent` injection in a
  test APK isn't subject to the same restriction) if that's worth setting
  up later.
- **Top-right corner now does double duty.** Per the gesture table it's the
  "media source picker"; since there's only one source kind (local files) in
  v1, it directly opens the SAF file picker rather than a picker-of-pickers.
  Revisit this if/when MediaSession sources are added (roadmap step 6) —
  then it needs to actually pick between sources, not just open one.

## Needs a decision, not urgent

- **minSdk.** Confirmed constraints: Media3 needs ≥23; `BlendMode.Difference`
  needs ≥29 (silent no-op fallback below that); some haptic constants need
  ≥34. No device-reach data available locally — pick a floor once that's
  known, likely 29 or 30.
- **Distribution channel(s).** F-Droid/GitHub APK vs. Google Play vs. both.
  Affects the restricted-settings/notification-listener onboarding design
  and whether the Play policy check below is needed at all.
- **Accessibility fallback.** TalkBack intercepts one-finger gestures, so
  the app is unusable with it on unless gestures are mirrored as semantic
  actions. Worth doing given how cheap it is relative to the gesture engine
  already being built, but not decided.
- **Exact-alarm permission choice.** `USE_EXACT_ALARM` (auto-granted,
  Play-restricted to genuine alarm/timer apps) vs. `SCHEDULE_EXACT_ALARM`
  (user-grant flow, works regardless of Play's judgment of "core function").

## Needs external verification (not answerable from docs)

- **Play policy on `BIND_NOTIFICATION_LISTENER_SERVICE`.** No primary Play
  policy page names it. Check directly in Play Console's "App content" flow
  before submitting, if Play distribution is chosen.
- **Restricted-settings trigger specifics.** Whether an F-Droid-client
  install vs. a raw sideloaded APK behaves differently for the Android 13+
  notification-listener toggle block isn't stated in any primary source
  found. Test on a real device with each install method.
- **Android Developer Verification 2027 rollout.** Global sideload
  enforcement is expected but unspecified in detail. Revisit before
  finalizing a distribution plan.
