# RYZOD 4.96 — Live Picture Recovery

- Live TV detects a stalled picture while playback continues, including when audio starts before the first video frame.
- The player first refreshes video output on the same channel. If Smooth playback still has a stalled picture and the feed cannot seek forward, it can retune the same channel once.
- Recovery during playback from the pause buffer uses the saved position.

Fire TV uses `RYZOD-v4.96-FireTV.apk`. Android and Android TV use `RYZOD-v4.96.apk`.
