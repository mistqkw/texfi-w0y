# TexFi w0y v0.0.2-beta-1

A YouTube Music client for Android: no ads, no Premium restrictions,
with your own account and downloads into phone storage.

## What is new in 0.0.2

- **Playlists land in your YouTube account.** Create a playlist in w0y and
  it appears in the account; add or remove a track and the account
  follows; rename or delete it and so does the copy there. The phone is
  the source of truth: nothing is deleted from the account behind your
  back, and if YouTube refuses a write, your change on the phone stays —
  the app says so and offers to upload the playlist again. Each playlist
  shows where it lives, so the word "sync" is something you can check.
- **All tracks of an artist.** The artist page had a dozen tracks and no
  way to see the rest. Now there is a button, and it opens the same list
  that sits behind "Show all" on YouTube — not a search for the artist's
  name, which brings in other people's covers.
- **Settings rebuilt.** Eight cramped tabs became eleven sections, each
  with a line saying what is inside, and above them a search across every
  setting — with forty of them that is the only way to reach the right one
  first time. New: seek step, cover glow, search suggestions, vibration,
  playlist mirroring, and the audio outputs your phone reports.
- **The player is no longer one long scroll.** The queue used to sit five
  screens below the cover, past the sound settings and the lyrics. Now
  there are three tabs — queue, lyrics, sound — and their strip sticks to
  the top while the list moves under it. The cover can be swiped sideways
  to change track, there are −10/+10 buttons next to the time, the
  position in the queue is shown, and the background takes the real colour
  of the cover, sampled from the image itself.
- **Search answers sooner.** YouTube's own suggestions appear while you
  type; the new "all" tab returns tracks, albums and artists in a single
  request instead of three; what you already have on the phone shows up
  before the network replies; "search" on the keyboard skips the typing
  pause; and one tap clears the field.
- **Audio outputs.** Settings list the outputs the system actually reports
  and mark the one in use; the player names it when it is not the phone
  itself — "playing through" explains silence in your headphones.

## What got faster

- Parsing a search response walked its whole tree once per card type —
  three times for one screen, six for an artist page. Now it is one walk.
  Each track in a result used to have its subtree walked twice for the
  artist and album links; now once.
- The player polled the playback position four times a second even on
  pause, redrawing to show the same number. Now polling stops with the
  music.
- The stream-link and search caches had no limit and grew for as long as
  the app stayed open. Both are bounded now.
- The list of audio outputs is one subscription for the whole app, not one
  per screen that asks for it.

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

## Honest about the limits

- Playlist mirroring goes one way: from the phone to the account. Tracks
  added on another device are pulled in when you sync, but the app will
  not delete anything from your account on its own.
- Words cannot be cut out of a finished recording — that needs a track
  without vocals. The app swaps in the official clean version when one is
  published, and does not pretend to do more.
- Muting individual lines only works where synchronised lyrics were found,
  and is marked beta.
- The name of a Bluetooth device is not always given to an app, and asking
  for the Bluetooth permission just to print a label is a bad trade. When
  the name is missing, the kind of output is shown instead.
- There is no web version and none is planned.
- Part of the delay when a track starts comes from YouTube's side. That is
  why the "tap → sound" time is measured and shown on the home screen:
  a number you can check, not a promise.

## Installing

The APK is below. It is signed with the project key: updates install over
the top, and local data and playlists survive.
