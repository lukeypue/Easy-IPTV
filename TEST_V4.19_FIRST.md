# EZTV v4.19 — Fire Stick Test First

Do these in order so one setting does not hide another problem.

## 1. GitHub build
The build must turn green before testing. If it is red, send the **first** compiler error with file + line number.

## 2. Provider streams
Your service allows 3 simultaneous streams, so set **Settings → Provider streams → 3** for this test. Customers with one connection should leave it at 1.

## 3. Live controller / mini guide
- Start Live TV.
- Press OK **once**.
- Expected: only the EZTV bottom controller appears. No Media3 gear/title controller should overlap it.
- Play/Pause should start highlighted pink.
- D-pad Up/Down should move between controls, DVR bar, recents and header, and should always be able to move back down.
- It should remain visible about 15 seconds while playing and remain visible while paused.

## 4. DVR seek
- Switch to DVR Live and let a channel play at least 30–60 seconds.
- Press OK. The bar should show DVR START, current position/behind-live and LIVE.
- Pause for 60 seconds.
- Try remote Rewind, the ↶30 control and Left on the DVR bar.
- Then FF/30-forward back toward live.
- Report whether the controls say seek is still initializing or whether they actually move.

## 5. Same-channel recording
- Stay on a live channel in DVR Live.
- Start Record and keep watching that same channel.
- Expected: picture continues; recording size grows on the verified external USB.
- Stop after 2–5 minutes and play the recording.

## 6. Two-stream behavior
- Set Provider streams = 3.
- Start recording Channel A.
- Change to Channel B while A keeps recording.
- Expected: Channel B plays and Channel A recording continues.

## 7. Movies / Series
- Open Movies. A brief “Loading on-demand catalog…” message is normal the first time.
- Check that poster tiles appear.
- Open Series and check that series artwork appears.
- Open the exact series/episode that previously failed in EZTV but works in TiviMate.
- If anything fails, report the exact new error text.

## 8. Search
- Search a live channel name, a movie, and a series.
- Even if Movies/Series fails, Live search should still work.

## 9. Download to USB
- Download a short movie/episode.
- Confirm Downloads shows Pending/Running/Successful correctly and that external USB free space changes.

## 10. Protect the win
Do not change live-buffer settings while testing the features above unless needed. The v4.18.2 live path was already smooth and v4.19 intentionally avoids adding background work to Smooth Live.
