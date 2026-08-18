# EZTV v4.22 — Test First

1. Confirm GitHub Actions builds green.
2. Confirm normal Live TV is still smooth.
3. Live TV: press OK once. Play/Pause should be pink initially.
4. Press Right repeatedly across RW -> Play -> FF -> REC -> SIZE. Press Down and move across CC -> AFR -> Mode -> Retry -> Settings. Then Down to the timeline and Down again to Recent Channels. Verify Up/Down always gets back where expected.
5. If the status says DIRECT RESCUE, the mode button should say DVR RETRY. Select it and see whether DVR Live relocks.
6. DVR Live: let at least 30-60 seconds build. The timeline should show the current show's scheduled start/end, cyan from the moment EZTV joined/buffered the program, and a pink playhead.
7. Focus the timeline and press Left/Right. Verify 30-second jumps work when DVR is active.
8. Start recording from the mini-guide. Verify REC changes to STOP REC and Live continues.
9. Play a saved recording or downloaded episode. Press OK, move Up to Play/FF/RW, then Down. Verify Down returns to the bottom progress bar instead of getting stuck.
10. Verify the download queue, Keep/Delete popup, 5-column Movies/Series posters, Search live-channel surfing, USB recording, and provider-stream behavior are unchanged.
