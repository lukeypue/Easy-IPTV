RYZOD 4.67 keeps the current player, DVR, recording, download menus and phone logo.

- Channel changes move temporary DVR storage discovery and cleanup off the UI thread.
- DVR sessions reuse bounded workers; retired channels cannot retain playback connections or overwrite current-channel state.
- Low-memory devices honor the player byte target and release unused mini-player references.
- Recording takeover respects the provider-stream limit through asynchronous channel setup.
- Guide requests cancel when leaving a channel, including incomplete response reads.
- Fire TV launch activity, intent icon, logo and banner explicitly use the wide artwork through a plain bitmap resource.
- Routine diagnostic writes run off the UI thread.

Regression coverage includes slow storage, ring retention and reader leases, rapid switching, same-channel recording, delayed/cancelled recording takeover, stalled local playback clients, guide cancellation, actual Media3 allocator behavior, phone/TV resources and saved-item menus. The previous 4.66 source and APK remain available as the rollback baseline.

Physical Fire Stick long-session playback and the Amazon home-screen icon still require device verification after installing this update. Install over the existing app to retain settings and saved items.
