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

Fire TV launcher icons now use the approved square RYZOD logo in every Fire
resource configuration; the wide TV banner remains separate. This avoids
feeding a wide image into the square Home icon slot shown in the device photo.
Regression tests check the resolved artwork and its proportions. Amazon may
still delay refreshing its Home image, and a full-width Appstore tile is not
guaranteed. See `docs/RYZOD-4.76-fire-tv-findings.md` for device evidence.

Fire TV: RYZOD-v4.76-FireTV.apk. Phone: RYZOD-v4.76.apk. Install over the
existing app to retain playlists/settings. From 4.75, the in-app updater selects
the Fire TV asset on Fire TV. No Android SDK or playback dependency changes.
