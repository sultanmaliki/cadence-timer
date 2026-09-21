# Contributing to SetBeat

Thanks for looking. This is a small personal-use project, so the bar is simple: keep it small,
keep it private, and say what you actually tested.

## Ground rules

- **No network, accounts, analytics or paid services.** The app has no internet permission and
  that is a feature. A change that needs a new permission must justify it in the README's
  permissions table and in `DECISIONS.md`.
- **Don't invent Android behaviour.** If a change relies on an API's behaviour, check the primary
  documentation or source, and in `PLAN.md` / `DECISIONS.md` separate what you confirmed from what
  you assumed. "It compiled" and "it worked on my phone" are different claims; say which one you
  mean.
- **Keep it simple.** Prefer the smallest change that works; don't add layers "for later", and delete
  code that nothing uses.
- **Don't change the package id (`dev.fitnesstimer`).** The app has been renamed twice; the id stays
  so installs keep upgrading.

## Setup

You need Android Studio (its bundled JDK is fine), an Android SDK with API 37, and a phone or
emulator on Android 10 (API 29) or newer. The Gradle wrapper does the rest. See
[`STRUCTURE.md`](STRUCTURE.md) for where things live.

> **Windows + Git Bash:** point `JAVA_HOME` at Android Studio's `jbr` folder, and do **not** set
> `MSYS_NO_PATHCONV=1` in the same shell as `gradlew.bat`: it stops the path being converted and
> Gradle then fails without a useful message. Use it only for the `adb` commands.

## Before you open a pull request

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease
```

All of it must pass, with lint clean. Then, depending on what you touched:

### Unit tests (JVM, `app/src/test`)
Pure logic lives in small functions so it can be tested without a phone: the timer engine, alarm
scheduling, countdown input, session selection, position maths, audio-band analysis, colour
derivation, artwork trimming, the two-finger tracker, playlist storage. New logic gets tests, and a
bug fix gets a regression test that **fails without the fix** (revert the fix once and confirm).

### On-device tests (`app/src/androidTest`)
30 tests drive the real gesture engine and the whole timer flow through the real `MainScreen`, using
Compose's test framework. It dispatches touches straight into the app, so it works even on phones that
block `adb shell input` (e.g. MIUI). Run them by hand: Gradle's `connectedDebugAndroidTest` uninstalls
the app afterwards and wipes its data.

```bash
./gradlew assembleDebug assembleDebugAndroidTest
adb install -r -t app/build/outputs/apk/debug/app-debug.apk
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w dev.fitnesstimer.test/androidx.test.runner.AndroidJUnitRunner
adb uninstall dev.fitnesstimer.test
```

Two harness gotchas that cost time:
- A `performTouchInput { ... }` block delivers its events together when it ends, so a real pause
  between "finger down" and "move" has to happen **between** blocks.
- The gesture engine measures holds in real time but wakes on coroutine timeouts that run on
  Compose's virtual clock. Let both pass together in small steps (see `waitReal` in the tests), or
  hold-related tests will be flaky.

### Things a script can't check
Permission dialogs, the Settings toggle for notification access, the countdown notification's
sound and the launcher icon need hands and a real phone. Say plainly what you checked by hand, and on
which device and Android version.

### Performance
The screen is on for a whole workout, so frames and battery matter. If you touch anything that
animates, check frame times (`adb shell dumpsys gfxinfo dev.fitnesstimer`), keep animations off the
composition path (read state in the draw phase), and keep the paused screen fully idle (zero frames).

## Docs and changelog

- User-visible change: add a line to [`CHANGELOG.md`](CHANGELOG.md).
- Moved or added files: update [`STRUCTURE.md`](STRUCTURE.md).
- Settled a question or made a trade-off: record it in [`DECISIONS.md`](DECISIONS.md), with why.
- Changed how something works or what was verified: update the relevant section of
  [`PLAN.md`](PLAN.md), keeping "confirmed" and "assumed" separate.

## Pull requests

Keep them focused, and describe **what changed, why, how you tested it, and what you did not
verify**. Contributions are licensed under the project's [Apache-2.0](LICENSE) licence.

## Releases (maintainer checklist)

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts`; update `CHANGELOG.md`.
2. Clean build with unit tests, lint and `assembleRelease`; run the on-device tests on that build.
3. Install the release APK over the previous one on a real phone and smoke-test it (launch, audio
   capture starts, no crash, developer test hooks are ignored).
4. Push, then publish a GitHub release from `main` with the APK and detailed notes, including a
   "Not verified" section. Sideload builds are signed with the debug key so they upgrade in place;
   a private release keystore is needed before any Play or F-Droid distribution.

## Reporting a bug

Please include your phone model, Android version and ROM, which music app was playing, and what you
expected. A `logcat` excerpt filtered on `FitnessTimer` helps a lot. For anything security-sensitive,
use GitHub's private vulnerability reporting (the repo's Security tab) if it is available, and
otherwise open an issue without exploit details.
