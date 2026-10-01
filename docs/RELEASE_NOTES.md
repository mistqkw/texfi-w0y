# TexFi w0y v0.0.3-beta-2

A YouTube Music client for Android: no ads, no Premium restrictions,
with your own account and downloads into phone storage.

## What is new in 0.0.3

- **Save downloads to your Music folder.** Library → Downloaded has a
  "save to music" button: it writes each downloaded track as one ordinary
  file into `Music/w0y music` — named "Artist - Title", in the format YouTube
  gave (m4a or webm/opus, nothing is re-encoded) — where other players see it
  and you can copy it to a computer. Tracks already saved are skipped. Title,
  artist and album go into the phone's media library; the file itself does
  not carry tags or a cover. Written for Android 10+; on Android 8–9 it needs
  a storage permission the app does not ask for yet, so it may fail there.
  Not yet tried on many devices — a failed save is reported, not hidden.
- **Your real YouTube playlists, editable.** Playlists that already lived
  in YouTube Music now appear in Library as ordinary playlists: add and
  remove tracks, rename them, and it all goes back to the account. Playlists
  you only saved from someone else are shown read-only, because YouTube does
  not let anyone but the author edit them. Saved albums have their own
  section.
- **Two-way sync.** What changes in YouTube Music comes to w0y and what you
  do in w0y goes to the account: tracks in playlists, renames, deletions,
  and likes. The app remembers what both sides had at the last sync, so
  "removed" is told apart from "not arrived yet" and neither side loses an
  edit. Changes made offline wait and go out on the next sync.
- **Library fills itself.** Opening Library syncs in the background (at most
  once a minute); the "refresh" button is still there to force it, and so
  is the pull-down gesture on Home. A playlist is re-checked when you open it.
- **Covers are visible.** Every playlist shows its cover from YouTube —
  including the custom one you picked there; with none, the first track's
  picture stands in instead of a grey square.
- **Your avatar** sits next to your name in Library, like on YouTube.
- **Sand as the second brand colour.** The palette moved from amber to sand
  `#e0a860`, with a warmer light text colour.
- **Colour settings.** Accent swatches with a live preview, and your own
  colour by hue slider or HEX.
- **Stepped motion, everywhere.** One motion language for the whole app:
  movement goes in a few quick steps with a small overshoot. Segmented
  progress bars on the mini player, downloads and stats; numbers fill in.
  Switching tabs is a calm four-step fade, nothing flashy.
- **Pull to refresh on Home** with a pixel indicator.
- **Hide a tile from speed dial for a while.** Long-press a tile: pin it or
  hide it for 7 to 56 days (28 by default), with an undo bar and a list of
  hidden tiles in Settings.
- **Swipes.** Swipe a track right for "play next", left for "to the queue";
  swipe the mini player sideways to change track; long-press any track for
  the same menu everywhere.
- **The queue is yours.** Drag a track by its grip to reorder, swipe to
  remove; the queue and the last tab survive a restart.
- **Search history** is saved when you press search, not after a typing
  pause, and each entry can be removed with a cross.

## Fixed

- Track menu had a large empty gap under the playlist list.
- Mini player could expand over the whole screen after the swipe was added.

## Speed

Measured on the same phone, alternating old and new builds: cold start
median 181 ms before, 180 ms after; dropped frames on scroll about 0.3%
in both; the APK grew from 7.15 to 7.19 MB.

## What's inside

- Search for tracks, albums and artists; artist and album pages.
- Background playback with a media notification, queue, repeat, shuffle,
  sleep timer.
- Sign in to a Google account — your playlists, likes, subscriptions. If
  Google refuses the app's window, there is sign-in by pasting a cookie
  by hand.
- Downloads into phone storage: tracks, albums, playlists; a queue with
  progress, a "Wi-Fi only" limit.
- Your own sound: speed, pitch and reverb per track — SLOWED and SPED UP
  straight from the original file, remembered for that track alone.
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

## Honest about the limits

- Account sync was tried on one account so far. It works by reading
  YouTube's pages the way the web app does, and YouTube can change them
  without notice; if a playlist, cover or like does not arrive, that is a
  bug worth reporting. A list that was not read to the end is never taken
  as a reason to delete anything.
- Someone else's playlist saved in your library cannot be edited — YouTube
  does not allow it, so w0y shows it read-only. The trash button there
  removes it from your library, it does not delete the original.
- A playlist deleted in the account disappears from w0y only after two
  separate checks; a playlist deleted in w0y while offline may come back
  on the next sync.
- Words cannot be cut out of a finished recording — that needs a track
  without vocals. The app swaps in the official clean version when one is
  published, and does not pretend to do more.
- Muting individual lines only works where synchronised lyrics were found,
  and is marked beta.
- The name of a Bluetooth device is not always given to an app, and asking
  for the Bluetooth permission just to print a label is a bad trade. When
  the name is missing, the kind of output is shown instead.
- The share card is a picture, not a link: whoever gets it sees the track
  and your settings, and has to find the track themselves. Handing out
  playable links to YouTube's files is not something this app will do.
- Google does not let a third-party app sign in with a phone's account on
  its own: the tokens that make a YouTube session are only issued to
  Google's apps. So the account is picked from the phone, and the password
  or confirmation still happens on Google's page.
- Downloads live inside the app: other players do not see them and they go
  away with the app, until you press "save to music" — that makes a copy.
- There is no web version and none is planned.
- Part of the delay when a track starts comes from YouTube's side. That is
  why the "tap → sound" time is measured and shown on the home screen:
  a number you can check, not a promise.

## Installing

The APK is below. It is signed with the project key: updates install over
the top, and local data and playlists survive.
