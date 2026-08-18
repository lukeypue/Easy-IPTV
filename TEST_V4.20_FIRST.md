# EZTV v4.20 — Test This First

Do these in order. Do not change several settings at once.

## 1 — GitHub
The build must turn green first. Install the v4.20 APK.

## 2 — Smooth Live safety check
1. Start a channel in Smooth Live.
2. Watch 1–2 minutes.
3. Confirm it is still as smooth as v4.19.

If Smooth Live regressed, stop the test and report it.

## 3 — DVR Live picture / lock-in
1. Switch the SAME channel to DVR Live.
2. The picture should lock in within seconds, not several minutes.
3. Press OK and confirm the DVR MB count grows.
4. Pause 30–60 seconds and resume.

Report:
- how long DVR Live takes to show picture
- whether lock-in leaves 0%
- whether pause/resume works

## 4 — DVR rewind / forward
1. Stay in DVR Live at least 60 seconds.
2. Press OK.
3. Use the 30-second rewind button.
4. Try the Fire remote Rewind button.
5. Try 30-second forward toward Live.

A jump may briefly relock because v4.20 reopens the local TS stream at an already-recorded byte offset. It should NOT wait minutes or restart the provider connection.

Report whether each jump works and roughly where it lands.

## 5 — Same-channel recording
With Provider Streams = 3:
1. Stay on a DVR Live channel.
2. Record that same channel for 1–2 minutes.
3. Live picture should continue playing.
4. Stop recording and play the saved USB recording.

## 6 — Movie download to USB
1. Pick a short movie that plays normally.
2. Press Download.
3. Open Downloads.
4. You should see bytes/percent increase within a few seconds.
5. Let it finish.
6. Play the downloaded local file.
7. Confirm USB free space decreases.

If it fails, report the exact red “Download failed: …” message. That message is intentionally exposed in v4.20.

## 7 — Download while Live TV plays
With Provider Streams = 3:
1. Start Smooth Live.
2. Start one VOD download.
3. Return to Live TV.
4. Confirm Live stays smooth and the download continues.

## 8 — Movies / Series / Search
Confirm the v4.19 catalog fix remains intact:
- Movies visible
- Series visible
- Search works
- known troublesome VOD still plays
