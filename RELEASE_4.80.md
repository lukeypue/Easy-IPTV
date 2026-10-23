# RYZOD 4.80 — Live buffer clarity and playback recovery

- Direct live playback remains the default. Temporary disk history starts only on Pause.
- Added a 20-second forward refill choice alongside 10/30/60. Existing 20/30-second startup choices remain.
- Clearly named Real time (0s): no deliberate startup or recovery wait, with necessary decoder buffering retained.
- Settings show effective startup, recovery and refill targets, including when Real time overrides saved cushion/recovery preferences.
- Natural and Steady both play at 1x. Steady rebuilds more video after starvation; it cannot improve a provider's sustained delivery rate.
- Fixed stale movie/channel retries affecting a newly selected movie, reset retry budgets for new movies, and preserved an explicit Pause during recovery.
- Updates Media3 to stable 1.11.1 with HLS EOF retry and renderer-stall fixes; retains the FFmpeg audio extension.
- Retains the compact guide, green category heading, upper-right logo and buffer-health display from 4.79.

Install the matching APK over the existing app to retain saved playlists and settings. Same package and permanent signing certificate. Actual live-buffer duration depends on provider availability, bitrate and device memory. Physical provider/device playback still needs testing; automated verification is not a promise that every bug has been eliminated.

Research and review: docs/RYZOD-4.80-buffer-research.md.
