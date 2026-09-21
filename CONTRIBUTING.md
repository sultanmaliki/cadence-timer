# Contributing to SetBeat

Thanks for looking. This is a small personal-use project, so the bar is simple: keep it small,
keep it private, and say what you actually tested.

## Ground rules

- **No network, accounts, analytics or paid services.** The app has no internet permission and
  that is a feature.
- **Don't invent Android behaviour.** If a change relies on an API's behaviour, check the primary
  documentation or source, and in `PLAN.md` / `DECISIONS.md` separate what you confirmed from what
  you assumed.
- **Keep it simple.** Prefer the smallest change that works; don't add layers "for later".

## Before you open a pull request

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

- On-device gesture tests (`app/src/androidTest`) run the real gesture engine using Compose's test
  framework, which works even on phones that block `adb shell input`. Run them by hand so your app
  data survives (Gradle's `connectedDebugAndroidTest` uninstalls the app afterwards):

  ```bash
  ./gradlew assembleDebug assembleDebugAndroidTest
  adb install -r -t app/build/outputs/apk/debug/app-debug.apk
  adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
  adb shell am instrument -w dev.fitnesstimer.test/androidx.test.runner.AndroidJUnitRunner
  ```

- Add or update unit tests for pure logic (timer, audio analysis, selection, colours) and, for
  gesture changes, an on-device test. Whole screens can't be scripted on some phones (MIUI blocks
  injected input and permission grants), so say plainly what you checked by hand and on which
  device and Android version.
- Performance matters on a screen that is on for a whole workout: check frame times
  (`adb shell dumpsys gfxinfo dev.fitnesstimer`) if you touch anything that animates, and keep the
  paused screen fully idle.
- Update `STRUCTURE.md` if you move files, and `DECISIONS.md` if you settle a question.

## Reporting a bug

Please include your phone model, Android version, which music app was playing, and what you
expected. A `logcat` excerpt filtered on `FitnessTimer` helps a lot.
