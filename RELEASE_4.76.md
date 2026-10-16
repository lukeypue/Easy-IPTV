# RYZOD 4.76 — Search memory and typing

- Entering Search stops and releases live/VOD preview playback, including
  decoder, provider connection and temporary pause buffer. The mini TV does
  not restart automatically when leaving Search. Choose a show to play again.
- Scheduled recordings and queued downloads remain independent of the preview.
- Letters update immediately while results refresh after a brief typing pause.
  Existing results stay mounted during a burst of letters. Catalog-wide channel
  lookups are prepared once on a background worker instead of rebuilt on the
  main thread after every query change.
- Keyboard exit remains bottom-only; Up from the first results row returns to
  the keyboard. Done/Down submission flushes the current search without waiting
  for the typing pause.
- Includes 4.75's startup/background audio fixes and the existing Live TV fixes.

The approved artwork is unchanged. Photos confirm 4.75's Fire package supplies
the correct wide app icon/banner but Amazon's pinned tile retains the old R.
Current developer reports distinguish that stale tile from Fire TV's square
sideloaded-icon presentation. This release does not claim to reset Amazon's
private launcher cache or force a full-width Appstore tile. See
`docs/RYZOD-4.76-fire-tv-findings.md` for device evidence and research.

Fire TV: RYZOD-v4.76-FireTV.apk. Phone: RYZOD-v4.76.apk. Install over the
existing app to retain playlists/settings. From 4.75, the in-app updater selects
the Fire TV asset on Fire TV. No Android SDK or playback dependency changes.
