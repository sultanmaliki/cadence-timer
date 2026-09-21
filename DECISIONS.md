# Open Questions & Decisions

Things that research couldn't settle and need a real answer (from the user,
or from an on-device test) before or during implementation. Update this file
as each is resolved — move resolved items into `PLAN.md` instead of leaving
them here.

## Resolved

- **Media wouldn't play — RESOLVED 2026-09-21, confirmed on-device.** Root
  cause was `PlaybackService.onConnect` granting *empty* command sets: the
  `super.onConnect()` result's `Player$Commands` / `SessionCommands` both
  hashed as empty-set values in logcat, so inheriting them denied every
  `setMediaItem`/`play` from the controller silently (no player state
  change on either side, no error). Fixed by granting explicitly
  (`Player.Commands.Builder().addAllCommands()` + the public
  `DEFAULT_SESSION_AND_LIBRARY_COMMANDS` plus the custom timer command).
  Two other real bugs were fixed on the way: the notification button's
  missing icon resource (crashed every launch) and a Compose
  `DisposableEffect` closure that released the controller right after
  connecting. Diagnosis method worth reusing: a debug launch hook
  (`--es debug_media_uri file:///sdcard/Android/data/dev.fitnesstimer/files/x.mp4`
  on `MainActivity`) plus `FitnessTimer`-tagged logs, since MIUI blocks
  scripted taps and the SAF picker can't be driven from adb.

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
