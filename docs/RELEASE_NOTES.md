# w0y v0.0.2 beta-2

Fixes for beta-1 and a new look: Easy Glass in Smooth, split bars
and grouped settings in both styles.

## New

- **Easy Glass.** In Smooth with Easy Glass on, the screen now runs
  under the bottom bars, and they blur and slightly bend what is behind
  them, with a thin bright rim — like iOS 26. Cards, buttons and the track
  menu use the same glass. Blur needs Android 12, the bending Android 13;
  older phones get denser bars.
- **Split bottom bar.** Tabs sit in one block, search in its own button on
  the right. In Smooth it is a capsule and a circle, in Pixel two TexFi
  blocks with hard shadows.
- **Grouped settings.** Sections are gathered into captioned groups (Sound
  & player, Speed & storage, Music & account, App): grey glass in Smooth,
  bordered blocks in Pixel. Settings open under the bottom bars; a tab
  closes them.
- **Stand mode:** brightness follows the system by default and has a slider
  right in stand mode; a "cover" view with a large cover; long press
  switches title and cover; optional scrolling title; an upside-down view
  for a phone standing port-up.

## Fixed

- **Playback and downloads failed** on tracks YouTube serves only over its
  SABR protocol ("Expected URL scheme 'http' or 'https'"). Such tracks now
  play and download.
- **Recommendations leaked into playlists.** Account sync read the second
  page of a short playlist from YouTube's recommendations, so foreign
  tracks got in and kept changing. Sync now reads only the playlist itself,
  and playlists already mixed up are restored once from the account. A
  track added on the phone that never reached the account can be removed
  by that one-time restore.
- A playlist's queue no longer turns into recommendations after the app
  restarts, and shuffle on albums and artists plays only their tracks.
- Stand mode no longer lights the screen brighter than the system setting,
  and the brightness controls no longer overlap the close button.

## Files

- `TexFi-w0y-android.apk` — Android 8.0 (API 26) and newer.
- `w0y-linux-x86_64.tar.gz` — Linux desktop build; needs `mpv`. Unpack it and
  run `./install.sh`.
- `SHA256SUMS.txt` — checksums of the files above.

The APK is signed with the project key: it installs over beta-1 and your
data stays.

## Honest about the limits

- If recommendations already got into a playlist on YouTube itself (for
  example after "upload again"), remove them there by hand: the app cannot
  tell them from tracks you added.
- MP3 at 320 kbps does not make the sound better than the source: YouTube's
  audio is compressed more than that.
- Account sync works by reading YouTube's pages the way the web app does,
  and YouTube can change them without notice.
- The Linux version's interface is in Russian only and does not have the new
  features of this release yet.

w0y is not affiliated with YouTube or Google.
