# RYZOD 4.78 — Guide navigation and buffering

- Browse the guide with only channels and the selected category above the first column. Left opens the full category list; Left again opens the main menu.
- OK and Right enter the chosen category or section, including Search and Settings. Choosing the current category also returns focus to its channels. Keep horizontal navigation within Settings controls.
- Keep category-menu focus while Movies/Series load, and provide a usable Left return from empty Favorites, Downloads and Recordings. The live-player Settings shortcut opens its controls directly.
- Keep an on-screen menu button for touch navigation, and restore the recording-list cursor after a slow initial file scan.
- Refill the forward buffer as its reserve is consumed, instead of leaving loading stopped until the smaller minimum is reached. Preserve the 32 MiB sample limit on low-memory devices.
- Natural and Steady now have distinct recovery waits even with a small buffer preference. Steady rebuilds 15–30 seconds when memory and provider availability permit it. Keep normal playback speed and give HLS an explicit reserve target.
- Apply buffer, lock-in and recovery changes consistently on the next tune. Stop the old preview cleanly rather than retaining a player with stale settings or refreshing the provider catalog.
- Keep pause-only temporary DVR, recording/download ownership, the existing application identity and permanent signing certificate.

Install over the existing RYZOD app to retain playlists and preferences. Do not uninstall first.

Device testing remains necessary: verify the remote navigation on Fire Stick, sustained playback beyond two minutes with a 10-second lock-in, Natural/Steady behavior on the same provider channel, explicit Pause/Play and preview/full-screen transitions. Automated playback-policy tests cannot establish the throughput or decoder behavior of a customer's live stream.
