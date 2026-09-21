# Open Questions & Decisions

Things that research couldn't settle and need a real answer (from the user,
or from an on-device test) before or during implementation. Update this file
as each is resolved — move resolved items into `PLAN.md` instead of leaving
them here.

## Resolved

- **Local playback — DECIDED 2026-09-21: keep it as it is** (owner). Video and audio files,
  playlists and the ExoPlayer service stay alongside companion mode; companion mode is the default and
  a menu entry switches sources. It is built, tested, idle unless used, the only source for offline
  files, and the home of the original negative-blend video look.
- **Cleanup pass — done 2026-09-21** (`PLAN.md` N.5h): unused code, stale heap dump and intermediate
  test logs removed; nothing user-visible changed.
- **Name — DECIDED 2026-09-21: "SetBeat"** (repo `setbeat`), after two rejected candidates.
  *Cadence* (used briefly) collides with Cadence Design Systems' registered CADENCE mark (US Reg. No.
  3474136, Class 9, IC-design software). *RepBeat* (proposed by the owner) already exists as an App
  Store workout interval timer ("Interval Timer: RepBeat") and as the workout-music app "Repbeats",
  i.e. the same product space. Web searches for SetBeat, RepWave and BeatSet found no app with that
  exact name (only differently named workout-music/timer apps, e.g. FitBeat). **That is a search, not a
  trademark clearance, and not legal advice:** before any store listing, search USPTO/EUIPO/WIPO and
  the stores for the exact name (Google Play's process on a complaint is that the owner contacts the
  developer or files a form, which can end in a takedown). For GitHub/sideload use the practical
  risk looks low; it rises with a store listing, money or popularity. The package id stays
  `dev.fitnesstimer` so installs keep upgrading.
- **Icon — DECIDED 2026-09-21:** a timer progress ring (dim track, gradient arc up to a ring thumb)
  around the layered wave hills, on a dark indigo gradient; adaptive with a monochrome layer. It fits
  the SetBeat name (a set's progress ring plus the beat).
- **Banner — 2026-09-21:** `docs/banner.png` (1280x640) is used at the top of the README and is meant to
  be the repo's GitHub "social preview". GitHub offers no API for that setting, so it is uploaded by hand
  (repo Settings > General > Social preview).
- **Licence — Apache-2.0 added 2026-09-21**, as `PLAN.md` section M had planned from the start.
- **Beat-reactive wave needs `RECORD_AUDIO` — DECIDED 2026-09-21** (user asked
  for a real-time, One UI-style reactive wave). The only unprivileged way to
  see another app's audio is `Visualizer` on the output mix, which Android
  gates behind `RECORD_AUDIO`; MediaProjection playback capture was not
  needed. Mitigation: explanation card first, capture only while visible and
  playing, no microphone use, nothing stored or sent. Works on the test phone
  (PLAN.md N.5e); other devices/ROMs unverified, with an honest idle
  fallback. Play Store review of a `RECORD_AUDIO` declaration for a non-
  recording use is an open question if Play distribution is ever chosen.
- **Exact-alarm permission — DECIDED 2026-09-21: `USE_EXACT_ALARM`** (plus
  `SCHEDULE_EXACT_ALARM` for Android 12 only, maxSdk 32). Per the Android
  alarm docs it is auto-granted on Android 13+ with no user flow, but is
  subject to Google Play's limited-use-cases policy. A countdown timer
  should qualify as a genuine timer/alarm use, but that is a **Play Console
  question to confirm before any Play submission**; GitHub/F-Droid
  distribution is unaffected. `SCHEDULE_EXACT_ALARM` alone would need a
  per-user grant flow on Android 13+.
- **YouTube / YouTube Music "play from link" — DECIDED AGAINST, 2026-09-21.**
  Researched three approaches (details and sources in `PLAN.md` section
  N.1). Official IFrame embed: forbids overlays on the player and
  background/audio-only playback — incompatible with the negative timer
  overlay and screen-off gym use. Unofficial extraction (NewPipeExtractor,
  GPLv3): outside YouTube's rules, fragile, forces GPLv3, no Play Store.
  Chosen instead: **companion mode** — read and control another app's media
  session; the source app does the playback. Policy quotes came via a
  summarizing fetch; re-read the original pages before quoting them
  externally.
- **v0.2 order — decided 2026-09-21:** build companion mode first, decide the
  fate of local playback afterwards. Nothing is removed until then.

- **Code-review hardening pass — RESOLVED 2026-09-21, verified on-device.**
  The player is now the single source of truth for the queue
  (`setMediaItems` once; UI mirrors it via a listener), which gave real
  next/previous/auto-advance, the notification's Next button, and survival
  of Activity recreation. Also: missing/unreadable files skip to the next
  item with a message; audio focus + becoming-noisy + wake lock on the
  player; keep-screen-on; bounded artwork decode and scaled ambient-color
  frame (no OOM on huge art); timer ticks once per displayed second and is
  read in the draw phase; equalizer animates only while playing; scrub
  seeks coalesced (`ScrubAccumulator`); gesture handler no longer restarts
  on state change; timer persisted across process death
  (`TimerSnapshot`, wall-clock gap credited up to 12h); picker grant
  failures no longer crash and grants are released on playlist delete;
  corrupt playlist file kept as `.corrupt` instead of overwritten;
  PlaybackService only accepts own/system/notification controllers; debug
  launch hooks only work in debuggable builds. Not testable here (MIUI
  blocks injected input): the gesture state machine itself, the SAF picker
  UI, HyperOS Island. Known remaining limits: Android caps persisted URI
  grants per app (not surfaced to the user); countdown completion is still
  foreground-only (no background alarm).
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
  (The spike screen was later folded into `render/NegativeTimerText.kt`.)
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

- **Artwork bars — RESOLVED 2026-09-21:** auto-trim implemented (`ArtTrim.kt`, `PLAN.md`
  N.5f). Still to see on a real barred cover.
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

## Needs external verification (not answerable from docs)

- **Countdown-finished notification + sound** — needs a hand test: pick a
  countdown, tap Allow on the notification permission dialog, go home, wait.
  Cannot be granted from the shell on this MIUI phone. Also untested:
  reboot rescheduling and long Doze.
- **What the source apps actually publish.** Observed 2026-09-21 with Mi
  Music (`PLAN.md` N.4): keys `ALBUM, ALBUM_ARTIST, ART, ARTIST, DURATION,
  MEDIA_ID, NUM_TRACKS, TITLE, TRACK_NUMBER`; a 256x144 artwork bitmap under
  `ART` and no artwork URI; full transport actions incl. skip and seek. Still
  unobserved: YouTube Music (not playing at probe time) and any other app.
- **Stale sessions.** The YouTube app leaves a `STOPPED` session with
  metadata in the stack; selection must require `PLAYING` and exclude our own
  session. Handle in `SessionSelector` with unit tests.
- **Restricted settings for `NotificationListenerService` on HyperOS 3.0 /
  Android 16** for our adb-installed and GitHub-APK installs. Test phone
  state before granting: listener not enabled, `ACCESS_RESTRICTED_SETTINGS`
  appop at default, installer=null.
- **Listener rebind after HyperOS kills the process** — still untested (the
  shell can't kill the app process). Also: `onListenerConnected` did not
  fire after a `cmd notification allow_listener` grant on this phone, though
  session access worked; the app refreshes on resume instead. Verify with a
  real Settings-toggle grant.
- **Android docs for `MediaSessionManager.getActiveSessions` / `MediaMetadata`
  keys** could not be re-read on 2026-09-21 (the fetch returned navigation
  only); the plan currently relies on earlier research and general
  knowledge for these.
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
