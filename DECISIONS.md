# Open Questions & Decisions

Things that research couldn't settle and need a real answer (from the user,
or from an on-device test) before or during implementation. Update this file
as each is resolved — move resolved items into `PLAN.md` instead of leaving
them here.

## Blocking / do first

- **Rendering approach.** Does TextureView + Compose `BlendMode.Difference`
  actually invert video pixels on a real device? Undocumented either way.
  Resolve via the prototype spike (`PLAN.md` → Roadmap step 1). If no,
  fall back to plain contrast/shadow text — do not reach for the Media3 GL
  shader route without first confirming the simple path fails.

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
