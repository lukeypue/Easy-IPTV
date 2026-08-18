# EZTV v4.17 — first Fire Stick test

Use the same provider and the same channels/shows you compared against TiviMate. Do these before adding more features.

1. **Strong live channel — Full mode:** play a channel that was smooth in v4.9/TiviMate for at least 10 minutes. Verify no new buffering burst appears around 20–60 seconds after startup (the old background catalog refresh is gone).
2. **Weak live channel — Simple Mode:** Settings → Simple Mode ON. Open the hard channel. Verify Record/Pause/Rewind are unavailable and compare stability with Full mode.
3. **Mini guide:** watch 4–5 different channels long enough for each to reach READY. Press OK. Confirm the recent strip shows different channels/logos, D-pad moves between them, and OK tunes the highlighted channel. Relaunch EZTV and verify recognizable recent channels return.
4. **Known-bad VOD episodes:** play the exact episodes/movies that worked in TiviMate but failed in EZTV v4.16. If one still fails, write down the exact EZTV error text and whether it is: spinner forever, immediate error, black video with audio, video with no audio, or playback that stops at a repeatable timestamp.
5. **Search:** while no video is playing, search a channel and a movie/series. Confirm live uses channel logos and VOD uses portrait poster boxes. Search intentionally does not start a full XMLTV parse at the same time it lazily loads the large on-demand catalog.
6. **Download retention:** choose "Until I delete" (or another period), start one small download, then confirm the Downloads screen reflects the choice. Existing downloads should not disappear on first v4.17 launch.
7. **AFR/FPS:** enable Live AFR, try a known 24/25/30/50/60 fps source if available. A brief HDMI black flash can happen when the TV changes refresh rate. Then leave playback and confirm the TV returns normally. Repeat for Movies/Series AFR separately.
8. **Audio:** test at least one ordinary stereo channel and one Dolby/AC-3/E-AC-3 channel you know worked before. FFmpeg is now fallback rather than preferred, so report immediately if a previously-audible Dolby channel becomes silent.

If GitHub build itself fails, send the red **Build debug APK** error before doing any Fire Stick testing.
