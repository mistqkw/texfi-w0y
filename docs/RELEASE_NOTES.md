# w0y v0.0.2 beta-1

The second beta: a second look for the whole app, lyrics and a visualizer,
proper downloads, listening stats and a lot of speed work.

## New

- **Two styles.** Pixel, as before, and Smooth: rounded, soft, with a light
  liquid-glass touch on panels and cards (or plain matte surfaces, if you
  prefer). Picked once on first launch, changed any time in Settings → Look,
  with a circular transition and no restart.
- **Lyrics.** Large synced lyrics in the player and full screen, the current
  line highlighted, word-by-word where available, tap a line to seek.
- **Visualizer** in place of the cover: four looks in Pixel, two in Smooth.
- **Stand mode:** a black full-screen view with a large title and big
  buttons for a phone on a stand; the screen stays on while it is open.
- **Downloads, reworked.** Range downloads with a queue, reasons when
  something fails, a Downloads section in settings and a downloaded screen.
- **Save to the phone as MP3.** "Save to music" now makes MP3 files at
  320 kbps with cover, title, artist and album. The folder is chosen right on
  the Downloaded screen the first time you open it.
- **Listening stats:** a real listening rule, minutes, a chart.
- **Playlists:** drag to reorder, a header like an album's, and playing a
  playlist plays only its own tracks.
- **Speed dial** fills from the first play and learns the order from what you
  finish. "Hide" just hides a tile; play the track again and it can come back.
- **Featured artists:** on a track with several artists, each one opens on
  its own from the player and the track menu.
- **Swipe along the bottom bar** to switch tabs without tapping each one.
- **Full reset** in Settings → Data.
- Sand is the default accent colour.

## Faster

- Faster track start: the start is measured step by step, the next track is
  prepared in advance.
- Pixel style reacts from the first frame and no longer redraws the screen
  while idle.

## Fixed

- Downloads from servers that do not report the file size were cut at 1 MB
  and still marked complete. If you have such tracks, delete them and
  download again.
- Seasonal promo shelves from YouTube ("The sound of autumn" and the like) no
  longer appear on the home screen.

## Files

- `TexFi-w0y-android.apk` — Android 8.0 (API 26) and newer.
- `w0y-linux-x86_64.tar.gz` — Linux desktop build; needs `mpv`. Unpack it and
  run `./install.sh`.
- `SHA256SUMS.txt` — checksums of the files above.

The APK is signed with the project key: if you have v0.0.1 installed, the
update goes on top and your data stays.

## Honest about the limits

- MP3 at 320 kbps does not make the sound better than the source: YouTube's
  audio is compressed more than that. 320 is there so nothing more is lost
  in conversion. Converting takes a few seconds per track.
- Account sync works by reading YouTube's pages the way the web app does,
  and YouTube can change them without notice. A list that was not read to
  the end is never taken as a reason to delete anything.
- Words cannot be cut out of a finished recording; the app swaps in the
  official clean version when one exists.
- The Linux version's interface is in Russian only and does not have the new
  features of this release yet.
- There is no web version and none is planned.

w0y is not affiliated with YouTube or Google.
