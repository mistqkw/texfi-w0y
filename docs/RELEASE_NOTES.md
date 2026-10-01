# w0y v0.0.1 beta-1

An open client for YouTube Music: with your own account, downloads, your
own sound for every track, and no telemetry. This is the first public
beta: it works, and it is honest about where it does not yet.

## What's inside

- Search for tracks, albums, artists and videos; artist and album pages.
- Background playback with a media notification, queue you can edit, repeat,
  shuffle, sleep timer.
- Sign in to a Google account: playlists, likes, subscriptions, two-way sync
  of playlists and likes. If Google refuses the app's window, there is
  sign-in by pasting a cookie by hand.
- Downloads into the app's storage: tracks, albums, playlists, a "Wi-Fi only"
  limit, and a button that copies them into `Music/w0y music`.
- Your own sound per track: speed, pitch and reverb (SLOWED, SPED UP) straight
  from the original file, remembered for that track alone.
- Lyrics from LRCLIB, synchronised line by line, with translation.
- Speed dial on the home screen, listening stats, a widget.
- Clean mode: swap in the official clean version, hide or skip tracks
  marked "E".
- Themes, colour schemes, four languages (Russian, English, Ukrainian,
  Polish), settings export.
- A desktop version for Linux (see below).

## Files

- `TexFi-w0y-android.apk` — Android 8.0 (API 26) and newer.
- `w0y-linux-x86_64.tar.gz` — Linux desktop build; needs `mpv`. Unpack it and
  run `./install.sh`.
- `SHA256SUMS.txt` — checksums of the files above.

The APK is signed with the project key: if you already have a w0y installed
from a release signed with it, the update goes on top and your data stays.

## Honest about the limits

- Account sync was tried on one account so far. It works by reading
  YouTube's pages the way the web app does, and YouTube can change them
  without notice; if a playlist, cover or like does not arrive, that is a
  bug worth reporting. A list that was not read to the end is never taken
  as a reason to delete anything.
- Someone else's playlist saved in your library cannot be edited — YouTube
  does not allow it, so w0y shows it read-only.
- Words cannot be cut out of a finished recording. The app swaps in the
  official clean version when one is published, and does not pretend to do
  more. Muting single lines works only where synchronised lyrics were found,
  and is marked beta.
- Google does not let a third-party app sign in with a phone's account on its
  own, so the password or confirmation happens on Google's page.
- Downloads live inside the app until you press "save to music", which makes
  a copy. Saving to the Music folder is written for Android 10+.
- The Linux version's interface is in Russian only. Sign-in on it opens a
  separate Chromium window; real-account sync there has had little testing.
- There is no web version and none is planned.
- Part of the delay when a track starts comes from YouTube's side, which is
  why the "tap → sound" time is measured and shown on the home screen.

w0y is not affiliated with YouTube or Google.
