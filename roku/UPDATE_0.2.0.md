# RYZOD Roku 0.2.0 — Android 4.80 guide and playback update

- Guide: eight compact channel rows, large green active category, logo at upper right, preview at upper left, Update guide above the grid with provider wording.
- Left at the first guide cell opens categories; Left again opens the main menu. OK enters Settings/Search and selecting the current category returns to channels. Rewind/Forward browse guide time; Replay returns to Now.
- Settings → Live delay cycles Real time (no added delay), 10s, 20s and 30s. Applies when opening the next HLS/DASH channel. Roku's documented negative PlayStart requests a position behind the available edge; the provider window may constrain it. Movies and saved media retain their own resume position.
- Roku manages forward buffering and recovery. A selected delay is not a measured downloaded reserve. The mini guide displays native buffering percentage when available, available-edge latency when provided, and labels paused history separately.
- Removed the redundant Live action during ordinary watching. End pause appears while paused/behind in pause history.
- Playback, providers, favorites, search, EPG, audio/captions and companion recording/download functionality retained. This updates the existing native app; it is not an APK conversion.

Roku cannot offer Android's FFmpeg decoder, USB recording or arbitrary forward-buffer controls through these native APIs. Saved recording/downloads still require the existing optional computer/NAS companion (unchanged 0.1.0). Native pause length depends on Roku and the source. Physical Roku/provider playback is still required; emulator/compiler tests do not prove device playback.

References: https://developer.roku.com/dev/docs/video and https://developer.roku.com/dev/docs/content-metadata . Android reference: https://github.com/lukeypue/Easy-IPTV/tree/ryzod-v4.80-buffer-parity .

Developer-mode installation: upload RYZOD-Roku-0.2.0.zip using the Roku development web page, following README.md. Keep the same developer identity to retain registry settings.
