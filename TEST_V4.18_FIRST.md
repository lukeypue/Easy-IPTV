# EZTV v4.18 — first Fire Stick test

Please test these in order so we can tell WHICH change helps. Do not turn several settings on at once until the individual checks are done.

## 1) Mini guide / remote
1. Start any LIVE channel.
2. Wait until the normal player controls disappear.
3. Press **OK once**.
4. Expected: EZTV's mini guide opens at the **bottom** immediately — not the old one-channel popup.
5. It should show the current channel/show, buffer bar, actual live path, AFR status, CC, and a row of recent/nearby channel logos.
6. D-pad left/right across the row and press OK to tune.

## 2) Smooth Live vs DVR Live — isolate the buffering cause
Use ONE known-good channel that is smooth in TiviMate.

A. In the mini guide choose **Smooth Live** and leave AFR OFF. Watch 5–10 minutes.
B. Switch to **DVR Live** and leave AFR OFF. Watch the same channel 5–10 minutes.
C. Leave the same live mode selected and turn AFR ON from the mini guide. Watch again.

Tell the next AI which of A/B/C buffers. The mini guide status line should make it obvious which mode and refresh rate are actually active.

New installs default to **Smooth Live**. DVR Live adds disk-backed pause/rewind/recording and is best with a verified USB drive.

## 3) Hard channel that used to pause
Run it in Smooth Live first. If the provider closes the HTTP response cleanly, v4.18 should automatically reconnect instead of sitting paused until you press Play.

## 4) Movies / Series / Search
1. Open Movies. A small **Loading on-demand catalog…** message may appear the first time.
2. Confirm posters appear.
3. Open Series and test the exact show/episode that plays in TiviMate but previously failed in EZTV.
4. Search and confirm live results show channel logos and VOD results show poster art.
5. If the Fire TV has no installed speech recognizer, the broken microphone button should simply not be shown.

## 5) Closed captions
Use a channel/movie you KNOW contains subtitles or CEA-608 captions.
- Settings > Closed captions > On, OR press Menu while full-screen, OR use CC in the mini guide.
- No caption track in the provider stream = nothing to display; EZTV cannot invent captions that are not present.

## 6) USB 128 GB drive
Go to Settings > Storage.
1. If **Allow USB** appears, grant it.
2. Choose **Recheck USB**.
3. Only if the create/write/delete probe passes will EZTV show the external drive as usable.
4. Select **External drive**.
5. Note the free-space number before testing.

New files write directly to the selected destination. They are NOT written internally first and moved later.

## 7) Recording
Recording requires **DVR Live**.
1. Use a verified USB drive if possible.
2. Tune a live channel in DVR Live and start recording.
3. Within a few seconds the Recordings page should show KB/MB growing — not a permanent 0 MB placeholder.
4. Stop after 1–2 minutes and play it back.
5. While recording, EZTV should block channel/VOD changes that would create provider connection #2.
6. Failed recordings that receive zero media bytes are removed instead of leaving a fake 0 MB file.

## 8) Downloads
1. Pick a small VOD item and start a download.
2. EZTV stops provider playback first because this IPTV account allows only one provider connection.
3. Only one provider download runs at a time.
4. While it is downloading, remote Live/VOD playback is blocked until you stop/finish the download. Local completed downloads/recordings remain playable.
5. A partial file must stay labeled **Downloading**, not become playable just because it has a few bytes.
6. If USB is selected but Fire OS DownloadManager rejects that destination, EZTV tells you plainly and uses internal storage for that download rather than pretending it went to USB.

## 9) Download retention
Settings > Keep downloads:
- Until I delete it
- 7 / 14 / 30 / 60 / 90 days

There is no forced 14-day rule anymore.

## 10) Menu focus / exit
- Main menu should start focus at the upper/first item instead of bottom-right.
- Exit confirmation should put focus on **Stay** first and show the same hot-pink focus outline used elsewhere.

## Report back
Please send:
- Smooth Live + AFR OFF result
- DVR Live + AFR OFF result
- same live mode + AFR ON result
- exact VOD episode result
- USB Settings text/free-space result
- recording file size after ~30 seconds
- download destination + progress result
