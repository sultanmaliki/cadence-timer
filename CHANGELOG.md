# Changelog

All notable changes to SetBeat, newest first. Each release has fuller notes (and the APK) on the
[Releases](https://github.com/sultanmaliki/setbeat/releases) page. Versions before 0.2.3 were published
under the earlier app names *Fitness Timer* (0.1 to 0.2) and *Cadence* (0.2.1 to 0.2.2).

## 0.2.3 (SetBeat)
- **Renamed to SetBeat** (a gym "set" plus the "beat" of the music). The earlier candidates were
  rejected: *Cadence* collides with a registered trademark, and *RepBeat* is an existing workout timer
  app. The Android package id is unchanged, so this installs over 0.2.x.
- New banner and README header; `CHANGELOG.md` added.
- No behaviour changes.

## 0.2.2
- Fixed the hold-to-reset ring getting stuck on screen after pausing, starting or resuming.
- Countdown dialog: accepts `mm:ss` and `h:mm:ss` (up to 99:59:59), shows an error on bad input, and
  no longer overflows on a huge number of minutes.
- 30 on-device tests (gestures and the whole timer flow); dead code, a stale heap dump and
  intermediate test logs removed.

## 0.2.1
- Renamed to *Cadence*; new adaptive icon (with a themed-icon layer); README, LICENSE (Apache-2.0),
  CONTRIBUTING and a bug-report template.
- Artwork side bars baked into some covers are trimmed automatically.
- Wave fixes: it now spans the whole bar (it used to start late after about 70% of a song), it no
  longer shrinks or vanishes in quiet passages, and a stale-audio-state bug is fixed.
- Two-finger tap no longer skips the song when one finger lifts first; a quick flick in the centre no
  longer toggles the timer.
- No more "Nothing playing" flicker between tracks.

## 0.2
- **Companion mode:** shows and controls whatever another app is playing (title, artist, artwork,
  progress) in a One UI-style player; gestures drive the source app. Needs notification access.
- **Beat-reactive wave:** the progress wave follows the low, mid and high sounds of the music in real
  time (Android's audio visualizer; needs the Record audio permission, never uses the microphone).
- **Background countdown alarm:** exact alarm plus notification, works with the screen off.
- Non-debuggable release build.

## 0.1.1
- Real player-owned queue (next, previous, auto-advance) and notification controls including a timer
  button; audio focus, pause on headphone unplug, screen kept on.
- Timer state survives the app being killed; performance and security hardening; unit tests.

## 0.1
- First installable build: stopwatch and countdown, gesture system, local video and audio with the
  negative-blend timer, playlists, ambient background, app icon.
