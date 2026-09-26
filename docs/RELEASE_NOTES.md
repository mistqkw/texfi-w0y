# TexFi w0y v0.0.1-beta

The first beta. A YouTube Music client for Android: no ads, no Premium
restrictions, with your own account and downloads into phone storage.

## What's inside

- Search for tracks, albums and artists; artist and album pages.
- Background playback with a media notification, queue, repeat, shuffle,
  sleep timer.
- Sign in to a Google account — your playlists, likes, subscriptions. If
  Google refuses the app's window, there is sign-in by pasting a cookie
  by hand.
- Downloads into phone storage: tracks, albums, playlists; a queue with
  progress, a "Wi-Fi only" limit.
- Your own sound: speed, pitch and reverb on any track — SLOWED and SPED
  UP straight from the original file.
- Speed dial on the home screen: what you play most, plus whatever you
  pinned by hand.
- Recommendations by genre and sound, based on what you have already
  played and searched for.
- Listening stats for a week, a month and all time — computed on the phone.
- Clean mode: swap in the official clean version, hide or skip tracks
  marked "E", mute lines using synchronised lyrics (beta).
- A home screen widget: cover, title and transport.
- Lyrics from LRCLIB, synchronised line by line.
- Four languages: Russian, English, Ukrainian, Polish.

## About smoothness

Checked on a Pixel 9a with 120 Hz enabled, on the release build:
scrolling runs at 5 ms median and 7 ms at the 90th percentile against an
8.3 ms budget, with no missed vsyncs. At rest the interface no longer
redraws on every frame — it used to render 522 frames across four idle
seconds, now it renders 75. If even that is too much, the view settings
have a "live background" switch: with it off the background is drawn once
and the app produces no frames at all while idle.

## Honest about the limits

- Words cannot be cut out of a finished recording — that needs a track
  without vocals. The app swaps in the official clean version when one is
  published, and does not pretend to do more.
- Muting individual lines only works where synchronised lyrics were found,
  and is marked beta.
- There is no web version and none is planned.
- Part of the delay when a track starts comes from YouTube's side. That is
  why the "tap → sound" time is measured and shown on the home screen:
  a number you can check, not a promise.

## Installing

The APK is below. It is signed with the project key: updates install over
the top, and local data and playlists survive.

If you installed a test build before this release, you need to remove it
first: it has a different signature and will not install over the top.
